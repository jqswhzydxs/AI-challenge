package com.xq.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * 排产方案生成请求 DTO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
public class ScheduleGenerateDTO {

    /** 排产日期，格式 yyyy-MM-dd */
    @NotBlank(message = "排产日期不能为空")
    private String scheduleDate;

    /** 计划跨度，当前为 24 */
    @NotNull(message = "计划跨度不能为空")
    private Integer planHorizon;

    /** 计划单位，当前为 hour */
    private String planUnit;

    /** 模型底层数据粒度 */
    private String dataGranularity;

    /** 优化目标，如 MIN_COST */
    private String objective;

    /** 约束条件，如 elecCoefficient */
    private Map<String, Object> constraints;
}
