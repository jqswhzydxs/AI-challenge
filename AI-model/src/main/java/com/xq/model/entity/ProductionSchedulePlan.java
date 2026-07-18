package com.xq.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 排产方案主表实体.
 * <p>
 * 对应日级排产模型的方案主记录，明细见 {@link ProductionScheduleDetail}.
 * </p>
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@TableName("production_schedule_plan")
public class ProductionSchedulePlan {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 对应算法任务 ID */
    private Long taskId;
    /** 方案名称 */
    private String scheduleName;
    /** 排产日期 */
    private LocalDate scheduleDate;
    /** 计划开始时间 */
    private LocalDateTime planStartTime;
    /** 计划跨度，当前为 24 */
    private Integer planHorizon;
    /** 计划单位，当前为 hour */
    private String planUnit;
    /** 模型底层数据粒度，当前为 1 minute */
    private String dataGranularity;
    /** 方案状态 */
    private String status;
    /** 优化目标 */
    private String objective;
    /** 电耗系数，kWh/吨 */
    private BigDecimal elecCoefficient;
    /** 总需求 */
    private BigDecimal totalDemand;
    /** 总排产量，可由明细汇总 */
    private BigDecimal totalProduction;
    /** 总预测电耗，kWh */
    private BigDecimal totalEnergy;
    /** daily_plan.json 原文，可选冗余 */
    private String rawPlanJson;

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
