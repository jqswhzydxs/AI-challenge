package com.xq.web.controller;

import com.xq.common.result.Result;
import com.xq.model.dto.PageQueryDTO;
import com.xq.model.vo.EnergyCarbonReductionVO;
import com.xq.model.vo.EnergyTrendVO;
import com.xq.model.vo.OptimizationEffectVO;
import com.xq.model.vo.ReportEnergyAnalysisVO;
import com.xq.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    @Operation(summary = "能源分析报表", description = "按日期范围汇总综合能耗、能源成本、降本、碳减排和评价指标")
    @GetMapping("/energy-analysis")
    public Result<ReportEnergyAnalysisVO> getEnergyAnalysis(@ParameterObject PageQueryDTO query) {
        return reportService.getEnergyAnalysis(query);
    }

    @Operation(summary = "能源趋势报表", description = "按日期返回能耗、成本、降本、碳减排和产量趋势")
    @GetMapping("/energy-trend")
    public Result<EnergyTrendVO> getEnergyTrend(@ParameterObject PageQueryDTO query) {
        return reportService.getEnergyTrend(query);
    }

    @Operation(summary = "碳减排报表", description = "按日期返回当日和累计碳减排趋势")
    @GetMapping("/carbon-reduction")
    public Result<EnergyCarbonReductionVO> getCarbonReduction(@ParameterObject PageQueryDTO query) {
        return reportService.getCarbonReduction(query);
    }

    @Operation(summary = "导出报表", description = "导出 CSV 报表，type 支持 optimization-effect / energy-analysis / energy-trend / carbon-reduction")
    @GetMapping(value = "/export/{type}", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> export(
            @PathVariable("type") String type,
            @ParameterObject PageQueryDTO query) {
        byte[] content = reportService.export(type, query);
        String fileName = "report-" + type + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(new MediaType("text", "csv"))
                .body(content);
    }
}
