package com.xq.service.impl;

import com.xq.common.result.Result;
import com.xq.model.vo.CorrelationVO;
import com.xq.service.AnalysisService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;

/**
 * 数据分析服务实现.
 * <p>
 * 相关系数矩阵当前为静态数据，来源为算法组 steel_data_workspace.mat 的 corr_mat，
 * 后续可改为从数据库 evaluation_metric 表按业务日期动态计算.
 * </p>
 *
 * @author XQ
 * @since 1.0.0
 */
@Service
public class AnalysisServiceImpl implements AnalysisService {

    /** 变量英文标签，与 corr_mat 行列顺序一致 */
    private static final List<String> LABELS = Arrays.asList(
            "elec",                                      // 用电量
            "Lagging_Current_Reactive_Power_kVarh",      // 滞后无功电量
            "Leading_Current_Reactive_Power_kVarh",      // 超前无功电量
            "CO2_tCO2_",                                 // 碳排放
            "Lagging_Current_Power_Factor",              // 滞后功率因数
            "Leading_Current_Power_Factor"               // 超前功率因数
    );

    /** 变量中文名 */
    private static final List<String> LABELS_CN = Arrays.asList(
            "用电量",
            "滞后无功电量",
            "超前无功电量",
            "碳排放",
            "滞后功率因数",
            "超前功率因数"
    );

    /** 变量单位 */
    private static final List<String> UNITS = Arrays.asList(
            "kWh",
            "kVarh",
            "kVarh",
            "tCO2",
            "%",
            "%"
    );

    /**
     * 6×6 相关系数矩阵，来自算法组 steel_data_workspace.mat 的 corr_mat.
     * <p>
     * 矩阵为对称矩阵，对角线为 1.0.
     * 计算基于 2018 全年 35,040 个 15 分钟粒度样本.
     * </p>
     */
    private static final double[][] CORR_MAT = {
        // elec     L.kVarh   Ld.kVarh  CO2       L.PF      Ld.PF
        { 1.000000,  0.896150, -0.324922, 0.988180,  0.385960,  0.353566 },
        { 0.896150,  1.000000, -0.405142, 0.886948,  0.144534,  0.407716 },
        {-0.324922, -0.405142,  1.000000, -0.332777,  0.526770, -0.944039 },
        { 0.988180,  0.886948, -0.332777, 1.000000,  0.379605,  0.360019 },
        { 0.385960,  0.144534,  0.526770, 0.379605,  1.000000, -0.519967 },
        { 0.353566,  0.407716, -0.944039, 0.360019, -0.519967,  1.000000 }
    };

    @Override
    public Result<CorrelationVO> correlation() {
        List<List<BigDecimal>> matrix = Arrays.stream(CORR_MAT)
                .map(row -> Arrays.stream(row)
                        .mapToObj(v -> BigDecimal.valueOf(v).setScale(6, RoundingMode.HALF_UP))
                        .toList())
                .toList();

        CorrelationVO vo = CorrelationVO.builder()
                .labels(LABELS)
                .labelsCn(LABELS_CN)
                .units(UNITS)
                .matrix(matrix)
                .source("steel_data_workspace.mat / corr_mat（算法组提供）")
                .sampleCount(35040)
                .timeRange("2018-01-01 00:15 至 2018-12-31 23:45（15 分钟粒度）")
                .build();

        return Result.ok(vo);
    }
}
