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

    /** 总需求 */
    private BigDecimal totalDemand;

    /** 总预测电耗，kWh */
    private BigDecimal totalEnergy;

    /** 小时明细列表 */
    private List<ScheduleDetailVO> details;
}
