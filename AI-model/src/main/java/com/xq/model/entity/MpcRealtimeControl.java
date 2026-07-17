package com.xq.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * MPC 实时调控结果实体.
 * <p>
 * 保存算法组每分钟更新一次的 realtime_control.json 数据.
 * </p>
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@TableName("mpc_realtime_control")
public class MpcRealtimeControl {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 对应算法任务 ID */
    private Long taskId;
    /** 控制日期 */
    private LocalDate controlDate;
    /** 控制时间，格式 HH:mm:ss */
    private LocalTime controlTime;
    /** 原始时间字符串 */
    private String rawTimestamp;
    /** 锅炉负荷指令，MW */
    private BigDecimal boilerLoadMw;
    /** 汽机出力指令，MW */
    private BigDecimal turbineOutputMw;
    /** 外购电力指令，kWh */
    private BigDecimal gridPurchaseKwh;
    /** 功率因数目标值 */
    private BigDecimal powerFactorTarget;
    /** 未来 5 分钟用电预测，kWh */
    private BigDecimal elecNext5minKwh;
    /** 未来 5 分钟蒸汽预测，吨 */
    private BigDecimal steamNext5minT;
    /** 来源文件名 */
    private String sourceFileName;
    /** 原始 JSON */
    private String rawJson;
    /** 入库时间 */
    private LocalDateTime createTime;
}
