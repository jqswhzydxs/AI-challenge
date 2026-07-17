package com.xq.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警记录实体.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@TableName("warning_record")
public class WarningRecord {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 告警类型 */
    private String warningType;
    /** 告警级别：LOW / MEDIUM / HIGH */
    private String level;
    /** 告警信息 */
    private String message;
    /** 关联业务类型 */
    private String bizType;
    /** 关联业务 ID */
    private Long bizId;
    /** 告警时间 */
    private LocalDateTime warningTime;
    /** 是否处理：0 否，1 是 */
    private Integer handled;
}
