package com.xq.web.controller;

import com.xq.common.result.PageResult;
import com.xq.common.result.Result;
import com.xq.model.dto.PageQueryDTO;
import com.xq.model.dto.ScheduleGenerateDTO;
import com.xq.model.vo.ImportPlanResultVO;
import com.xq.model.vo.ProductionOrderVO;
import com.xq.model.vo.SchedulePlanVO;
import com.xq.model.vo.TaskVO;
import com.xq.service.ProductionOrderService;
import com.xq.service.ProductionScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 生产管理控制器.
 * <p>
 * 对应接口文档:
 * 4.3 生产订单列表,
 * 4.4 生成生产排产方案,
 * 4.5 查询排产方案详情,
 * 4.5.1 导入日级排产 JSON.
 * </p>
 *
 * @author XQ
 * @since 1.0.0
 */
@Tag(name = "生产管理", description = "生产订单、排产方案管理")
@RestController
@RequestMapping("/api/production")
@RequiredArgsConstructor
public class ProductionController {

    private final ProductionOrderService orderService;
    private final ProductionScheduleService scheduleService;

    @Operation(summary = "生产订单列表", description = "分页查询生产订单，支持按日期、状态筛选")
    @GetMapping("/orders")
    public Result<PageResult<ProductionOrderVO>> listOrders(@ParameterObject PageQueryDTO query) {
        return orderService.listOrders(query);
    }

    @Operation(summary = "生成排产方案", description = "根据排产参数发起异步排产任务，返回任务 ID 供后续查询")
    @PostMapping("/schedule/generate")
    public Result<TaskVO> generateSchedule(@Valid @RequestBody ScheduleGenerateDTO dto) {
        return scheduleService.generate(dto);
    }

    @Operation(summary = "查询排产方案详情", description = "根据方案 ID 查询排产方案的完整结果")
    @GetMapping("/schedules/{scheduleId}")
    public Result<SchedulePlanVO> getSchedule(
            @Parameter(description = "排产方案 ID", required = true, example = "1")
            @PathVariable("scheduleId") Long scheduleId) {
        return scheduleService.getPlanDetail(scheduleId);
    }

    @Operation(summary = "排产方案历史列表", description = "分页查询所有排产方案，按创建时间倒序")
    @GetMapping("/schedule/history")
    public Result<PageResult<SchedulePlanVO>> listScheduleHistory(
            @Parameter(description = "页码", example = "1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数", example = "10") @RequestParam(defaultValue = "10") int size) {
        return scheduleService.listHistory(page, size);
    }

    @Operation(summary = "导入日级排产 JSON", description = "联调用：导入算法端返回的日级排产 JSON，解析后保存到数据库")
    @PostMapping("/schedules/import-daily-plan")
    public Result<ImportPlanResultVO> importDailyPlan(@RequestBody Map<String, Object> dailyPlanJson) {
        return scheduleService.importDailyPlan(dailyPlanJson);
    }
}
