package com.xq.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 协同优化方案主表实体.
 * <p>
 * 存储排产-能源协同优化的结果，含 MAPE/EC/ER 等核心评价指标.
 * </p>
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@TableName("joint_optimization_plan")
public class JointOptimizationPlan {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 对应算法任务 ID */
    private Long taskId;
    /** 排产方案 ID */
    private Long scheduleId;
    /** 能源方案 ID */
    private Long energyPlanId;
    /** 方案状态 */
    private String status;
    /** 是否推荐方案：0 否，1 是 */
    private Integer recommended;
    /** 降本率，% */
    private BigDecimal costReductionRate;
    /** 降耗率，% */
    private BigDecimal energyReductionRate;
    /** 可执行率，% */
    private BigDecimal executeRate;
    /** 仿真误差 MAPE，% */
    private BigDecimal mape;
    /** 单位合格产品能耗 */
    private BigDecimal ec;
    /** 方案可执行率 ER，% */
    private BigDecimal er;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    private Long createBy;
    private Long updateBy;
    @TableLogic
    private Integer deleted;
    private String remark;
}
