package com.xq.agent;

import com.alibaba.fastjson2.JSON;
import com.xq.contracts.agent.AgentAdviceVO;
import com.xq.contracts.agent.AgentChatDTO;
import com.xq.contracts.agent.ToolResult;
import com.xq.contracts.agent.ToolStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Deterministic analysis over this run's queries. Missing values never become observed zeroes. */
@Service
@RequiredArgsConstructor
public class AgentFallbackService {
  private final IndustrialAgentTools tools;

  public AgentAdviceVO chat(String scene, AgentChatDTO dto) {
    return prepare(scene, dto);
  }

  public AgentAdviceVO prepare(String scene, AgentChatDTO dto) {
    AgentChatDTO request = dto == null ? new AgentChatDTO() : dto;
    List<String> missing = new ArrayList<>();
    List<String> metrics = new ArrayList<>();
    List<String> problems = new ArrayList<>();
    List<String> evidence = new ArrayList<>();
    Map<String, Object> snapshots = new LinkedHashMap<>();
    Map<String, Object> evidenceSummary = new LinkedHashMap<>();
    List<String> suggestions = new ArrayList<>();
    List<String> basis = new ArrayList<>();
    String risk = "UNKNOWN";
    String summary = "证据不足，无法判断业务风险。";

    if ("DAILY_REPORT".equals(scene)) {
      LocalDate date = date(request, missing);
      if (date != null) {
        Map<String, Object> data = fetch("dailyReport", null, date, missing, evidence, snapshots);
        if (data != null) {
          Map<String, Object> analysis = object(data.get("energyAnalysis"));
          metric(analysis, "totalEnergyKgce", "综合能耗", "kgce", metrics, missing);
          metric(analysis, "totalEnergyCost", "能源成本", "元", metrics, missing);
          metric(analysis, "totalProductionOutput", "产量", "t", metrics, missing);
          summary = "已汇总指定业务日期的报表；聚合数据不足以独立判断运行风险。";
        }
      }
    } else if ("OPTIMIZATION_EXPLAIN".equals(scene)
        || (!"ENERGY_PLAN_DIAGNOSE".equals(scene) && request.getOptimizeId() != null)) {
      Map<String, Object> data =
          fetch("evaluation", request.getOptimizeId(), null, missing, evidence, snapshots);
      Map<String, Object> conflicts =
          fetch("conflicts", request.getOptimizeId(), null, missing, evidence, snapshots);
      BigDecimal mape = metric(data, "mape", "MAPE", "%", metrics, missing);
      String erKey = data != null && data.get("er") != null ? "er" : "executeRate";
      BigDecimal er = metric(data, erKey, "ER/可执行率", "%", metrics, missing);
      metric(data, "costReductionRate", "降本率", "%", metrics, missing);
      metric(data, "energyReductionRate", "降耗率", "%", metrics, missing);
      BigDecimal count = metric(conflicts, "conflictCount", "本次查询约束冲突数", "", metrics, missing);
      if (count != null && count.signum() > 0) problems.add("存在约束冲突，请核查冲突明细和设备边界。");
      if (mape != null && mape.compareTo(new BigDecimal("10")) > 0)
        problems.add("MAPE 偏高，请复核预测与实测口径。");
      if (er != null && er.compareTo(new BigDecimal("90")) < 0) problems.add("可执行率偏低，请核查设备约束。");
      if ((count != null && count.signum() > 0)
          || (mape != null && mape.compareTo(new BigDecimal("15")) > 0)) risk = "HIGH";
      else if (er != null && er.compareTo(new BigDecimal("90")) < 0) risk = "MEDIUM";
      else if (missing.isEmpty()) risk = "LOW";
      summary = missing.isEmpty() ? "已基于本次评价与冲突查询完成规则分析。" : "部分评价证据缺失，结论仅覆盖可用指标。";
    } else if ("ENERGY_PLAN_DIAGNOSE".equals(scene) || request.getEnergyPlanId() != null) {
      Map<String, Object> data =
          fetch("energyPlan", request.getEnergyPlanId(), null, missing, evidence, snapshots);
      var diagnosis = EnergyDiagnosticRules.analyze(data, metrics, problems, missing);
      risk = diagnosis.risk();
      summary = diagnosis.summary();
      suggestions.addAll(diagnosis.suggestions());
      basis.addAll(diagnosis.basis());
      if (data != null) {
        for (String key : List.of("planId", "planDate", "dataSource", "measurementType", "timezone",
            "timeIntervalMinutes", "rawCollectionInterval", "calculationInterval", "constraintsComplete"))
          evidenceSummary.put(key, data.get(key));
      }
    } else {
      missing.add("subject：请提供协同优化对象、能源方案或日报日期");
    }
    String status =
        metrics.isEmpty() ? "INSUFFICIENT_EVIDENCE" : missing.isEmpty() ? "COMPLETE" : "PARTIAL";
    if (!missing.isEmpty()) problems.add("存在缺失或失败的证据，请补齐后重新分析。");
    return AgentAdviceVO.builder()
        .runId(UUID.randomUUID().toString())
        .sessionId(request.getSessionId())
        .scene(scene)
        .source("RULE_FALLBACK")
        .aiEnabled(false)
        .modelAttempted(false)
        .analysisStatus(status)
        .riskLevel(risk)
        .summary(summary)
        .keyMetrics(List.copyOf(metrics))
        .problems(List.copyOf(problems))
        .suggestions(suggestions.isEmpty()
            ? List.of(missing.isEmpty() ? "结合现场约束复核可用指标，再决定业务操作。" : "先核查缺失项与数据来源，证据不足时保留人工判断。")
            : List.copyOf(suggestions))
        .evidenceSummary(evidenceSummary)
        .evidenceDetails(List.copyOf(evidence))
        .ruleBasis(List.copyOf(basis))
        .evidence(List.copyOf(evidence))
        .missingEvidence(List.copyOf(missing))
        .validationErrors(List.of())
        .nextActions(List.of("查看方案或报表中的原始业务数据"))
        .rawAnswer(JSON.toJSONString(snapshots))
        .generatedAt(OffsetDateTime.now().toString())
        .build();
  }

