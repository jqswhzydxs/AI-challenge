package com.xq.model.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 日级排产 JSON 导入结果 VO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@Builder
public class ImportPlanResultVO {

    /** 任务 ID */
    private Long taskId;

    /** 排产方案 ID */
    private Long scheduleId;

    /** 导入明细数 */
    private Integer detailCount;
}
