package com.xq.model.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 能源实时采集点位推送 DTO.
 */
@Data
public class EnergyRealtimePushDTO {

    /** 采集时间，支持 yyyy-MM-dd HH:mm:ss / ISO 时间 */
    @JsonAlias({"collectTime", "collect_time", "datetime", "date"})
    private String timestamp;

    /** 用电量，kWh */
    @JsonAlias({"elec", "usageKwh", "usage_kwh", "power", "electricity"})
    private BigDecimal electricityConsumption;

    /** 蒸汽用量 */
    @JsonAlias({"steam", "steamUsage", "steam_usage"})
    private BigDecimal steamConsumption;

    /** 碳排放，tCO2 */
    @JsonAlias({"co2", "carbonEmission", "carbon_emission", "CO2_tCO2_"})
    private BigDecimal carbonEmissionTco2;

    private BigDecimal laggingReactivePowerKvarh;

    private BigDecimal leadingReactivePowerKvarh;

    private BigDecimal laggingPowerFactor;

    private BigDecimal leadingPowerFactor;

    private String loadType;

    private String dataQuality;

    private String source;
}
