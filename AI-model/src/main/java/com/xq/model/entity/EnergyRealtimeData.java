package com.xq.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 能源实时数据实体.
 * <p>
 * 用于前端实时曲线和算法历史数据输入，数据量较大，后期可按月分表.
 * 数据粒度为 15 分钟/点，总记录数约 35040.
 * </p>
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@TableName("energy_realtime_data")
public class EnergyRealtimeData {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 清洗后的采集时间 */
    private LocalDateTime timestamp;
    /** 原始时间文本 */
    private String rawTimestamp;
    /** 15 分钟用电量，kWh（源字段 elec） */
    private BigDecimal electricityConsumption;
    /** 蒸汽用量，单位待确认（源字段 steam） */
    private BigDecimal steamConsumption;
    /** 碳排放，tCO2 */
    private BigDecimal carbonEmissionTco2;
    /** 滞后无功电量，kVarh */
    private BigDecimal laggingReactivePowerKvarh;
    /** 超前无功电量，kVarh */
    private BigDecimal leadingReactivePowerKvarh;
    /** 滞后功率因数，% */
    private BigDecimal laggingPowerFactor;
    /** 超前功率因数，% */
    private BigDecimal leadingPowerFactor;
    /** 日内秒数 */
    private Integer nsm;
    /** 工作日/周末 */
    private String weekStatus;
    /** 星期 */
    private String dayOfWeek;
    /** 负荷类型 */
    private String loadType;
    /** 数据质量：NORMAL / MISSING / ABNORMAL */
    private String dataQuality;
    /** 数据来源，默认 steel-industry-energy-dataset */
    private String source;
}
