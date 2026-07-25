package com.xq.model.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 排产方案小时明细 VO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@Builder
public class ScheduleDetailVO {

    /** 明细 ID */
    private Long detailId;

    /** 小时序号，0-23 */
    private Integer hourIndex;

    /** 产线 ID */
    private Long lineId;

    /** 产线名称 */
    private String lineName;

    /** 小时开始时间 */
    private LocalDateTime startTime;

    /** 小时结束时间 */
    private LocalDateTime endTime;

    /** 小时需求 */
    private BigDecimal demand;

    /** 小时排产量，吨 */
    private BigDecimal production;

    /** 小时预测电耗，kWh */
    private BigDecimal elecForecast;

    /** 设备负荷率，% */
    private BigDecimal equipmentLoadRate;
}
