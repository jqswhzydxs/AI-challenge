package com.xq.service;

import com.xq.common.result.PageResult;
import com.xq.common.result.Result;
import com.xq.model.dto.ScheduleCompareDTO;
import com.xq.model.dto.ScheduleGenerateDTO;
import com.xq.model.vo.ImportPlanResultVO;
import com.xq.model.vo.ScheduleCompareVO;
import com.xq.model.vo.SchedulePlanVO;
import com.xq.model.vo.TaskVO;

import java.util.Map;

public interface ProductionScheduleService {

    Result<TaskVO> generate(ScheduleGenerateDTO dto);

    Result<TaskVO> generateFromRawData(byte[] fileBytes, String originalFilename);

    Result<SchedulePlanVO> getPlanDetail(Long scheduleId);

    Result<SchedulePlanVO> getPlanByDate(String scheduleDate);

    Result<ImportPlanResultVO> importDailyPlan(Map<String, Object> dailyPlanJson);

    Result<PageResult<SchedulePlanVO>> listHistory(int page, int size);

    Result<ScheduleCompareVO> compare(ScheduleCompareDTO dto);
}
