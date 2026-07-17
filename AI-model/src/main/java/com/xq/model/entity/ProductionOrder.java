package com.xq.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产订单实体.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@TableName("production_order")
public class ProductionOrder {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 订单编号 */
    private String orderNo;
    /** 产品名称 */
    private String productName;
    /** 产品规格 */
    private String productSpec;
    /** 计划数量，t */
    private BigDecimal plannedQuantity;
    /** 单位，默认 t */
    private String unit;
    /** 交付时间 */
    private LocalDateTime dueTime;
    /** 优先级，数值越小越优先 */
    private Integer priority;
    /** 订单状态 */
    private String status;

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
