package com.xq.model.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 协同优化评价指标 VO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@Builder
public class JointOptimizeEvaluationVO {

    /** 协同优化方案 ID */
    private Long optimizeId;

    /** 对应任务 ID */
    private Long taskId;

    /** 方案状态 */
    private String status;

    /** 是否推荐方案 */
    private Boolean recommended;

    /** 降本率，% */
    private BigDecimal costReductionRate;

    /** 降耗率，% */
    private BigDecimal energyReductionRate;

    /** 可执行率，% */
    private BigDecimal executeRate;

    /** MAPE，% */
    private BigDecimal mape;

    /** 仿真精度，% */
    private BigDecimal simulationAccuracy;

    /** EC降低率，% */
    private BigDecimal ec;

    /** ER，% */
    private BigDecimal er;

    /** 冲突数 */
    private Integer conflictCount;

    /** 时序点数 */
    private Integer timeseriesCount;
}
