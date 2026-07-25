package com.xq.model.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 协同优化帕累托前沿点 VO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@Builder
public class JointParetoPointVO {

    /** 协同优化方案 ID */
    private Long optimizeId;

    /** 方案名称 */
    private String name;

    /** 降本率，% */
    private BigDecimal costReductionRate;

    /** 降耗率，% */
    private BigDecimal energyReductionRate;

    /** MAPE，% */
    private BigDecimal mape;

    /** ER，% */
    private BigDecimal er;

    /** 是否推荐方案 */
    private Boolean recommended;

    /** 是否为帕累托最优点 */
    private Boolean paretoOptimal;
}
