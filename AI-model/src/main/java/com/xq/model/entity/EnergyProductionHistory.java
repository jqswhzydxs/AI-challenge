package com.xq.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产能源历史实体.
 * <p>
 * 用于融合生产数据和能源数据，当前数据集暂时不能完整填充此表.
 * </p>
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@TableName("energy_production_history")
public class EnergyProductionHistory {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 时间，建议按 15 分钟对齐 */
    private LocalDateTime timestamp;
    /** 轧钢产量，t（当前数据集缺失） */
    private BigDecimal steelOutput;
    /** 合格产品产量，t（当前数据集缺失） */
    private BigDecimal qualifiedOutput;
    /** 用电量，kWh */
    private BigDecimal electricityConsumption;
    /** 蒸汽用量，单位待确认 */
    private BigDecimal steamConsumption;
    /** 碳排放，tCO2 */
    private BigDecimal carbonEmissionTco2;
    /** 能源成本，元（需由电价和用能量派生） */
    private BigDecimal energyCost;
    /** 负荷类型 */
    private String loadType;
    /** 数据质量 */
    private String dataQuality;
}
