package com.xq.service;

import com.xq.common.result.PageResult;
import com.xq.common.result.Result;
import com.xq.model.dto.EnergyPlanGenerateDTO;
import com.xq.model.dto.PageQueryDTO;
import com.xq.model.vo.EnergyAnalysisVO;
import com.xq.model.vo.EnergyCarbonReductionVO;
import com.xq.model.vo.EnergyConsumptionTrendVO;
import com.xq.model.vo.EnergyDeviceStatusVO;
import com.xq.model.vo.EnergyLoadForecastVO;
import com.xq.model.vo.EnergyPlanVO;
import com.xq.model.vo.EnergyTrendVO;
import com.xq.model.vo.TaskVO;

import java.util.List;

public interface EnergyPlanService {

    Result<TaskVO> generate(EnergyPlanGenerateDTO dto);

    Result<EnergyPlanVO> getPlanDetail(String planDate);

    Result<PageResult<EnergyPlanVO>> listHistory(PageQueryDTO query);

    Result<List<EnergyDeviceStatusVO>> getDeviceStatus();

    Result<EnergyLoadForecastVO> getLoadForecast(String planDate);

    Result<EnergyConsumptionTrendVO> getConsumptionTrend(PageQueryDTO query);

    Result<EnergyAnalysisVO> getAnalysis(PageQueryDTO query);

    Result<EnergyTrendVO> getTrend(PageQueryDTO query);

    Result<EnergyCarbonReductionVO> getCarbonReduction(PageQueryDTO query);
}
