package com.xq.model.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 首页 Dashboard 响应 VO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@Builder
public class DashboardVO {

    /** 今日总能耗，kgce */
    private BigDecimal totalEnergyKgceToday;

    /** 本月总能耗，kgce */
    private BigDecimal totalEnergyKgceMonth;

    /** 生产进度率，% */
    private BigDecimal productionProgressRate;

    /** 能源负荷率，% */
    private BigDecimal energyLoadRate;

    /** 方案执行率，% */
    private BigDecimal schemeExecuteRate;

    /** 告警数量 */
    private Integer warningCount;

    /** 最新告警列表 */
    private List<WarningItemVO> latestWarnings;
}
