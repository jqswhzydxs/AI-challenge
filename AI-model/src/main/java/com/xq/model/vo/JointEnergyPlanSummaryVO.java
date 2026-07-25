package com.xq.model.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 协同优化中的能源计划摘要 VO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@Builder
public class JointEnergyPlanSummaryVO {

    /** 能源方案 ID */
    private Long energyPlanId;

    /** 方案日期 */
    private LocalDate planDate;

    /** 电力成本，元 */
    private BigDecimal electricityCost;

    /** 蒸汽成本，元 */
    private BigDecimal steamCost;

    /** 总能源成本，元 */
    private BigDecimal totalEnergyCost;

    /** 峰值负荷 */
    private BigDecimal peakLoad;

    /** 平均负荷 */
    private BigDecimal avgLoad;

    /** 明细条数 */
    private Integer detailCount;
}
