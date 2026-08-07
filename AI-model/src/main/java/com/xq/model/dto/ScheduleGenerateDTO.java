package com.xq.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 排产方案生成请求 DTO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
public class ScheduleGenerateDTO {

    /** 排产日期，格式 yyyy-MM-dd */
    @NotBlank(message = "排产日期不能为空")
    private String scheduleDate;

    /** 计划跨度，当前为 24；不传时默认 24 */
    private Integer planHorizon;

    /** 前端录入的次日订单产量，单位 t */
    private BigDecimal plannedQuantity;

    /** 产品名称，不传时按“次日轧钢计划”处理 */
    private String productName;

    /** 计划单位，当前为 hour */
    private String planUnit;

    /** 模型底层数据粒度 */
    private String dataGranularity;

    /** 优化目标，如 MIN_COST */
    private String objective;

    /** 约束条件，如 elecCoefficient */
    private Map<String, Object> constraints;
}
