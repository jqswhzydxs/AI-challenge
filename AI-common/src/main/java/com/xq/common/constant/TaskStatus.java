package com.xq.common.constant;

/**
 * 任务状态枚举常量.
 * <p>
 * 统一用于 {@code algorithm_task.status} 和方案状态字段.
 * </p>
 *
 * @author XQ
 * @since 1.0.0
 */
public final class TaskStatus {

    /** 待执行：后端已创建任务，尚未调用算法 */
    public static final String PENDING = "PENDING";
    /** 执行中：算法正在计算或后端正在保存结果 */
    public static final String RUNNING = "RUNNING";
    /** 执行成功：算法完成且结果已保存 */
    public static final String SUCCESS = "SUCCESS";
    /** 执行失败：算法失败、超时或结果校验失败 */
    public static final String FAILED = "FAILED";
    /** 已取消：用户取消任务 */
    public static final String CANCELED = "CANCELED";
    /** 已发布：方案已发布给业务执行 */
    public static final String PUBLISHED = "PUBLISHED";
    /** 已执行：方案已完成执行 */
    public static final String EXECUTED = "EXECUTED";

    private TaskStatus() {
    }
}
