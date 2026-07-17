package com.xq.model.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 实时能源数据点 VO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@Builder
public class RealtimeDataPointVO {

    /** 时间戳 */
    private String timestamp;

    /** 用电量，kWh */
    private BigDecimal electricityConsumption;

    /** 蒸汽用量 */
    private BigDecimal steamConsumption;

    /** 碳排放，tCO2 */
    private BigDecimal carbonEmissionTco2;

    /** 滞后无功电量，kVarh */
    private BigDecimal laggingReactivePowerKvarh;

    /** 超前无功电量，kVarh */
    private BigDecimal leadingReactivePowerKvarh;

    /** 滞后功率因数，% */
    private BigDecimal laggingPowerFactor;

    /** 超前功率因数，% */
    private BigDecimal leadingPowerFactor;

    /** 日内秒数 */
    private Integer nsm;

    /** 工作日/周末 */
    private String weekStatus;

    /** 星期 */
    private String dayOfWeek;

    /** 负荷类型 */
    private String loadType;
}
