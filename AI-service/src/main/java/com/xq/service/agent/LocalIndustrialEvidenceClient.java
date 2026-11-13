package com.xq.service.agent;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xq.common.result.Result;
import com.xq.contracts.agent.ToolResult;
import com.xq.contracts.agent.ToolStatus;
import com.xq.mapper.EnergyPlanDetailMapper;
import com.xq.mapper.EnergyPlanMapper;
import com.xq.model.dto.PageQueryDTO;
import com.xq.model.entity.EnergyPlan;
import com.xq.model.entity.EnergyPlanDetail;
import com.xq.service.JointOptimizationService;
import com.xq.service.ReportService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class LocalIndustrialEvidenceClient implements IndustrialEvidenceClient {
  private final JointOptimizationService jointOptimizationService;
  private final ReportService reportService;
  private final EnergyPlanMapper energyPlanMapper;
  private final EnergyPlanDetailMapper energyPlanDetailMapper;
  private final EnergyEvidenceService energyEvidenceService;

  @Override
  @Transactional(readOnly = true)
  public ToolResult<Map<String, Object>> query(String tool, Long subjectId, LocalDate date) {
    if (!"dailyReport".equals(tool) && (subjectId == null || subjectId <= 0)) {
      return outcome(ToolStatus.ERROR, null, "INVALID_SUBJECT");
    }
    try {
      return switch (tool) {
        case "jointOptimization" -> business(jointOptimizationService.getResult(subjectId));
        case "evaluation" -> business(jointOptimizationService.getEvaluation(subjectId));
        case "conflicts" -> {
          Result<?> result = jointOptimizationService.listConflicts(null, subjectId);
          if (result == null || result.getCode() != 200)
            yield outcome(ToolStatus.ERROR, null, "CORE_QUERY_FAILED");
          if (!(result.getData() instanceof List<?> conflicts))
            yield outcome(ToolStatus.ERROR, null, "INVALID_RESPONSE");
          yield outcome(
              ToolStatus.OK,
              Map.of("conflicts", conflicts, "conflictCount", conflicts.size()),
              null);
        }
        case "energyPlan" -> energy(subjectId);
        case "dailyReport" -> report(date);
        default -> outcome(ToolStatus.ERROR, null, "UNKNOWN_TOOL");
      };
    } catch (RuntimeException exception) {
      return outcome(ToolStatus.ERROR, null, "CORE_QUERY_FAILED");
    }
  }

  private ToolResult<Map<String, Object>> energy(Long id) {
    EnergyPlan plan = energyPlanMapper.selectById(id);
    if (plan == null) return outcome(ToolStatus.EMPTY, null, "SUBJECT_NOT_FOUND");
    List<EnergyPlanDetail> details =
        energyPlanDetailMapper.selectList(
            new LambdaQueryWrapper<EnergyPlanDetail>()
                .eq(EnergyPlanDetail::getPlanId, id)
                .orderByAsc(EnergyPlanDetail::getTimestamp));
    Map<String, Object> data = energyEvidenceService.describe(plan, details);
    boolean complete = Boolean.TRUE.equals(data.get("loadEvidenceComplete"))
        && Boolean.TRUE.equals(data.get("costBreakdownComplete"));
    return outcome(complete ? ToolStatus.OK : ToolStatus.PARTIAL, data, null);
  }

  private ToolResult<Map<String, Object>> report(LocalDate date) {
    if (date == null) return outcome(ToolStatus.ERROR, null, "INVALID_DATE");
    PageQueryDTO query = new PageQueryDTO();
    query.setDate(date.toString());
    query.setStartDate(date.toString());
    query.setEndDate(date.toString());
    Result<?> analysis = reportService.getEnergyAnalysis(query);
    if (analysis == null || analysis.getCode() != 200)
      return outcome(ToolStatus.ERROR, null, "CORE_QUERY_FAILED");
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("businessDate", date.toString());
    data.put("energyAnalysis", analysis.getData());
    if (analysis.getData() == null) return outcome(ToolStatus.EMPTY, null, "NO_REPORT_DATA");
    Map<String, Object> summary = JSON.parseObject(JSON.toJSONString(analysis.getData()));
    Number count = (Number) summary.get("statisticCount");
    if (count == null || count.longValue() == 0)
      return outcome(ToolStatus.EMPTY, data, "NO_REPORT_DATA");
    return outcome(ToolStatus.OK, data, null);
  }

  private ToolResult<Map<String, Object>> business(Result<?> result) {
    if (result == null || result.getCode() != 200)
      return outcome(ToolStatus.ERROR, null, "CORE_QUERY_FAILED");
    if (result.getData() == null) return outcome(ToolStatus.EMPTY, null, "SUBJECT_NOT_FOUND");
    Map<String, Object> data = JSON.parseObject(JSON.toJSONString(result.getData()));
    if (result.getData() instanceof com.xq.model.vo.JointOptimizeEvaluationVO) {
      Map<String, Object> legacy = new LinkedHashMap<>();
      for (String key : List.of("mape", "er", "executeRate", "energyReductionRate", "costReductionRate")) {
        legacy.put(key, data.get(key));
        data.put(key, null);
      }
      data.put("legacyModelIndicators", legacy);
      data.put("measurementType", "PLAN_MODEL_ASSUMPTIONS");
      data.put("metricBasis", "MAPE/ER含固定演示假设；电耗降低率与EC同源；综合能源和成本基准未核验，不能作为独立实测收益");
      return outcome(ToolStatus.PARTIAL, data, null);
    }
    return outcome(ToolStatus.OK, data, null);
  }

  private boolean complete(List<BigDecimal> values) {
    return !values.isEmpty() && values.stream().allMatch(java.util.Objects::nonNull);
  }

  private BigDecimal sum(List<BigDecimal> values) {
    return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private ToolResult<Map<String, Object>> outcome(
      ToolStatus status, Map<String, Object> data, String code) {
    return ToolResult.of(
        status, data, code, status == ToolStatus.PARTIAL ? List.of("部分业务指标缺失") : List.of());
  }
}
