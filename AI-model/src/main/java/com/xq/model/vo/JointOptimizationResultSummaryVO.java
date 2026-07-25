package com.xq.model.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 协同优化结果摘要 VO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@Builder
public class JointOptimizationResultSummaryVO {

    /** 总能源成本，元 */
    private BigDecimal totalCost;

    /** 降本率，% */
    private BigDecimal costReductionRate;

    /** 降耗率，% */
    private BigDecimal energyReductionRate;

    /** 削峰率，% */
    private BigDecimal peakShavingRate;

    /** 交期达成率，% */
    private BigDecimal deadlineCompliance;

    /** 可执行率，% */
    private BigDecimal executeRate;

    /** MAPE，% */
    private BigDecimal mape;

    /** EC */
    private BigDecimal ec;

    /** ER，% */
    private BigDecimal er;
}
