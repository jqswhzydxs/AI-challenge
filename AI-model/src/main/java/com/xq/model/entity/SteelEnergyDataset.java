package com.xq.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 钢铁能源样本数据实体.
 * <p>
 * 完整保存 steel-industry-energy-dataset 原始数据集，
 * 用于导入、清洗、算法训练.
 * </p>
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@TableName("steel_energy_dataset")
public class SteelEnergyDataset {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 清洗后的时间 */
    private LocalDateTime timestamp;
    /** 原始时间文本 */
    private String rawTimestamp;
    /** 用电量，kWh */
    private BigDecimal electricityConsumption;
    /** 滞后无功电量 */
    private BigDecimal laggingReactivePowerKvarh;
    /** 超前无功电量 */
    private BigDecimal leadingReactivePowerKvarh;
    /** 碳排放，tCO2 */
    private BigDecimal carbonEmissionTco2;
    /** 滞后功率因数 */
    private BigDecimal laggingPowerFactor;
    /** 超前功率因数 */
    private BigDecimal leadingPowerFactor;
    /** 日内秒数 */
    private Integer nsm;
    /** 工作日/周末 */
    private String weekStatus;
    /** 星期 */
    private String dayOfWeek;
    /** 负荷类型 */
    private String loadType;
    /** 蒸汽用量，单位待确认 */
    private BigDecimal steamConsumption;
    /** 数据质量 */
    private String dataQuality;
}
