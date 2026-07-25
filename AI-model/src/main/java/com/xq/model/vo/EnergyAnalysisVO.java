package com.xq.model.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 能源分析摘要 VO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@Builder
public class EnergyAnalysisVO {

    /** 样本数 */
    private Long sampleCount;

    /** 总用电量，kWh */
    private BigDecimal totalElectricityConsumption;

    /** 总蒸汽用量 */
    private BigDecimal totalSteamConsumption;

    /** 总碳排放，tCO2 */
    private BigDecimal totalCarbonEmissionTco2;

    /** 估算能源成本，元 */
    private BigDecimal totalEnergyCost;

    /** 平均滞后功率因数 */
    private BigDecimal avgLaggingPowerFactor;

    /** 平均超前功率因数 */
    private BigDecimal avgLeadingPowerFactor;

    /** 最近 MAPE，% */
    private BigDecimal mape;

    /** 最近优化前 EC */
    private BigDecimal ecBefore;

    /** 最近优化后 EC */
    private BigDecimal ecAfter;

    /** 最近可执行率 ER，% */
    private BigDecimal er;
}
