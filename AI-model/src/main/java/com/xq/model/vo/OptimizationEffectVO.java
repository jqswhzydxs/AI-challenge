package com.xq.model.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 优化效果报表 VO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@Builder
public class OptimizationEffectVO {

    /** 降本金额，元 */
    private BigDecimal costSaving;

    /** 降耗率，% */
    private BigDecimal energyReductionRate;

    /** 碳减排，tCO2 */
    private BigDecimal carbonReduction;

    /** 仿真误差 MAPE，% */
    private BigDecimal mape;

    /** 优化前 EC */
    private BigDecimal ecBefore;

    /** 优化后 EC */
    private BigDecimal ecAfter;

    /** 方案可执行率 ER，% */
    private BigDecimal er;
}
