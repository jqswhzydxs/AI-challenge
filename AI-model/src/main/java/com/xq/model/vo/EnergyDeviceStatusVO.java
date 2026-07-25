package com.xq.model.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 能源设备状态 VO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@Builder
public class EnergyDeviceStatusVO {

    /** 设备 ID */
    private Long equipmentId;

    /** 设备编码 */
    private String equipmentCode;

    /** 设备名称 */
    private String equipmentName;

    /** 设备类型 */
    private String equipmentType;

    /** 运行状态 */
    private String status;

    /** 当前输出 */
    private BigDecimal currentOutput;

    /** 最大输出 */
    private BigDecimal maxOutput;

    /** 负荷率，% */
    private BigDecimal loadRate;

    /** 效率，% */
    private BigDecimal efficiency;

    /** 告警等级 */
    private String warningLevel;
}
