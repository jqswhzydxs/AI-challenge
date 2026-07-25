package com.xq.model.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 协同优化方案对比结果 VO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@Builder
public class JointOptimizeCompareVO {

    /** 基准方案 ID */
    private Long baselineOptimizeId;

    /** 推荐方案 ID */
    private Long recommendedOptimizeId;

    /** 降本率最高方案 ID */
    private Long bestCostOptimizeId;

    /** 降耗率最高方案 ID */
    private Long bestEnergyOptimizeId;

    /** MAPE 最低方案 ID */
    private Long bestMapeOptimizeId;

    /** 对比项 */
    private List<JointOptimizeCompareItemVO> records;
}
