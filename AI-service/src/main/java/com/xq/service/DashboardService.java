package com.xq.service;

import com.xq.common.result.Result;
import com.xq.model.vo.DashboardVO;

public interface DashboardService {

    Result<DashboardVO> overview();
}
