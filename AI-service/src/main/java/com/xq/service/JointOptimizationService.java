package com.xq.service;

import com.xq.common.result.PageResult;
import com.xq.common.result.Result;
import com.xq.model.dto.JointOptimizeCompareDTO;
import com.xq.model.dto.JointOptimizeDTO;
import com.xq.model.dto.PageQueryDTO;
import com.xq.model.vo.ConflictVO;
import com.xq.model.vo.JointOptimizeCompareVO;
import com.xq.model.vo.JointOptimizeEvaluationVO;
import com.xq.model.vo.JointOptimizeVO;
import com.xq.model.vo.JointParetoFrontierVO;
import com.xq.model.vo.TaskVO;

import java.util.List;

public interface JointOptimizationService {

    Result<TaskVO> generate(JointOptimizeDTO dto);

    Result<JointOptimizeVO> getResult(Long optimizeId);

    Result<PageResult<TaskVO>> listTasks(PageQueryDTO query);

    Result<List<ConflictVO>> listConflicts(Long taskId, Long optimizeId);

    Result<JointOptimizeEvaluationVO> getEvaluation(Long optimizeId);

    Result<JointOptimizeCompareVO> compare(JointOptimizeCompareDTO dto);

    Result<JointParetoFrontierVO> getParetoFrontier(PageQueryDTO query);
}
