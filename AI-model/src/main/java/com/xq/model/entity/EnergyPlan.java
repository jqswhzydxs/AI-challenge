package com.xq.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 能源运行方案主表实体.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@TableName("energy_plan")
public class EnergyPlan {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 对应算法任务 ID */
    private Long taskId;
    /** 方案日期 */
    private LocalDate planDate;
    /** 方案状态 */
    private String status;
    /** 优化目标 */
    private String objective;
    /** 电价模式 */
    private String electricPriceMode;
    /** 时间粒度，min */
    private Integer timeInterval;
    /** 电力成本，需由电价派生 */
    private BigDecimal electricityCost;
    /** 蒸汽成本，需由蒸汽单价派生 */
    private BigDecimal steamCost;
    /** 总能源成本，派生字段 */
    private BigDecimal totalEnergyCost;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    private Long createBy;
    private Long updateBy;
    @TableLogic
    private Integer deleted;
    private String remark;
}
