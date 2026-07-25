package com.xq.model.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 能源负荷预测摘要 VO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@Builder
public class LoadForecastVO {

    /** 峰值时段 */
    private String peakHour;

    /** 峰值负荷 */
    private BigDecimal peakLoad;

    /** 谷值时段 */
    private String valleyHour;

    /** 谷值负荷 */
    private BigDecimal valleyLoad;

    /** 平均负荷 */
    private BigDecimal avgLoad;
}
