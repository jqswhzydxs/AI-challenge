package com.xq.model.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 协同优化时序数据点 VO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@Builder
public class OptimizeTimeseriesVO {

    /** 时间点 */
    private LocalDateTime timestamp;

    /** 计划产量，t */
    private BigDecimal plannedOutput;

    /** 用电量，kWh */
    private BigDecimal electricityConsumption;

    /** 蒸汽用量 */
    private BigDecimal steamConsumption;

    /** 碳排放，tCO2 */
    private BigDecimal carbonEmissionTco2;

    /** 能源成本，元 */
    private BigDecimal energyCost;
}
