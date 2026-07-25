package com.xq.model.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 能源负荷预测 VO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@Builder
public class EnergyLoadForecastVO {

    /** 负荷预测摘要 */
    private LoadForecastVO summary;

    /** 小时预测点 */
    private List<EnergyLoadForecastPointVO> points;
}
