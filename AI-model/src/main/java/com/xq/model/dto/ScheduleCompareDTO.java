package com.xq.model.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 排产方案对比请求 DTO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
public class ScheduleCompareDTO {

    /** 需要对比的排产方案 ID，首个 ID 作为基准方案 */
    @NotEmpty(message = "排产方案 ID 列表不能为空")
    private List<Long> scheduleIds;
}
