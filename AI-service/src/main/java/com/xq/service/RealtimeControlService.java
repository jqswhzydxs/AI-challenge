package com.xq.service;

import com.xq.common.result.PageResult;
import com.xq.common.result.Result;
import com.xq.model.dto.PageQueryDTO;
import com.xq.model.vo.RealtimeControlImportResultVO;
import com.xq.model.vo.RealtimeControlVO;

public interface RealtimeControlService {

    Result<RealtimeControlVO> getLatest();

    Result<PageResult<RealtimeControlVO>> getHistory(PageQueryDTO query);

    Result<RealtimeControlImportResultVO> importRealtimeControl(Object realtimeControlJson, String controlDate, String sourceFileName);
}
