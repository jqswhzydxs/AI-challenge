package com.xq.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 协同优化时序明细实体.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@TableName("joint_optimization_timeseries")
public class JointOptimizationTimeseries {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 协同优化方案 ID */
    private Long optimizeId;
    /** 时间点 */
    private LocalDateTime timestamp;
    /** 计划产量，t */
    private BigDecimal plannedOutput;
    /** 用电量，kWh */
    private BigDecimal electricityConsumption;
    /** 蒸汽用量，单位待确认 */
    private BigDecimal steamConsumption;
    /** 碳排放，tCO2 */
    private BigDecimal carbonEmissionTco2;
    /** 能源成本，派生字段 */
    private BigDecimal energyCost;
}
