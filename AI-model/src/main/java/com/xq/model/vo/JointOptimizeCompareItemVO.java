package com.xq.model.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 协同优化方案对比项 VO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@Builder
public class JointOptimizeCompareItemVO {

    /** 协同优化方案 ID */
    private Long optimizeId;

    /** 方案名称 */
    private String name;

    /** 是否为基准方案 */
    private Boolean baseline;

    /** 是否推荐方案 */
    private Boolean recommended;

    /** 降本率，% */
    private BigDecimal costReductionRate;

    /** 相对基准降本率差值 */
    private BigDecimal costReductionDelta;

    /** 降耗率，% */
    private BigDecimal energyReductionRate;

    /** 相对基准降耗率差值 */
    private BigDecimal energyReductionDelta;

    /** MAPE，% */
    private BigDecimal mape;

    /** 相对基准 MAPE 差值 */
    private BigDecimal mapeDelta;

    /** EC */
    private BigDecimal ec;

    /** ER，% */
    private BigDecimal er;

    /** 可执行率，% */
    private BigDecimal executeRate;

    /** 冲突数 */
    private Integer conflictCount;
}
