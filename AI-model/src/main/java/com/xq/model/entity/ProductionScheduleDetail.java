package com.xq.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 排产方案明细实体.
 * <p>
 * 每小时一条记录，共 24 条，对应 daily_plan.json 的 schedule 数组.
 * </p>
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@TableName("production_schedule_detail")
public class ProductionScheduleDetail {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 排产方案 ID */
    private Long scheduleId;
    /** 小时序号，0-23 */
    private Integer hourIndex;
    /** 小时开始时间 */
    private LocalDateTime startTime;
    /** 小时结束时间 */
    private LocalDateTime endTime;
    /** 小时需求 */
    private BigDecimal demand;
    /** 小时排产量，吨 */
    private BigDecimal production;
    /** 小时预测电耗，kWh */
    private BigDecimal elecForecast;
    /** 产线 ID（当前 JSON 未提供） */
    private Long lineId;
    /** 设备 ID */
    private Long equipmentId;
    /** 订单 ID（当前 JSON 未提供） */
    private Long orderId;
    /** 设备负荷率 */
    private BigDecimal equipmentLoadRate;
    /** 是否存在冲突：0 否，1 是 */
    private Integer conflictFlag;
}
