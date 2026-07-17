package com.xq.service;

import com.xq.common.result.Result;
import com.xq.model.dto.JointOptimizeDTO;
import com.xq.model.vo.JointOptimizeVO;
import com.xq.model.vo.TaskVO;

public interface JointOptimizationService {

    Result<TaskVO> generate(JointOptimizeDTO dto);

    Result<JointOptimizeVO> getResult(Long optimizeId);
}
