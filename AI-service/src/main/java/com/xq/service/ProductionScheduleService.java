package com.xq.service;

import com.xq.common.result.PageResult;
import com.xq.common.result.Result;
import com.xq.model.dto.ScheduleGenerateDTO;
import com.xq.model.vo.ImportPlanResultVO;
import com.xq.model.vo.SchedulePlanVO;
import com.xq.model.vo.TaskVO;

import java.util.Map;

public interface ProductionScheduleService {

    Result<TaskVO> generate(ScheduleGenerateDTO dto);

    Result<SchedulePlanVO> getPlanDetail(Long scheduleId);

    Result<ImportPlanResultVO> importDailyPlan(Map<String, Object> dailyPlanJson);

    Result<PageResult<SchedulePlanVO>> listHistory(int page, int size);
}
