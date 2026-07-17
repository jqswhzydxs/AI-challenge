package com.xq.service.impl;

import com.xq.common.result.Result;
import com.xq.model.dto.PageQueryDTO;
import com.xq.model.vo.OptimizationEffectVO;
import com.xq.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 报表服务实现.
 * <p>
 * 联调阶段返回 Mock 数据.
 * </p>
 *
 * @author XQ
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    @Override
    public Result<OptimizationEffectVO> getOptimizationEffect(PageQueryDTO query) {
        // Mock 数据 - 联调阶段使用
        OptimizationEffectVO vo = OptimizationEffectVO.builder()
                .costSaving(new BigDecimal("125000.0"))
                .energyReductionRate(new BigDecimal("1.8"))
                .carbonReduction(new BigDecimal("3600.0"))
                .mape(new BigDecimal("2.1"))
                .ecBefore(new BigDecimal("42.0"))
                .ecAfter(new BigDecimal("41.2"))
                .er(new BigDecimal("96.8"))
                .build();
        return Result.ok(vo);
    }
}
