package com.xq.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 约束冲突记录实体.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@TableName("constraint_conflict")
public class ConstraintConflict {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 协同优化方案 ID */
    private Long optimizeId;
    /** 冲突类型，如 ENERGY_SUPPLY_SHORTAGE */
    private String conflictType;
    /** 冲突开始时间 */
    private LocalDateTime startTime;
    /** 冲突结束时间 */
    private LocalDateTime endTime;
    /** 冲突说明 */
    private String description;
    /** 是否解决：0 否，1 是 */
    private Integer resolved;
}
