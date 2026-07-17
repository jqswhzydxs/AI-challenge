package com.xq.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 能源运行方案生成请求 DTO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
public class EnergyPlanGenerateDTO {

    /** 方案日期，格式 yyyy-MM-dd */
    @NotBlank(message = "方案日期不能为空")
    private String planDate;

    /** 时间范围，如 24h */
    private String timeRange;

    /** 电价模式，如 PEAK_VALLEY */
    private String electricPriceMode;

    /** 优化目标，如 MIN_ENERGY_COST */
    private String objective;

    /** 约束条件 */
    private Map<String, Object> constraints;
}
