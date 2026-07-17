package com.xq.service;

import com.xq.common.result.Result;
import com.xq.model.dto.EnergyPlanGenerateDTO;
import com.xq.model.vo.EnergyPlanVO;
import com.xq.model.vo.TaskVO;

public interface EnergyPlanService {

    Result<TaskVO> generate(EnergyPlanGenerateDTO dto);

    Result<EnergyPlanVO> getPlanDetail(Long planId);
}
