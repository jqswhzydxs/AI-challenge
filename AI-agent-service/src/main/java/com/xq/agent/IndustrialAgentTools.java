package com.xq.agent;

import com.alibaba.fastjson2.JSON;
import com.xq.contracts.agent.ToolResult;
import java.time.LocalDate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IndustrialAgentTools {
  private final IndustrialEvidenceClient evidenceClient;

  public ToolResult<Map<String, Object>> query(String tool, Long id, LocalDate date) {
    return evidenceClient.query(tool, id, date);
  }

  @Tool(description = "Query a joint optimization by its authorized ID.")
  public String queryJointOptimization(Long optimizeId) {
    return JSON.toJSONString(query("jointOptimization", optimizeId, null));
  }

  @Tool(description = "Query evaluation metrics; null metrics are unavailable, never zero.")
  public String queryJointOptimizationEvaluation(Long optimizeId) {
    return JSON.toJSONString(query("evaluation", optimizeId, null));
  }

  @Tool(
      description = "Query conflicts; ERROR must never be interpreted as an empty successful list.")
  public String queryOptimizationConflicts(Long optimizeId) {
    return JSON.toJSONString(query("conflicts", optimizeId, null));
  }

  @Tool(description = "Query energy summary, without returning unbounded detail arrays.")
  public String queryEnergyPlan(Long energyPlanId) {
    return JSON.toJSONString(query("energyPlan", energyPlanId, null));
  }

  @Tool(description = "Query a daily report for an explicit ISO business date.")
  public String queryDailyReportSummary(String businessDate) {
    return JSON.toJSONString(query("dailyReport", null, LocalDate.parse(businessDate)));
  }
}
