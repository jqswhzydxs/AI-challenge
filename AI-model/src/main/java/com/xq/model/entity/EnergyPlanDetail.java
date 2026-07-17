package com.xq.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 能源运行方案明细实体.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@TableName("energy_plan_detail")
public class EnergyPlanDetail {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 能源方案 ID */
    private Long planId;
    /** 时间点 */
    private LocalDateTime timestamp;
    /** 能源设备 ID */
    private Long equipmentId;
    /** 设备输出 */
    private BigDecimal output;
    /** 用电量，kWh */
    private BigDecimal electricityConsumption;
    /** 蒸汽用量，单位待确认 */
    private BigDecimal steamConsumption;
    /** 碳排放，tCO2 */
    private BigDecimal carbonEmissionTco2;
    /** 能源成本，派生字段 */
    private BigDecimal energyCost;
}
