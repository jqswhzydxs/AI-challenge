package com.xq.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 能源介质实体.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@TableName("energy_medium")
public class EnergyMedium {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 介质编码，如 ELECTRICITY、STEAM */
    private String mediumCode;
    /** 介质名称 */
    private String mediumName;
    /** 单位，如 kWh、t */
    private String unit;
    /** 折标煤系数 */
    private BigDecimal standardCoalFactor;

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
