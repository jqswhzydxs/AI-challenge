package com.xq.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.xq.contracts.agent.*;
import com.xq.contracts.agent.AgentAdviceVO;
import com.xq.contracts.agent.AgentChatDTO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class AgentAccuracyRegressionTest {
  final IndustrialEvidenceClient client = mock(IndustrialEvidenceClient.class);
  final AgentFallbackService rules = new AgentFallbackService(new IndustrialAgentTools(client));

  @ParameterizedTest
  @CsvSource({"0,100,0,LOW", "0,0,0,MEDIUM", "16,100,0,HIGH", "2,89,0,MEDIUM", "2,100,1,HIGH"})
  void validZeroAndThresholds(String mape, String er, int count, String risk) {
    stub(evaluation(mape, er), ok(Map.of("conflictCount", count)));
    AgentAdviceVO result = optimization();
    assertEquals(risk, result.getRiskLevel());
    assertEquals("COMPLETE", result.getAnalysisStatus());
  }

  @ParameterizedTest
  @ValueSource(strings = {"mape", "er", "costReductionRate", "energyReductionRate"})
  void missingMetricNeverMeansLowRisk(String field) {
    Map<String, Object> data = evaluation("0", "100");
    data.remove(field);
    stub(data, ok(Map.of("conflictCount", 0)));
    assertEquals("UNKNOWN", optimization().getRiskLevel());
  }

  @ParameterizedTest
  @ValueSource(strings = {"ERROR", "EMPTY"})
  void failedEvaluationIsNotNormal(String status) {
    when(client.query(eq("evaluation"), anyLong(), isNull()))
        .thenReturn(
            ToolResult.of(ToolStatus.valueOf(status), null, "CORE_QUERY_FAILED", List.of()));
    when(client.query(eq("conflicts"), anyLong(), isNull()))
        .thenReturn(ok(Map.of("conflictCount", 0)));
    assertEquals("UNKNOWN", optimization().getRiskLevel());
  }

  @Test
  void failedConflictsAreNotSuccessfulEmptyConflicts() {
    stub(
        evaluation("0", "100"),
        ToolResult.of(ToolStatus.ERROR, null, "CORE_QUERY_FAILED", List.of()));
    AgentAdviceVO result = optimization();
    assertEquals("UNKNOWN", result.getRiskLevel());
    assertTrue(result.getKeyMetrics().stream().noneMatch(text -> text.contains("冲突数")));
  }

  @Test
  void exceptionsAreSanitized() {
    when(client.query(anyString(), any(), any()))
        .thenThrow(new IllegalStateException("secret database password"));
    AgentAdviceVO result = optimization();
    assertEquals("INSUFFICIENT_EVIDENCE", result.getAnalysisStatus());
    assertFalse(result.getRawAnswer().contains("password"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1", "null"})
  void invalidMeanDoesNotProduceZeroPeakRatio(String mean) {
    Map<String, Object> data =
        new LinkedHashMap<>(
            Map.of("totalEnergyCost", 0, "peakElectricity", 0, "totalCarbonEmissionTco2", 0));
    if (!"null".equals(mean)) data.put("avgElectricity", new BigDecimal(mean));
    when(client.query(eq("energyPlan"), eq(9L), isNull())).thenReturn(ok(data));
    AgentChatDTO request = new AgentChatDTO();
    request.setEnergyPlanId(9L);
    AgentAdviceVO result = rules.chat("ENERGY_PLAN_DIAGNOSE", request);
    assertEquals("UNKNOWN", result.getRiskLevel());
    assertTrue(result.getKeyMetrics().stream().noneMatch(text -> text.startsWith("峰均比")));
  }

  @Test
  void explicitDateIsPassedAndSceneWins() {
    LocalDate date = LocalDate.of(2026, 7, 17);
    when(client.query("dailyReport", null, date))
        .thenReturn(
            ok(
                Map.of(
                    "energyAnalysis",
                    Map.of(
                        "totalEnergyKgce",
                        12,
                        "totalEnergyCost",
                        30,
                        "totalProductionOutput",
                        4))));
    AgentChatDTO request = new AgentChatDTO();
    request.setBusinessDate(date.toString());
    request.setOptimizeId(7L);
    assertEquals("COMPLETE", rules.chat("DAILY_REPORT", request).getAnalysisStatus());
    verify(client).query("dailyReport", null, date);
    verifyNoMoreInteractions(client);
  }

  @Test
  void invalidDateNeverQueriesAnotherDay() {
    AgentChatDTO request = new AgentChatDTO();
    request.setBusinessDate("2026-02-30");
    assertEquals("INSUFFICIENT_EVIDENCE", rules.chat("DAILY_REPORT", request).getAnalysisStatus());
    verifyNoInteractions(client);
  }

  @Test
  void noSubjectHasNoLowRiskClaim() {
    AgentAdviceVO result = rules.chat("GENERAL_CHAT", null);
    assertEquals("UNKNOWN", result.getRiskLevel());
    assertNotNull(result.getRunId());
  }

  @ParameterizedTest
  @ValueSource(strings = {"{}", "cost", "foreignCall", "risk", "proseNumber", "date"})
  void alteredModelFactsAreRejected(String mutation) {
    AgentAdviceVO frozen =
        AgentAdviceVO.builder()
            .summary("指标已查询。")
            .riskLevel("LOW")
            .keyMetrics(List.of("成本：100 元"))
            .evidence(List.of("callId=current-run-call"))
            .build();
    AgentAdviceVO model =
        AgentAdviceVO.builder()
            .summary("请复核现场约束。")
            .riskLevel("LOW")
            .keyMetrics(frozen.getKeyMetrics())
            .evidence(frozen.getEvidence())
            .build();
    switch (mutation) {
      case "{}" -> model = new AgentAdviceVO();
      case "cost" -> model.setKeyMetrics(List.of("成本：99 元"));
      case "foreignCall" -> model.setEvidence(List.of("callId=other-run-call"));
      case "risk" -> model.setRiskLevel("SAFE");
      case "proseNumber" -> model.setSuggestions(List.of("建议降低成本 20%"));
      case "date" -> model.setSummary("2026-07-18 已完成分析");
    }
    assertFalse(new AgentResponseValidator().validate(model, frozen).isEmpty());
  }

  AgentAdviceVO optimization() {
    AgentChatDTO request = new AgentChatDTO();
    request.setOptimizeId(7L);
    return rules.chat("OPTIMIZATION_EXPLAIN", request);
  }

  Map<String, Object> evaluation(String mape, String er) {
    return new LinkedHashMap<>(
        Map.of(
            "mape",
            new BigDecimal(mape),
            "er",
            new BigDecimal(er),
            "costReductionRate",
            0,
            "energyReductionRate",
            0));
  }

  ToolResult<Map<String, Object>> ok(Map<String, Object> data) {
    return ToolResult.of(ToolStatus.OK, data, null, List.of());
  }

  void stub(Map<String, Object> data, ToolResult<Map<String, Object>> conflicts) {
    when(client.query(eq("evaluation"), anyLong(), isNull())).thenReturn(ok(data));
    when(client.query(eq("conflicts"), anyLong(), isNull())).thenReturn(conflicts);
  }
}
