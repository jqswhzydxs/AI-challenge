package com.xq.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 评价指标记录实体.
 * <p>
 * 保存 MAPE、EC、ER 和优化前后对比指标.
 * </p>
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@TableName("evaluation_metric")
public class EvaluationMetric {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 业务类型：SCHEDULE / ENERGY / JOINT */
    private String bizType;
    /** 业务主键 */
    private Long bizId;
    /** 仿真误差 MAPE，% */
    private BigDecimal mape;
    /** 优化前 EC */
    private BigDecimal ecBefore;
    /** 优化后 EC */
    private BigDecimal ecAfter;
    /** 方案可执行率 ER，% */
    private BigDecimal er;
    /** 降本金额，元 */
    private BigDecimal costSaving;
    /** 碳减排，tCO2 */
    private BigDecimal carbonReduction;
    /** 指标计算时间 */
    private LocalDateTime calculateTime;
}
