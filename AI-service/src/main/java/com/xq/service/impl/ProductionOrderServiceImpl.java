package com.xq.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xq.common.result.PageResult;
import com.xq.common.result.Result;
import com.xq.mapper.ProductionOrderMapper;
import com.xq.model.dto.PageQueryDTO;
import com.xq.model.entity.ProductionOrder;
import com.xq.model.vo.ProductionOrderVO;
import com.xq.service.ProductionOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductionOrderServiceImpl implements ProductionOrderService {

    private final ProductionOrderMapper productionOrderMapper;

    @Override
    public Result<PageResult<ProductionOrderVO>> listOrders(PageQueryDTO query) {
        LambdaQueryWrapper<ProductionOrder> wrapper = new LambdaQueryWrapper<>();
        if (query.getDate() != null) {
            wrapper.apply("DATE_FORMAT(due_time, '%Y-%m-%d') = {0}", query.getDate());
        }
        if (query.getStatus() != null) {
            wrapper.eq(ProductionOrder::getStatus, query.getStatus());
        }
        wrapper.orderByAsc(ProductionOrder::getPriority)
               .orderByAsc(ProductionOrder::getDueTime);

        Page<ProductionOrder> page = new Page<>(query.getPageNum(), query.getPageSize());
        Page<ProductionOrder> result = productionOrderMapper.selectPage(page, wrapper);

        List<ProductionOrderVO> records = result.getRecords().stream().map(o -> ProductionOrderVO.builder()
                .orderId(o.getId())
                .orderNo(o.getOrderNo())
                .productName(o.getProductName())
                .plannedQuantity(o.getPlannedQuantity())
                .unit(o.getUnit())
                .dueTime(o.getDueTime())
                .priority(o.getPriority())
                .status(o.getStatus())
                .build()).collect(Collectors.toList());

        return Result.ok(PageResult.of(result.getTotal(), query.getPageNum(), query.getPageSize(), records));
    }
}
