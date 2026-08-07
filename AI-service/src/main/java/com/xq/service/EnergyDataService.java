package com.xq.service;

import com.xq.common.result.Result;
import com.xq.model.dto.EnergyRealtimePushDTO;
import com.xq.model.dto.PageQueryDTO;
import com.xq.model.vo.EnergyRealtimePushResultVO;
import com.xq.model.vo.RealtimeDataVO;

public interface EnergyDataService {

    Result<RealtimeDataVO> getRealtime(PageQueryDTO query);

    Result<EnergyRealtimePushResultVO> pushRealtime(EnergyRealtimePushDTO dto);

    Result<EnergyRealtimePushResultVO> pushMockRealtime();

    int backfillMockRealtimeIfNeeded(int days);
}
