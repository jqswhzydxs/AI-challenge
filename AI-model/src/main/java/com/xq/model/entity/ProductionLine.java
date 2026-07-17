package com.xq.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 产线实体.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@TableName("production_line")
public class ProductionLine {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 产线编码 */
    private String lineCode;
    /** 产线名称 */
    private String lineName;
    /** 最大产能，t/h */
    private BigDecimal maxCapacity;
    /** 最小产能，t/h */
    private BigDecimal minCapacity;
    /** 状态：AVAILABLE / STOPPED */
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
