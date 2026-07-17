package com.xq.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 算法任务实体.
 * <p>
 * 统一保存排产、能源、协同优化、实时调控任务的状态流转和原始 JSON.
 * </p>
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@TableName("algorithm_task")
public class AlgorithmTask {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 任务类型，参考 {@link com.xq.common.constant.TaskType} */
    private String taskType;
    /** 状态，参考 {@link com.xq.common.constant.TaskStatus} */
    private String status;
    /** 进度 0-100 */
    private Integer progress;
    /** 结果主键，如 schedule_id / plan_id / optimize_id */
    private Long resultId;
    /** 状态说明 */
    private String message;
    /** 失败原因 */
    private String errorMessage;
    /** 重试次数 */
    private Integer retryCount;
    /** 算法名称 */
    private String algorithmName;
    /** 算法版本 */
    private String algorithmVersion;
    /** 算法结果文件名，如 daily_plan.json */
    private String resultFileName;
    /** 模型训练/拟合使用记录数 */
    private Integer trainingRecordCount;
    /** 前端请求原始 JSON */
    private String frontendRequestJson;
    /** 后端传给算法的原始 JSON */
    private String algorithmRequestJson;
    /** 算法返回原始 JSON */
    private String algorithmResponseJson;
    /** 任务开始时间 */
    private LocalDateTime startTime;
    /** 任务结束时间 */
    private LocalDateTime finishTime;

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
