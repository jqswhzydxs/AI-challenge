package com.xq.model.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 单个排产方案对比项 VO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@Builder
public class ScheduleCompareItemVO {

    /** 排产方案 ID */
    private Long scheduleId;

    /** 方案名称 */
    private String scheduleName;

    /** 排产日期 */
    private LocalDate scheduleDate;

    /** 是否为基准方案 */
    private Boolean baseline;

    /** 总排产量，吨 */
    private BigDecimal totalProduction;

    /** 相对基准方案的产量差值，吨 */
    private BigDecimal productionDelta;

    /** 总预测电耗，kWh */
    private BigDecimal totalEnergy;

    /** 相对基准方案的电耗差值，kWh */
    private BigDecimal energyDelta;

    /** 相对基准方案的电耗变化率，% */
    private BigDecimal energyDeltaRate;

    /** 优化前 EC */
    private BigDecimal ecBaseline;

    /** 优化后 EC */
    private BigDecimal ecOptimized;

    /** EC 降低百分比 */
    private BigDecimal ecReduction;

    /** 节能率，% */
    private BigDecimal energySavingsRate;

    /** 平均负荷率，% */
    private BigDecimal avgLoadRate;

    /** 交期达成率，% */
    private BigDecimal deadlineCompliance;

    /** 小时明细条数 */
    private Integer detailCount;
}
