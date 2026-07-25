package com.xq.web.controller;

import com.xq.common.result.PageResult;
import com.xq.common.result.Result;
import com.xq.model.dto.JointOptimizeCompareDTO;
import com.xq.model.dto.JointOptimizeDTO;
import com.xq.model.dto.PageQueryDTO;
import com.xq.model.vo.ConflictVO;
import com.xq.model.vo.JointOptimizeCompareVO;
import com.xq.model.vo.JointOptimizeEvaluationVO;
import com.xq.model.vo.JointOptimizeVO;
import com.xq.model.vo.JointParetoFrontierVO;
import com.xq.model.vo.TaskVO;
import com.xq.service.JointOptimizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 协同优化控制器.
 * <p>
 * 对应接口文档:
 * 4.9 创建协同优化任务,
 * 4.11 查询协同优化结果.
 * </p>
 *
 * @author XQ
 * @since 1.0.0
 */
@Tag(name = "协同优化", description = "生产-能源联合协同优化")
@RestController
@RequestMapping("/api/optimize")
@RequiredArgsConstructor
public class OptimizeController {

    private final JointOptimizationService optimizeService;

    @Operation(summary = "创建协同优化任务", description = "发起生产-能源联合协同优化任务，返回任务 ID")
    @PostMapping("/joint/generate")
    public Result<TaskVO> generate(@Valid @RequestBody JointOptimizeDTO dto) {
        return optimizeService.generate(dto);
    }

    @Operation(summary = "查询协同优化结果", description = "根据优化 ID 查询协同优化的完整结果，含约束冲突和时序数据")
    @GetMapping("/joint/{optimizeId}")
    public Result<JointOptimizeVO> getResult(
            @Parameter(description = "协同优化 ID", required = true, example = "1")
            @PathVariable("optimizeId") Long optimizeId) {
        return optimizeService.getResult(optimizeId);
    }

    @Operation(summary = "协同优化任务列表", description = "分页查询协同优化任务列表")
    @GetMapping("/tasks")
    public Result<PageResult<TaskVO>> listTasks(@ParameterObject PageQueryDTO query) {
        return optimizeService.listTasks(query);
    }

    @Operation(summary = "协同优化冲突列表", description = "按任务 ID 或优化方案 ID 查询约束冲突")
    @GetMapping("/conflicts")
    public Result<List<ConflictVO>> listConflicts(
            @Parameter(description = "任务 ID", example = "1") @RequestParam(value = "taskId", required = false) Long taskId,
            @Parameter(description = "协同优化方案 ID", example = "1") @RequestParam(value = "optimizeId", required = false) Long optimizeId) {
        return optimizeService.listConflicts(taskId, optimizeId);
    }

    @Operation(summary = "协同优化评价指标", description = "根据优化 ID 查询评价指标，含 MAPE、EC、ER、冲突数等")
    @GetMapping("/evaluation/{optimizeId}")
    public Result<JointOptimizeEvaluationVO> getEvaluation(
            @Parameter(description = "协同优化 ID", required = true, example = "1")
            @PathVariable("optimizeId") Long optimizeId) {
        return optimizeService.getEvaluation(optimizeId);
    }

    @Operation(summary = "协同优化方案对比", description = "传入多个协同优化方案 ID，返回核心指标对比")
    @PostMapping("/compare")
    public Result<JointOptimizeCompareVO> compare(@Valid @RequestBody JointOptimizeCompareDTO dto) {
        return optimizeService.compare(dto);
    }

    @Operation(summary = "协同优化帕累托前沿", description = "从历史协同优化方案派生降本率-降耗率帕累托散点")
    @GetMapping("/pareto-frontier")
    public Result<JointParetoFrontierVO> getParetoFrontier(@ParameterObject PageQueryDTO query) {
        return optimizeService.getParetoFrontier(query);
    }
}
