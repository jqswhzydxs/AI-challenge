package com.xq.model.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 协同优化中的生产计划摘要 VO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@Builder
public class JointProductionPlanSummaryVO {

    /** 排产方案 ID */
    private Long scheduleId;

    /** 方案名称 */
    private String scheduleName;

    /** 排产日期 */
    private LocalDate scheduleDate;

    /** 计划开始时间 */
    private LocalDateTime planStartTime;

    /** 总产量，吨 */
    private BigDecimal totalProduction;

    /** 总电耗，kWh */
    private BigDecimal totalEnergy;

    /** 优化前 EC */
    private BigDecimal ecBaseline;

    /** 优化后 EC */
    private BigDecimal ecOptimized;

    /** 明细条数 */
    private Integer detailCount;
}
