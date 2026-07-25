package com.xq.model.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 协同优化帕累托前沿 VO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@Builder
public class JointParetoFrontierVO {

    /** X 轴字段 */
    private String xAxis;

    /** Y 轴字段 */
    private String yAxis;

    /** 颜色/大小辅助字段 */
    private String metric;

    /** 散点列表 */
    private List<JointParetoPointVO> points;

    /** 帕累托前沿点 */
    private List<JointParetoPointVO> frontier;
}
