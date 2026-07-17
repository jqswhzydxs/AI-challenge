package com.xq.model.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 约束冲突 VO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@Builder
public class ConflictVO {

    /** 冲突类型，如 ENERGY_SUPPLY_SHORTAGE */
    private String conflictType;

    /** 冲突开始时间 */
    private LocalDateTime startTime;

    /** 冲突结束时间 */
    private LocalDateTime endTime;

    /** 冲突说明 */
    private String description;
}
