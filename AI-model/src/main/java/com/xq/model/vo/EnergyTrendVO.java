package com.xq.model.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 能源综合趋势 VO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@Builder
public class EnergyTrendVO {

    /** 趋势点 */
    private List<EnergyTrendPointVO> points;
}
