package com.xq.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 任务状态 VO.
 * <p>
 * 对应 GET /api/tasks/{taskId} 接口响应.
 * </p>
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskVO {

    /** 任务 ID */
    private Long taskId;

    /** 任务类型 */
    private String taskType;

    /** 任务状态 */
    private String status;

    /** 进度 0-100 */
    private Integer progress;

    /** 状态说明 */
    private String message;

    /** 结果主键，SUCCESS 时返回 */
    private Long resultId;

    /** 失败原因 */
    private String errorMessage;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
