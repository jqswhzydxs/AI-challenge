package com.xq.model.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 能源综合趋势点 VO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@Builder
public class EnergyTrendPointVO {

    /** 统计日期 */
    private LocalDate date;

    /** 总能耗，kgce */
    private BigDecimal totalEnergyKgce;

    /** 能源成本，元 */
    private BigDecimal energyCost;

    /** 降本金额，元 */
    private BigDecimal costSaving;

    /** 碳减排，tCO2 */
    private BigDecimal carbonReduction;

    /** 产量，t */
    private BigDecimal productionOutput;
}
