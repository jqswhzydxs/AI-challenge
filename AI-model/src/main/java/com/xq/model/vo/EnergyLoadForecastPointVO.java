package com.xq.model.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 能源负荷预测点 VO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@Builder
public class EnergyLoadForecastPointVO {

    /** 时间点 */
    private LocalDateTime timestamp;

    /** 小时序号 */
    private Integer hourIndex;

    /** 时段平均功率，kW */
    private BigDecimal electricityLoad;

    /** 时段平均蒸汽流量，t/h */
    private BigDecimal steamLoad;

    /** 预计能源成本，元 */
    private BigDecimal energyCost;
}
