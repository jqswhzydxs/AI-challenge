package com.xq.model.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 排产方案详情 VO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@Builder
public class SchedulePlanVO {

    /** 排产方案 ID */
    private Long scheduleId;

    /** 对应任务 ID */
    private Long taskId;

    /** 方案名称 */
    private String scheduleName;

    /** 计划开始时间 */
    private LocalDateTime planStartTime;

    /** 计划跨度 */
    private Integer planHorizon;

    /** 计划单位 */
    private String planUnit;

    /** 数据粒度 */
    private String dataGranularity;

    /** 方案状态 */
    private String status;

    /** 电耗系数，kWh/吨 */
    private BigDecimal elecCoefficient;

    /** 优化前 EC 基准值，kWh/吨 */
    private BigDecimal ecBaseline;

    /** 优化后 EC 值，kWh/吨 */
    private BigDecimal ecOptimized;

    /** EC 降低百分比 */
    private BigDecimal ecReduction;

    /** 最优温度，℃ */
    private BigDecimal optimalTemperature;

    /** 最优速度 */
    private BigDecimal optimalSpeed;

    /** 总需求 */
    private BigDecimal totalDemand;

    /** 总排产量，吨 */
    private BigDecimal totalProduction;

    /** 总预测电耗，kWh */
    private BigDecimal totalEnergy;

    /** 小时明细列表 */
    private List<ScheduleDetailVO> details;
}
