package com.xq.model.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 协同优化结果 VO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@Builder
public class JointOptimizeVO {

    /** 前端兼容字段：方案 ID */
    private Long id;

    /** 协同优化方案 ID */
    private Long optimizeId;

    /** 方案名称 */
    private String name;

    /** 对应任务 ID */
    private Long taskId;

    /** 排产方案 ID */
    private Long scheduleId;

    /** 能源方案 ID */
    private Long energyPlanId;

    /** 方案状态 */
    private String status;

    /** 优化目标与权重 */
    private Map<String, Object> objectives;

    /** 约束条件摘要 */
    private Map<String, Object> constraints;

    /** 前端兼容字段：优化结果摘要 */
    private JointOptimizationResultSummaryVO optimizationResult;

    /** 前端兼容字段：生产计划摘要 */
    private JointProductionPlanSummaryVO productionPlan;

    /** 前端兼容字段：能源计划摘要 */
    private JointEnergyPlanSummaryVO energyPlan;

    /** 降本率，% */
    private BigDecimal costReductionRate;

    /** 降耗率，% */
    private BigDecimal energyReductionRate;

    /** 可执行率，% */
    private BigDecimal executeRate;

    /** 仿真误差 MAPE，% */
    private BigDecimal mape;

    /** 仿真精度，% */
    private BigDecimal simulationAccuracy;

    /** EC降低率，% */
    private BigDecimal ec;

    /** 方案可执行率 ER，% */
    private BigDecimal er;

    /** 是否推荐方案 */
    private Boolean recommended;

    /** 约束冲突列表 */
    private List<ConflictVO> conflicts;

    /** 时序数据 */
    private List<OptimizeTimeseriesVO> timeSeries;
}
