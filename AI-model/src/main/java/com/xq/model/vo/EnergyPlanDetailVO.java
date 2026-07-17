package com.xq.model.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 能源运行方案明细 VO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@Builder
public class EnergyPlanDetailVO {

    /** 时间点 */
    private LocalDateTime timestamp;

    /** 设备 ID */
    private Long equipmentId;

    /** 设备名称 */
    private String equipmentName;

    /** 设备输出 */
    private BigDecimal output;

    /** 用电量，kWh */
    private BigDecimal electricityConsumption;

    /** 蒸汽用量 */
    private BigDecimal steamConsumption;

    /** 碳排放，tCO2 */
    private BigDecimal carbonEmissionTco2;

    /** 能源成本，元 */
    private BigDecimal energyCost;
}
