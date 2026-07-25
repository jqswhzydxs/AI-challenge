package com.xq.model.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 能源运行策略 VO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@Builder
public class EnergyStrategyVO {

    /** 策略类型 */
    private String type;

    /** 策略标题 */
    private String title;

    /** 策略说明 */
    private String description;

    /** 建议执行时段 */
    private String timeRange;

    /** 影响等级 */
    private String impactLevel;
}
