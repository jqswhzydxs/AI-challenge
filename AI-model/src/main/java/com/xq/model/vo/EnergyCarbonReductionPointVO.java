package com.xq.model.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 碳减排趋势点 VO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@Builder
public class EnergyCarbonReductionPointVO {

    /** 日期 */
    private LocalDate date;

    /** 当日碳减排，tCO2 */
    private BigDecimal carbonReduction;

    /** 累计碳减排，tCO2 */
    private BigDecimal cumulativeCarbonReduction;
}
