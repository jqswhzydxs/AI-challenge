package com.xq.model.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 排产方案对比结果 VO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@Builder
public class ScheduleCompareVO {

    /** 基准方案 ID */
    private Long baselineScheduleId;

    /** 总电耗最低的方案 ID */
    private Long bestEnergyScheduleId;

    /** EC 最低的方案 ID */
    private Long bestEcScheduleId;

    /** 最大节能率，% */
    private BigDecimal maxEnergySavingsRate;

    /** 对比项 */
    private List<ScheduleCompareItemVO> records;
}
