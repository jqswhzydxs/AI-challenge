package com.xq.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产设备实体.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@TableName("production_equipment")
public class ProductionEquipment {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 所属产线 ID */
    private Long lineId;
    /** 设备编码 */
    private String equipmentCode;
    /** 设备名称 */
    private String equipmentName;
    /** 设备类型 */
    private String equipmentType;
    /** 额定功率，kW */
    private BigDecimal ratedPower;
    /** 状态：AVAILABLE / STOPPED / MAINTAINING */
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