  private Map<String, Object> fetch(
      String tool,
      Long id,
      LocalDate date,
      List<String> missing,
      List<String> evidence,
      Map<String, Object> snapshots) {
    ToolResult<Map<String, Object>> result;
    try {
      result = tools.query(tool, id, date);
    } catch (RuntimeException exception) {
      if (exception instanceof org.springframework.web.server.ResponseStatusException status
          && (status.getStatusCode().value() == 401 || status.getStatusCode().value() == 403))
        throw status;
      result = ToolResult.of(ToolStatus.ERROR, null, "CORE_QUERY_FAILED", List.of());
    }
    if (result == null)
      result = ToolResult.of(ToolStatus.ERROR, null, "INVALID_RESPONSE", List.of());
    snapshots.put(tool, result);
    evidence.add(
        tool
            + ": callId="
            + result.callId()
            + ", snapshotId="
            + result.snapshotId()
            + ", status="
            + result.status());
    if (result.status() == ToolStatus.ERROR || result.status() == ToolStatus.EMPTY) {
      missing.add(
          tool + ": " + (result.errorCode() == null ? result.status().name() : result.errorCode()));
      return null;
    }
    if (result.status() == ToolStatus.PARTIAL) missing.add(tool + "：部分数据缺失");
    return result.data();
  }

  private BigDecimal metric(
      Map<String, Object> data,
      String key,
      String label,
      String unit,
      List<String> metrics,
      List<String> missing) {
    Object raw = data == null ? null : data.get(key);
    try {
      if (raw == null) throw new IllegalArgumentException();
      BigDecimal value = new BigDecimal(raw.toString());
      metrics.add(label + "：" + value.toPlainString() + (unit.isEmpty() ? "" : " " + unit));
      return value;
    } catch (RuntimeException exception) {
      missing.add(key + "：指标不可用");
      return null;
    }
  }

  private LocalDate date(AgentChatDTO request, List<String> missing) {
    Object value = request.getBusinessDate();
    if (value == null && request.getContext() != null) value = request.getContext().get("date");
    try {
      return value == null
          ? LocalDate.now(ZoneId.of("Asia/Shanghai"))
          : LocalDate.parse(value.toString());
    } catch (RuntimeException exception) {
      missing.add("businessDate：必须使用有效的 yyyy-MM-dd 日期");
      return null;
    }
  }

  private Map<String, Object> object(Object value) {
    return value == null ? Map.of() : JSON.parseObject(JSON.toJSONString(value));
  }
}
