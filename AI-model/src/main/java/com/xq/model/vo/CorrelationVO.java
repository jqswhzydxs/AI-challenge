package com.xq.model.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 变量相关系数矩阵 VO.
 * <p>
 * 返回算法组 steel_data_workspace.mat 中 corr_mat 的分析结果，
 * 用于前端展示 6 个能源变量间的相关性热力图或矩阵表.
 * </p>
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@Builder
public class CorrelationVO {

    /** 变量标签列表（英文），顺序与矩阵行列一致 */
    private List<String> labels;

    /** 变量中文名列表 */
    private List<String> labelsCn;

    /** 变量单位列表 */
    private List<String> units;

    /** 相关系数矩阵，大小 labels × labels，index [row][col] */
    private List<List<BigDecimal>> matrix;

    /** 数据来源说明 */
    private String source;

    /** 数据样本数量 */
    private Integer sampleCount;

    /** 数据时间范围 */
    private String timeRange;
}
