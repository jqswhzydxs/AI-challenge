package com.xq.model.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警项 VO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@Builder
public class WarningItemVO {

    /** 告警 ID */
    private Long warningId;

    /** 告警级别：LOW / MEDIUM / HIGH */
    private String level;

    /** 告警信息 */
    private String message;

    /** 告警时间 */
    private LocalDateTime warningTime;
}
