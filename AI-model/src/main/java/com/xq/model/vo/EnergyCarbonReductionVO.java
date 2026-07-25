package com.xq.model.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 碳减排 VO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@Builder
public class EnergyCarbonReductionVO {

    /** 总碳减排，tCO2 */
    private BigDecimal totalCarbonReduction;

    /** 趋势点 */
    private List<EnergyCarbonReductionPointVO> points;
}
