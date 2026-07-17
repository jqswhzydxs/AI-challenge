package com.xq.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 能源设备实体.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@TableName("energy_equipment")
public class EnergyEquipment {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 设备编码 */
    private String equipmentCode;
    /** 设备名称 */
    private String equipmentName;
    /** 设备类型，如 BOILER */
    private String equipmentType;
    /** 最小输出 */
    private BigDecimal minOutput;
    /** 最大输出 */
    private BigDecimal maxOutput;
    /** 效率 */
    private BigDecimal efficiency;
    /** 设备状态 */
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
