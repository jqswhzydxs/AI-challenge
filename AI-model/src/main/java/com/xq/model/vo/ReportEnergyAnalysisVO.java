package com.xq.model.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 报表能源分析 VO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@Builder
public class ReportEnergyAnalysisVO {

    /** 统计记录数 */
    private Long statisticCount;

    /** 总综合能耗，kgce */
    private BigDecimal totalEnergyKgce;

    /** 总能源成本，元 */
    private BigDecimal totalEnergyCost;

    /** 总降本金额，元 */
    private BigDecimal totalCostSaving;

    /** 总碳减排，tCO2 */
    private BigDecimal totalCarbonReduction;

    /** 总产量，t */
    private BigDecimal totalProductionOutput;

    /** 单位产量综合能耗，kgce/t */
    private BigDecimal energyKgcePerTon;

    /** 单位产量能源成本，元/t */
    private BigDecimal energyCostPerTon;

    /** 最近 MAPE，% */
    private BigDecimal mape;

    /** 最近优化前 EC */
    private BigDecimal ecBefore;

    /** 最近优化后 EC */
    private BigDecimal ecAfter;

    /** 最近可执行率 ER，% */
    private BigDecimal er;
}
