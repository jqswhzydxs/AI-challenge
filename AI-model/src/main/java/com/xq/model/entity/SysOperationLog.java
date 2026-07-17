package com.xq.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志实体.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@TableName("sys_operation_log")
public class SysOperationLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 操作人 ID */
    private Long userId;
    /** 模块 */
    private String module;
    /** 操作名称 */
    private String operation;
    /** 请求地址 */
    private String requestUri;
    /** 请求方式 */
    private String requestMethod;
    /** 请求参数 */
    private String requestParam;
    /** 响应码 */
    private Integer resultCode;
    /** 错误信息 */
    private String errorMessage;
    /** 操作时间 */
    private LocalDateTime operationTime;
}
