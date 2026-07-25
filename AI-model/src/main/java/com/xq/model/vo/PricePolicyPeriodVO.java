package com.xq.model.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 峰谷平电价时段 VO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@Builder
public class PricePolicyPeriodVO {

    /** 时段类型：PEAK / FLAT / VALLEY */
    private String type;

    /** 中文名称 */
    private String name;

    /** 时间范围 */
    private String timeRange;

    /** 单价，元/kWh */
    private BigDecimal price;
}
