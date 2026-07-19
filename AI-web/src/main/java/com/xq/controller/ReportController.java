package com.xq.controller;

import com.xq.common.result.Result;
import com.xq.model.dto.PageQueryDTO;
import com.xq.model.vo.OptimizationEffectVO;
import com.xq.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.*;

/**
 * 报表控制器.
 * <p>
 * 对应接口文档 4.12 数据报表 - 优化效果.
 * </p>
 *
 * @author XQ
 * @since 1.0.0
 */
@Tag(name = "数据报表", description = "优化效果统计与报表")
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @Operation(summary = "优化效果报表", description = "按日期范围查询优化前后各项指标的对比数据")
    @GetMapping("/optimization-effect")
    public Result<OptimizationEffectVO> getOptimizationEffect(@ParameterObject PageQueryDTO query) {
        return reportService.getOptimizationEffect(query);
    }
}
