package com.xq.web.controller;

import com.xq.common.result.Result;
import com.xq.model.dto.JointOptimizeDTO;
import com.xq.model.vo.JointOptimizeVO;
import com.xq.model.vo.TaskVO;
import com.xq.service.JointOptimizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
}
