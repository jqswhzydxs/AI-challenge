package com.xq.model.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 能源优化结果摘要 VO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@Builder
public class EnergyOptimizationResultVO {

    /** 预计节省率，% */
    private BigDecimal estimatedSavings;

    /** 优化后总成本，元 */
    private BigDecimal totalCost;

    /** 估算优化前总成本，元 */
    private BigDecimal originalCost;

    /** 削峰率，% */
    private BigDecimal peakShavingRate;
}
