package com.xq.service;

import com.xq.common.result.Result;
import com.xq.model.dto.PageQueryDTO;
import com.xq.model.vo.OptimizationEffectVO;

public interface ReportService {

    Result<OptimizationEffectVO> getOptimizationEffect(PageQueryDTO query);
}
