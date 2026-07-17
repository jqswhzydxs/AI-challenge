package com.xq.controller;

import com.xq.common.result.Result;
import com.xq.model.vo.DashboardVO;
import com.xq.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 首页仪表盘控制器.
 * <p>
 * 对应接口文档 4.2 首页 Dashboard.
 * </p>
 *
 * @author XQ
 * @since 1.0.0
 */
@Tag(name = "仪表盘", description = "首页数据概览")
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "首页概览", description = "获取首页仪表盘总览数据，包括今日节能、今日生产、任务统计和预警信息")
    @GetMapping("/overview")
    public Result<DashboardVO> overview() {
        return dashboardService.overview();
    }
}
