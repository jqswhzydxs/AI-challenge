package com.xq.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * 协同优化任务创建请求 DTO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
public class JointOptimizeDTO {

    /** 排产方案 ID */
    @NotNull(message = "排产方案ID不能为空")
    private Long scheduleId;

    /** 能源方案 ID */
    @NotNull(message = "能源方案ID不能为空")
    private Long energyPlanId;

    /** 多目标权重，如 {"productionEfficiency": 0.3, "energyCost": 0.4, ...} */
    private Map<String, Double> objectiveWeights;
}
