package com.xq.model.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产订单 VO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@Builder
public class ProductionOrderVO {

    /** 订单 ID */
    private Long orderId;

    /** 订单编号 */
    private String orderNo;

    /** 产品名称 */
    private String productName;

    /** 计划数量，t */
    private BigDecimal plannedQuantity;

    /** 单位 */
    private String unit;

    /** 交付时间 */
    private LocalDateTime dueTime;

    /** 优先级 */
    private Integer priority;

    /** 订单状态 */
    private String status;
}
