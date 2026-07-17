package com.xq.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 报表统计实体.
 * <p>
 * 如果报表直接实时查询压力较大，可定时汇总到此表.
 * </p>
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@TableName("report_statistic")
public class ReportStatistic {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 统计日期 */
    private LocalDate statDate;
    /** 统计类型 */
    private String statType;
    /** 总能耗，kgce */
    private BigDecimal totalEnergyKgce;
    /** 能源成本，元 */
    private BigDecimal energyCost;
    /** 降本金额，元 */
    private BigDecimal costSaving;
    /** 碳减排，tCO2 */
    private BigDecimal carbonReduction;
    /** 产量，t */
    private BigDecimal productionOutput;
}
