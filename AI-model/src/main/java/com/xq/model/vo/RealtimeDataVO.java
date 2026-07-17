package com.xq.model.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 实时能源数据响应 VO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@Builder
public class RealtimeDataVO {

    /** 时间粒度，min */
    private Integer timeInterval;

    /** 数据点列表 */
    private List<RealtimeDataPointVO> points;
}
