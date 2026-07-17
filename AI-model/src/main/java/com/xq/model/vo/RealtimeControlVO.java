package com.xq.model.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * MPC 实时调控结果 VO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@Builder
public class RealtimeControlVO {

    /** 控制记录 ID */
    private Long controlId;

    /** 控制日期 */
    private String controlDate;

    /** 控制时间，格式 HH:mm:ss */
    private String timestamp;

    /** 锅炉负荷指令，MW */
    private BigDecimal boilerLoad;

    /** 汽机出力指令，MW */
    private BigDecimal turbineOutput;

    /** 外购电力指令，kWh */
    private BigDecimal gridPurchase;

    /** 功率因数目标值 */
    private BigDecimal powerFactorTarget;

    /** 未来 5 分钟用电预测，kWh */
    private BigDecimal elecNext5min;

    /** 未来 5 分钟蒸汽预测，t */
    private BigDecimal steamNext5min;

    /** 入库时间 */
    private String createTime;
}
