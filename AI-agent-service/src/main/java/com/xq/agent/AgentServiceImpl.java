package com.xq.agent;

import com.xq.common.result.Result;
import com.xq.contracts.agent.AgentAdviceVO;
import com.xq.contracts.agent.AgentChatDTO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 工业优化 Agent 服务实现.
 *
 * <p>当 {@code agent.llm.enabled=true} 且 Spring AI ChatClient 可用时，使用大模型和 Tool Calling；
 * 否则自动走规则兜底，保证比赛演示和本地开发在没有 API Key 时也能返回可用结果。
 */
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {
  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(AgentServiceImpl.class);

  /** 使用 ObjectProvider 延迟获取 ChatClient，避免未启用模型时影响应用启动。 */
  private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;

  /** 业务工具集合，提供给 Spring AI Tool Calling 调用。 */
  private final IndustrialAgentTools tools;

  /** Prompt 模板工厂，统一约束模型输出为结构化 JSON。 */
  private final IndustrialPromptFactory promptFactory;

  /** 规则兜底服务，处理未配置模型或模型调用失败的场景。 */
  private final AgentFallbackService fallbackService;

  private final AgentResponseValidator responseValidator;
  private final AgentModelExecutor modelExecutor;
  private final AgentRunRepository runRepository;

  @Value("${agent.total-timeout-seconds:30}")
  private long totalTimeoutSeconds;

  /** 大模型开关，默认启用；如需本地离线演示，可通过 AGENT_LLM_ENABLED=false 切回规则兜底。 */
  @Value("${agent.llm.enabled:true}")
  private boolean llmEnabled;

  @Override
  public Result<AgentAdviceVO> chat(AgentChatDTO dto) {
    AgentChatDTO request = normalize(dto);
    String scene = blankToDefault(request.getScene(), "GENERAL_CHAT");
    if (!List.of("GENERAL_CHAT", "OPTIMIZATION_EXPLAIN", "ENERGY_PLAN_DIAGNOSE", "DAILY_REPORT")
        .contains(scene))
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_SCENE");
    return Result.ok(callAgent(scene, request));
  }

  @Override
  public Result<AgentAdviceVO> explainOptimization(Long optimizeId, AgentChatDTO dto) {
    AgentChatDTO request = normalize(dto);
    request.setOptimizeId(optimizeId);
    return Result.ok(callAgent("OPTIMIZATION_EXPLAIN", request));
  }

  @Override
  public Result<AgentAdviceVO> diagnoseEnergyPlan(Long energyPlanId, AgentChatDTO dto) {
    AgentChatDTO request = normalize(dto);
    request.setEnergyPlanId(energyPlanId);
    return Result.ok(callAgent("ENERGY_PLAN_DIAGNOSE", request));
  }

  @Override
  public Result<AgentAdviceVO> generateDailyReport(AgentChatDTO dto) {
    AgentChatDTO request = normalize(dto);
    return Result.ok(callAgent("DAILY_REPORT", request));
  }

  /**
   * Agent 调用主流程.
   *
   * <p>先判断是否启用 LLM，再构建 Spring AI ChatClient； 真正调用时把业务工具注册给模型，使模型能够按需查询优化结果、能源方案和报表指标。
   */
  private AgentAdviceVO callAgent(String scene, AgentChatDTO dto) {
    long started = System.nanoTime();
    var attributes =
        (org.springframework.web.context.request.ServletRequestAttributes)
            org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes();
    var request = attributes.getRequest();
    long deadline =
        request.getAttribute("agentDeadline") instanceof Long value
            ? value
            : started + totalTimeoutSeconds * 1_000_000_000L;
    AgentAdviceVO result = analyze(scene, dto, deadline);
    if (request.getAttribute("evidenceBundle") instanceof com.xq.contracts.agent.EvidenceBundle bundle) {
      var provenance = new java.util.LinkedHashMap<String, Object>();
      if (result.getEvidenceSummary() != null) provenance.putAll(result.getEvidenceSummary());
      provenance.putIfAbsent("planId", bundle.subjectId());
      provenance.putIfAbsent("planDate", bundle.businessDate());
      provenance.putIfAbsent("timezone", bundle.timezone());
      provenance.putIfAbsent("dataSource", "Core 受控证据：" + bundle.subjectType());
      provenance.putIfAbsent("measurementType", "UNVERIFIED_AGGREGATE");
      result.setEvidenceSummary(provenance);
    }
    var identity =
        (com.xq.contracts.agent.VerifiedIdentity) request.getAttribute("verifiedIdentity");
    runRepository.save(
        result,
        identity,
        (System.nanoTime() - started) / 1_000_000L,
        request.getAttribute("evidenceBundle"));
    log.info(
        "agent run completed runId={} userId={} source={} analysisStatus={} modelAttempted={}"
            + " durationMs={}",
        result.getRunId(),
        identity.userId(),
        result.getSource(),
        result.getAnalysisStatus(),
        result.getModelAttempted(),
        (System.nanoTime() - started) / 1_000_000L);
    return result;
  }

  private AgentAdviceVO analyze(String scene, AgentChatDTO dto, long deadline) {
    AgentAdviceVO frozen = fallbackService.prepare(scene, dto);
    if (!llmEnabled) {
      return frozen;
    }
    ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
    if (builder == null) {
      return frozen;
    }
    if (!"COMPLETE".equals(frozen.getAnalysisStatus())) return frozen;
    frozen.setModelAttempted(true);
    try {
      AgentAdviceVO advice =
          modelExecutor.call(
              () ->
                  builder
                      .build()
                      .prompt()
                      .system(promptFactory.systemPrompt())
                      .user(
                          promptFactory.userPrompt(scene, dto)
                              + "\nFrozen evidence and server-rendered facts:\n"
                              + com.alibaba.fastjson2.JSON.toJSONString(frozen))
                      .call()
                      .entity(AgentAdviceVO.class),
              (deadline - System.nanoTime()) / 1_000_000L);
      List<String> errors = responseValidator.validate(advice, frozen);
      if (!errors.isEmpty()) {
        frozen.setSource("RULE_FALLBACK_AFTER_VALIDATION_ERROR");
        frozen.setValidationErrors(errors);
        return frozen;
      }
      return enrich(advice, frozen, scene, dto);
    } catch (RuntimeException e) {
      // 模型服务超时、无 Key、网络异常或结构化解析失败时，自动降级到规则分析。
      frozen.setSource("RULE_FALLBACK_AFTER_AI_ERROR");
      frozen.setValidationErrors(List.of("MODEL_CALL_FAILED"));
      return frozen;
    }
  }

  /** 补齐模型可能漏掉的字段，保证前端拿到稳定的 AgentAdviceVO 结构。 */
  private AgentAdviceVO enrich(
      AgentAdviceVO advice, AgentAdviceVO frozen, String scene, AgentChatDTO dto) {
    advice.setRunId(frozen.getRunId());
    advice.setSessionId(dto.getSessionId());
    advice.setScene(scene);
    advice.setSource("SPRING_AI_VALIDATED");
    advice.setAiEnabled(true);
    advice.setModelAttempted(true);
    advice.setAnalysisStatus(frozen.getAnalysisStatus());
    advice.setRiskLevel(frozen.getRiskLevel());
    advice.setKeyMetrics(frozen.getKeyMetrics());
    advice.setMissingEvidence(frozen.getMissingEvidence());
    advice.setValidationErrors(List.of());
    advice.setProblems(defaultList(advice.getProblems()));
    advice.setSuggestions(defaultList(advice.getSuggestions()));
    advice.setEvidence(frozen.getEvidence());
    advice.setEvidenceSummary(frozen.getEvidenceSummary());
    advice.setEvidenceDetails(frozen.getEvidenceDetails());
    advice.setRuleBasis(frozen.getRuleBasis());
    advice.setNextActions(defaultList(advice.getNextActions()));
    advice.setRawAnswer(frozen.getRawAnswer());
    advice.setGeneratedAt(frozen.getGeneratedAt());
    return advice;
  }

  private AgentChatDTO normalize(AgentChatDTO dto) {
    AgentChatDTO value = dto != null ? dto : new AgentChatDTO();
    if (value.getQuestion() != null && value.getQuestion().length() > 4000)
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.BAD_REQUEST, "QUESTION_TOO_LONG");
    if (value.getContext() != null
        && com.alibaba.fastjson2.JSON.toJSONString(value.getContext()).length() > 8000)
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.BAD_REQUEST, "CONTEXT_TOO_LARGE");
    return value;
  }

  private List<String> defaultList(List<String> values) {
    return values != null ? values : List.of();
  }

  private String blankToDefault(String value, String defaultValue) {
    return value != null && !value.trim().isEmpty() ? value : defaultValue;
  }
}
