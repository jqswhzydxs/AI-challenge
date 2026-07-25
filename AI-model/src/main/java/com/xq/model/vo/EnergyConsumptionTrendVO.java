package com.xq.model.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 能耗趋势 VO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@Builder
public class EnergyConsumptionTrendVO {

    /** 聚合粒度：HOUR / DAY */
    private String granularity;

    /** 趋势点 */
    private List<EnergyConsumptionTrendPointVO> points;
}
