package com.xq.model.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 实时调控 JSON 导入结果 VO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@Builder
public class RealtimeControlImportResultVO {

    /** 任务 ID */
    private Long taskId;

    /** 最新控制记录 ID */
    private Long latestControlId;

    /** 新增记录数 */
    private Integer insertedCount;

    /** 更新记录数 */
    private Integer updatedCount;

    /** 总处理记录数 */
    private Integer totalCount;
}
