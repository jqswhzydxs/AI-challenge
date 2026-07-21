package com.xq.web.controller;

import com.xq.common.result.Result;
import com.xq.model.vo.CorrelationVO;
import com.xq.service.AnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 数据分析控制器.
 * <p>
 * 提供算法组预处理数据的分析结果查询接口，
 * 包括变量相关性矩阵等派生分析数据.
 * </p>
 *
 * @author XQ
 * @since 1.0.0
 */
@Tag(name = "数据分析", description = "能源变量相关性分析等派生分析接口")
@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;

    @Operation(summary = "变量相关性矩阵",
            description = "获取 6 个能源变量（用电量、滞/超前无功电量、碳排放、滞/超前功率因数）的 Pearson 相关系数矩阵，"
                    + "基于 2018 全年 35,040 个 15 分钟样本。"
                    + "返回 label 顺序与矩阵行列顺序一致。"
                    + "前端可用于渲染相关性热力图（正相关→暖色，负相关→冷色，0→白色，对角线=1.0 最深）。")
    @GetMapping("/correlation")
    public Result<CorrelationVO> correlation() {
        return analysisService.correlation();
    }
}
