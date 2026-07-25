package com.xq.service;

import com.xq.common.result.Result;
import com.xq.model.dto.PageQueryDTO;
import com.xq.model.vo.EnergyCarbonReductionVO;
import com.xq.model.vo.EnergyTrendVO;
import com.xq.model.vo.OptimizationEffectVO;
import com.xq.model.vo.ReportEnergyAnalysisVO;

public interface ReportService {

    Result<OptimizationEffectVO> getOptimizationEffect(PageQueryDTO query);

    Result<ReportEnergyAnalysisVO> getEnergyAnalysis(PageQueryDTO query);

    Result<EnergyTrendVO> getEnergyTrend(PageQueryDTO query);

    Result<EnergyCarbonReductionVO> getCarbonReduction(PageQueryDTO query);

    byte[] export(String type, PageQueryDTO query);
}
