package com.xq.service.agent;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xq.common.result.Result;
import com.xq.mapper.EnergyPlanDetailMapper;
import com.xq.mapper.EnergyPlanMapper;
import com.xq.model.dto.PageQueryDTO;
import com.xq.model.entity.EnergyPlan;
import com.xq.model.entity.EnergyPlanDetail;
import com.xq.model.vo.ConflictVO;
import com.xq.model.vo.EnergyCarbonReductionVO;
import com.xq.model.vo.EnergyTrendVO;
import com.xq.model.vo.JointOptimizeEvaluationVO;
import com.xq.model.vo.JointOptimizeVO;
import com.xq.model.vo.OptimizationEffectVO;
import com.xq.model.vo.ReportEnergyAnalysisVO;
import com.xq.service.JointOptimizationService;
import com.xq.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 暴露给 Spring AI Tool Calling 的工业业务工具.
 * <p>
 * 大模型本身不直接访问数据库，而是通过这些受控工具读取已有业务服务和 Mapper 的结果，
 * 从而保证回答中的指标、冲突和报表数据可追溯。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class IndustrialAgentTools {

    private final JointOptimizationService jointOptimizationService;
    private final ReportService reportService;
    private final EnergyPlanMapper energyPlanMapper;
    private final EnergyPlanDetailMapper energyPlanDetailMapper;

    /** 查询完整协同优化结果，供模型解释方案推荐原因和时序表现。 */
    @Tool(description = "Query a production-energy joint optimization result by optimizeId, including metrics, conflicts and time series.")
    public String queryJointOptimization(Long optimizeId) {
        try {
            Result<JointOptimizeVO> result = jointOptimizationService.getResult(optimizeId);
            return JSON.toJSONString(result.getData());
        } catch (RuntimeException e) {
            return error("queryJointOptimization", e);
        }
    }

    /** 查询协同优化核心评价指标，供模型分析降本率、降耗率、MAPE、ER 等。 */
    @Tool(description = "Query core evaluation metrics of a joint optimization result by optimizeId.")
    public String queryJointOptimizationEvaluation(Long optimizeId) {
        try {
            Result<JointOptimizeEvaluationVO> result = jointOptimizationService.getEvaluation(optimizeId);
            return JSON.toJSONString(result.getData());
        } catch (RuntimeException e) {
            return error("queryJointOptimizationEvaluation", e);
        }
    }

    /** 查询约束冲突列表，供模型判断方案是否存在设备出力或执行风险。 */
    @Tool(description = "Query constraint conflicts of a joint optimization result by optimizeId.")
    public String queryOptimizationConflicts(Long optimizeId) {
        try {
            Result<List<ConflictVO>> result = jointOptimizationService.listConflicts(null, optimizeId);
            return JSON.toJSONString(result.getData());
        } catch (RuntimeException e) {
            return error("queryOptimizationConflicts", e);
        }
    }

    /** 查询能源方案及聚合指标，减少模型处理大批明细数据的负担。 */
    @Tool(description = "Query an energy operation plan by energyPlanId, including cost, load and detail summary.")
    public String queryEnergyPlan(Long energyPlanId) {
        try {
            EnergyPlan plan = energyPlanMapper.selectById(energyPlanId);
            if (plan == null) {
                Map<String, Object> notFound = new LinkedHashMap<>();
                notFound.put("error", "energy plan not found");
                notFound.put("energyPlanId", energyPlanId);
                return JSON.toJSONString(notFound);
            }
            List<EnergyPlanDetail> details = energyPlanDetailMapper.selectList(
                    new LambdaQueryWrapper<EnergyPlanDetail>()
                            .eq(EnergyPlanDetail::getPlanId, energyPlanId)
                            .orderByAsc(EnergyPlanDetail::getTimestamp)
            );
            // 汇总模型最需要的指标，同时保留明细用于必要时追溯。
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("planId", plan.getId());
            payload.put("taskId", plan.getTaskId());
            payload.put("planDate", plan.getPlanDate());
            payload.put("status", plan.getStatus());
            payload.put("objective", plan.getObjective());
            payload.put("electricPriceMode", plan.getElectricPriceMode());
            payload.put("electricityCost", plan.getElectricityCost());
            payload.put("steamCost", plan.getSteamCost());
            payload.put("totalEnergyCost", plan.getTotalEnergyCost());
            payload.put("detailCount", details.size());
            payload.put("peakElectricity", max(details.stream().map(EnergyPlanDetail::getElectricityConsumption).toList()));
            payload.put("avgElectricity", avg(details.stream().map(EnergyPlanDetail::getElectricityConsumption).toList()));
            payload.put("peakOutput", max(details.stream().map(EnergyPlanDetail::getOutput).toList()));
            payload.put("totalCarbonEmissionTco2", sum(details.stream().map(EnergyPlanDetail::getCarbonEmissionTco2).toList()));
            payload.put("details", details);
            return JSON.toJSONString(payload);
        } catch (RuntimeException e) {
            return error("queryEnergyPlan", e);
        }
    }

    /** 汇总日报相关指标，供模型生成适合展示的生产-能源优化日报。 */
    @Tool(description = "Query daily report metrics, including optimization effect, energy analysis, trend and carbon reduction.")
    public String queryDailyReportSummary() {
        try {
            PageQueryDTO query = new PageQueryDTO();
            Map<String, Object> payload = new LinkedHashMap<>();
            Result<OptimizationEffectVO> effect = reportService.getOptimizationEffect(query);
            Result<ReportEnergyAnalysisVO> analysis = reportService.getEnergyAnalysis(query);
            Result<EnergyTrendVO> trend = reportService.getEnergyTrend(query);
            Result<EnergyCarbonReductionVO> carbon = reportService.getCarbonReduction(query);
            payload.put("optimizationEffect", effect.getData());
            payload.put("energyAnalysis", analysis.getData());
            payload.put("energyTrend", trend.getData());
            payload.put("carbonReduction", carbon.getData());
            return JSON.toJSONString(payload);
        } catch (RuntimeException e) {
            return error("queryDailyReportSummary", e);
        }
    }

    /** 工具调用异常也转成 JSON，避免一次查询失败导致整个 Agent 调用中断。 */
    private String error(String toolName, RuntimeException e) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tool", toolName);
        payload.put("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        return JSON.toJSONString(payload);
    }

    private BigDecimal value(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private BigDecimal sum(List<BigDecimal> values) {
        return values.stream().map(this::value).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal max(List<BigDecimal> values) {
        return values.stream().map(this::value).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
    }

    private BigDecimal avg(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return sum(values).divide(BigDecimal.valueOf(values.size()), 4, java.math.RoundingMode.HALF_UP);
    }
}
