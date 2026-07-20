package com.xq.web.controller;

import com.xq.common.result.Result;
import com.xq.model.dto.EnergyPlanGenerateDTO;
import com.xq.model.dto.PageQueryDTO;
import com.xq.model.vo.EnergyPlanVO;
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

    @Operation(summary = "生成能源运行方案", description = "根据能源参数发起异步方案生成任务，返回任务 ID")
    @PostMapping("/plan/generate")
    public Result<TaskVO> generatePlan(@Valid @RequestBody EnergyPlanGenerateDTO dto) {
        return energyPlanService.generate(dto);
    }

    @Operation(summary = "查询能源运行方案详情", description = "根据方案 ID 查询能源运行方案的完整结果")
    @GetMapping("/plans/{planId}")
    public Result<EnergyPlanVO> getPlan(
            @Parameter(description = "能源方案 ID", required = true, example = "1")
            @PathVariable("planId") Long planId) {
        return energyPlanService.getPlanDetail(planId);
    }
}
