package com.xq.model.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 能源运行方案详情 VO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@Builder
public class EnergyPlanVO {

    /** 能源方案 ID */
    private Long planId;

    /** 对应任务 ID */
    private Long taskId;

    /** 方案日期 */
    private LocalDate planDate;

    /** 方案状态 */
    private String status;

    /** 电力成本，元 */
    private BigDecimal electricityCost;

    /** 蒸汽成本，元 */
    private BigDecimal steamCost;

    /** 总能源成本，元 */
    private BigDecimal totalEnergyCost;

    /** 方案明细列表 */
    private List<EnergyPlanDetailVO> details;
}
