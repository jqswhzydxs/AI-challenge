package com.xq.web.controller;

import com.xq.common.result.Result;
import com.xq.model.vo.TaskVO;
import com.xq.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 任务控制器.
 * <p>
 * 对应接口文档 4.10 查询任务状态.
 * </p>
 *
 * @author XQ
 * @since 1.0.0
 */
@Tag(name = "任务", description = "异步任务状态查询")
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @Operation(summary = "查询任务状态", description = "根据任务 ID 查询异步任务状态，返回 PENDING / RUNNING / SUCCESS / FAILED")
    @GetMapping("/{taskId}")
    public Result<TaskVO> getTask(
            @Parameter(description = "任务 ID", required = true, example = "1")
            @PathVariable("taskId") Long taskId) {
        return taskService.getTask(taskId);
    }

}
