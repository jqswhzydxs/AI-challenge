package com.xq.service.impl;

import com.xq.common.result.Result;
import com.xq.model.dto.AgentChatDTO;
import com.xq.model.vo.AgentAdviceVO;
import com.xq.service.AgentService;
import com.xq.service.agent.AgentFallbackService;
import com.xq.service.agent.IndustrialAgentTools;
import com.xq.service.agent.IndustrialPromptFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工业优化 Agent 服务实现.
 * <p>
 * 当 {@code agent.llm.enabled=true} 且 Spring AI ChatClient 可用时，使用大模型和 Tool Calling；
 * 否则自动走规则兜底，保证比赛演示和本地开发在没有 API Key 时也能返回可用结果。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    /** 使用 ObjectProvider 延迟获取 ChatClient，避免未启用模型时影响应用启动。 */
    private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;
    /** 业务工具集合，提供给 Spring AI Tool Calling 调用。 */
    private final IndustrialAgentTools tools;
    /** Prompt 模板工厂，统一约束模型输出为结构化 JSON。 */
    private final IndustrialPromptFactory promptFactory;
    /** 规则兜底服务，处理未配置模型或模型调用失败的场景。 */
    private final AgentFallbackService fallbackService;

    /** 大模型开关，默认启用；如需本地离线演示，可通过 AGENT_LLM_ENABLED=false 切回规则兜底。 */
    @Value("${agent.llm.enabled:true}")
    private boolean llmEnabled;

    @Override
    public Result<AgentAdviceVO> chat(AgentChatDTO dto) {
        AgentChatDTO request = normalize(dto);
        String scene = blankToDefault(request.getScene(), "GENERAL_CHAT");
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
     * <p>
     * 先判断是否启用 LLM，再构建 Spring AI ChatClient；
     * 真正调用时把业务工具注册给模型，使模型能够按需查询优化结果、能源方案和报表指标。
     * </p>
     */
    private AgentAdviceVO callAgent(String scene, AgentChatDTO dto) {
        if (!llmEnabled) {
            return fallbackService.chat(scene, dto);
        }
        ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
        if (builder == null) {
            return fallbackService.chat(scene, dto);
        }
        try {
            AgentAdviceVO advice = builder.build()
                    .prompt()
                    .system(promptFactory.systemPrompt())
                    .user(promptFactory.userPrompt(scene, dto))
                    .tools(tools)
                    .call()
                    .entity(AgentAdviceVO.class);
            return enrich(advice, scene, dto);
        } catch (RuntimeException e) {
            // 模型服务超时、无 Key、网络异常或结构化解析失败时，自动降级到规则分析。
            AgentAdviceVO fallback = fallbackService.chat(scene, dto);
            fallback.setSource("RULE_FALLBACK_AFTER_AI_ERROR");
            fallback.setRawAnswer("Spring AI 调用失败，已自动降级：" + e.getMessage());
            return fallback;
        }
    }

    /** 补齐模型可能漏掉的字段，保证前端拿到稳定的 AgentAdviceVO 结构。 */
    private AgentAdviceVO enrich(AgentAdviceVO advice, String scene, AgentChatDTO dto) {
        if (advice == null) {
            return fallbackService.chat(scene, dto);
        }
        advice.setSessionId(blankToDefault(advice.getSessionId(), dto.getSessionId()));
        advice.setScene(blankToDefault(advice.getScene(), scene));
        advice.setSource(blankToDefault(advice.getSource(), "SPRING_AI_TOOL_CALLING"));
        advice.setAiEnabled(true);
        advice.setRiskLevel(blankToDefault(advice.getRiskLevel(), "LOW"));
        advice.setSummary(blankToDefault(advice.getSummary(), "Agent 已完成分析。"));
        advice.setKeyMetrics(defaultList(advice.getKeyMetrics()));
        advice.setProblems(defaultList(advice.getProblems()));
        advice.setSuggestions(defaultList(advice.getSuggestions()));
        advice.setEvidence(defaultList(advice.getEvidence()));
        advice.setNextActions(defaultList(advice.getNextActions()));
        advice.setGeneratedAt(blankToDefault(advice.getGeneratedAt(), LocalDateTime.now().toString()));
        return advice;
    }

    private AgentChatDTO normalize(AgentChatDTO dto) {
        return dto != null ? dto : new AgentChatDTO();
    }

    private List<String> defaultList(List<String> values) {
        return values != null ? values : List.of();
    }

    private String blankToDefault(String value, String defaultValue) {
        return value != null && !value.trim().isEmpty() ? value : defaultValue;
    }
}
