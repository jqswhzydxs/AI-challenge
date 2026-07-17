package com.xq.controller;

import com.xq.common.result.PageResult;
import com.xq.common.result.Result;
import com.xq.model.dto.PageQueryDTO;
import com.xq.model.vo.RealtimeControlVO;
import com.xq.service.RealtimeControlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 实时调控控制器.
 * <p>
 * 对应接口文档:
 * 4.6.1 查询最新 MPC 实时调控结果,
 * 4.6.2 查询 MPC 实时调控历史.
 * </p>
 *
 * @author XQ
 * @since 1.0.0
 */
@Tag(name = "实时调控", description = "MPC 实时优化调控结果")
@RestController
@RequestMapping("/api/realtime-control")
@RequiredArgsConstructor
public class RealtimeControlController {

    private final RealtimeControlService realtimeControlService;

    @Operation(summary = "最新调控结果", description = "获取最近一次 MPC 实时调控的最新结果")
    @GetMapping("/latest")
    public Result<RealtimeControlVO> getLatest() {
        return realtimeControlService.getLatest();
    }

    @Operation(summary = "调控历史", description = "分页查询 MPC 实时调控历史记录")
    @GetMapping("/history")
    public Result<PageResult<RealtimeControlVO>> getHistory(PageQueryDTO query) {
        return realtimeControlService.getHistory(query);
    }
}
