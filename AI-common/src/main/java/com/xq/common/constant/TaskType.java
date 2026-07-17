package com.xq.common.constant;

/**
 * 任务类型枚举常量.
 * <p>
 * 统一用于 {@code algorithm_task.task_type} 字段.
 * </p>
 *
 * @author XQ
 * @since 1.0.0
 */
public final class TaskType {

    /** 生产排产任务 */
    public static final String PRODUCTION_SCHEDULE = "PRODUCTION_SCHEDULE";
    /** 能源运行优化任务 */
    public static final String ENERGY_PLAN = "ENERGY_PLAN";
    /** 生产-能源协同优化任务 */
    public static final String JOINT_OPTIMIZATION = "JOINT_OPTIMIZATION";
    /** 实时调控任务 */
    public static final String REALTIME_CONTROL = "REALTIME_CONTROL";
    /** MPC 实时调控任务 */
    public static final String REALTIME_MPC = "REALTIME_MPC";

    private TaskType() {
    }
}
