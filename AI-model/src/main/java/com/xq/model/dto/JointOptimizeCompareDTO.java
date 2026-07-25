package com.xq.model.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 协同优化方案对比请求 DTO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
public class JointOptimizeCompareDTO {

    /** 需要对比的协同优化方案 ID，首个 ID 作为基准方案 */
    @NotEmpty(message = "协同优化方案 ID 列表不能为空")
    private List<Long> optimizeIds;
}
