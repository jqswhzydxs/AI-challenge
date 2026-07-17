package com.xq.model.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 协同优化结果 VO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@Builder
public class JointOptimizeVO {

    /** 协同优化方案 ID */
    private Long optimizeId;

    /** 对应任务 ID */
    private Long taskId;

    /** 排产方案 ID */
    private Long scheduleId;

    /** 能源方案 ID */
    private Long energyPlanId;

    /** 降本率，% */
    private BigDecimal costReductionRate;

    /** 降耗率，% */
    private BigDecimal energyReductionRate;

    /** 可执行率，% */
    private BigDecimal executeRate;

    /** 仿真误差 MAPE，% */
    private BigDecimal mape;

    /** 单位合格产品能耗 */
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
