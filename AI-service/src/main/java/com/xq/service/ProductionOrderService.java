package com.xq.service;

import com.xq.common.result.PageResult;
import com.xq.common.result.Result;
import com.xq.model.dto.PageQueryDTO;
import com.xq.model.vo.ProductionOrderVO;

public interface ProductionOrderService {

    Result<PageResult<ProductionOrderVO>> listOrders(PageQueryDTO query);
}
