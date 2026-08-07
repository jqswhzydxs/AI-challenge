package com.xq.web.controller;

import com.xq.common.result.PageResult;
import com.xq.common.result.Result;
import com.xq.model.dto.EnergyRealtimePushDTO;
import com.xq.model.dto.EnergyPlanGenerateDTO;
import com.xq.model.dto.PageQueryDTO;
import com.xq.model.vo.EnergyAnalysisVO;
import com.xq.model.vo.EnergyCarbonReductionVO;
import com.xq.model.vo.EnergyConsumptionTrendVO;
import com.xq.model.vo.EnergyDeviceStatusVO;
import com.xq.model.vo.EnergyLoadForecastVO;
import com.xq.model.vo.EnergyPlanVO;
import com.xq.model.vo.EnergyRealtimePushResultVO;
import com.xq.model.vo.EnergyTrendVO;
import com.xq.model.vo.RealtimeDataVO;
import com.xq.model.vo.TaskVO;
import com.xq.service.EnergyDataService;
import com.xq.service.EnergyPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 能源管理控制器.
 * <p>
 * 对应接口文档:
 * 4.6 实时能源数据,
 * 4.7 生成能源运行方案,
 * 4.8 查询能源运行方案详情.
 * </p>
 *
 * @author XQ
 * @since 1.0.0
 */
@Tag(name = "能源管理", description = "实时能源数据、能源运行方案")
@RestController
@RequestMapping("/api/energy")
@RequiredArgsConstructor
public class EnergyController {

    private final EnergyDataService energyDataService;
    private final EnergyPlanService energyPlanService;

    @Operation(summary = "实时能源数据", description = "按时间范围和采样间隔查询实时能源数据（电、煤气、蒸汽等）")
    @GetMapping("/realtime")
    public Result<RealtimeDataVO> getRealtime(@ParameterObject PageQueryDTO query) {
        return energyDataService.getRealtime(query);
    }

    @Operation(summary = "推送能源实时数据", description = "模拟采集装置或外部采集程序按分钟推送电、蒸汽等实时数据并入库")
    @PostMapping("/realtime/push")
    public Result<EnergyRealtimePushResultVO> pushRealtime(@RequestBody EnergyRealtimePushDTO dto) {
        return energyDataService.pushRealtime(dto);
    }

    @Operation(summary = "生成一条模拟能源数据", description = "联调用：按当前分钟生成一条模拟采集点位并入库")
    @PostMapping("/realtime/mock")
    public Result<EnergyRealtimePushResultVO> pushMockRealtime() {
        return energyDataService.pushMockRealtime();
    }

    @Operation(summary = "生成能源运行方案", description = "根据能源参数发起异步方案生成任务，返回任务 ID")
    @PostMapping("/plan/generate")
    public Result<TaskVO> generatePlan(@Valid @RequestBody EnergyPlanGenerateDTO dto) {
        return energyPlanService.generate(dto);
    }

    @Operation(summary = "查询能源运行方案详情", description = "根据方案日期查询当天最新能源运行方案的完整结果")
    @GetMapping("/plan/{planDate}")
    public Result<EnergyPlanVO> getPlan(
            @Parameter(description = "方案日期，格式 yyyy-MM-dd", required = true, example = "2026-07-21")
            @PathVariable("planDate") String planDate) {
        return energyPlanService.getPlanDetail(planDate);
    }

    @Operation(summary = "能源方案历史", description = "分页查询能源运行方案历史列表")
    @GetMapping("/plans/history")
    public Result<PageResult<EnergyPlanVO>> listPlanHistory(@ParameterObject PageQueryDTO query) {
        return energyPlanService.listHistory(query);
    }

    @Operation(summary = "能源设备状态", description = "查询能源设备运行状态和最近负荷")
    @GetMapping("/device-status")
    public Result<List<EnergyDeviceStatusVO>> getDeviceStatus() {
        return energyPlanService.getDeviceStatus();
    }

    @Operation(summary = "负荷预测", description = "按日期查询能源方案的小时负荷预测")
    @GetMapping("/load-forecast")
    public Result<EnergyLoadForecastVO> getLoadForecast(
            @Parameter(description = "方案日期，格式 yyyy-MM-dd；不传则取最新能源方案")
            @RequestParam(value = "planDate", required = false) String planDate) {
        return energyPlanService.getLoadForecast(planDate);
    }

    @Operation(summary = "能耗趋势", description = "按小时或按日聚合实时能源数据，返回电、蒸汽、碳排趋势")
    @GetMapping("/consumption-trend")
    public Result<EnergyConsumptionTrendVO> getConsumptionTrend(@ParameterObject PageQueryDTO query) {
        return energyPlanService.getConsumptionTrend(query);
    }

    @Operation(summary = "能源分析", description = "基于实时能源数据和最近评价指标返回能源分析摘要")
    @GetMapping("/analysis")
    public Result<EnergyAnalysisVO> getAnalysis(@ParameterObject PageQueryDTO query) {
        return energyPlanService.getAnalysis(query);
    }

    @Operation(summary = "能源综合趋势", description = "从报表统计表返回能耗、成本、降本、碳减排趋势")
    @GetMapping("/trend")
    public Result<EnergyTrendVO> getTrend(@ParameterObject PageQueryDTO query) {
        return energyPlanService.getTrend(query);
    }

    @Operation(summary = "碳减排数据", description = "从报表统计表返回碳减排和累计碳减排趋势")
    @GetMapping("/carbon-reduction")
    public Result<EnergyCarbonReductionVO> getCarbonReduction(@ParameterObject PageQueryDTO query) {
        return energyPlanService.getCarbonReduction(query);
    }
}
