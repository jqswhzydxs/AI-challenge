package com.xq.service;

import com.xq.common.result.Result;
import com.xq.model.vo.CorrelationVO;

/**
 * 数据分析服务接口.
 * <p>
 * 提供算法组预处理后的数据集分析结果查询，
 * 包括变量相关性矩阵等派生分析数据.
 * </p>
 *
 * @author XQ
 * @since 1.0.0
 */
public interface AnalysisService {

    /**
     * 查询 6 个能源变量的相关系数矩阵.
     * <p>
     * 数据来源：steel_data_workspace.mat / corr_mat，
     * 基于 2018 全年 35,040 个 15 分钟粒度样本计算.
     * </p>
     *
     * @return 相关性矩阵结果
     */
    Result<CorrelationVO> correlation();
}
