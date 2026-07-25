package com.xq.model.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 能耗趋势点 VO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@Builder
public class EnergyConsumptionTrendPointVO {

    /** 时间标签 */
    private String time;

    /** 用电量，kWh */
    private BigDecimal electricityConsumption;

    /** 蒸汽用量 */
    private BigDecimal steamConsumption;

    /** 碳排放，tCO2 */
    private BigDecimal carbonEmissionTco2;

    /** 估算能源成本，元 */
    private BigDecimal energyCost;
}
