package com.xq.service.impl;

import com.xq.common.result.Result;
import com.xq.mapper.WarningRecordMapper;
import com.xq.model.entity.WarningRecord;
import com.xq.model.vo.DashboardVO;
import com.xq.model.vo.WarningItemVO;
import com.xq.service.DashboardService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final WarningRecordMapper warningRecordMapper;

    @Override
    public Result<DashboardVO> overview() {
        // Mock 数据 - 联调阶段使用
        List<WarningRecord> warnings = warningRecordMapper.selectList(
                new LambdaQueryWrapper<WarningRecord>()
                        .eq(WarningRecord::getHandled, 0)
                        .orderByDesc(WarningRecord::getWarningTime)
                        .last("limit 5")
        );

        List<WarningItemVO> warningItems = warnings.stream().map(w -> WarningItemVO.builder()
                .warningId(w.getId())
                .level(w.getLevel())
                .message(w.getMessage())
                .warningTime(w.getWarningTime())
                .build()).collect(Collectors.toList());

        DashboardVO vo = DashboardVO.builder()
                .totalEnergyKgceToday(new BigDecimal("13250.5"))
                .totalEnergyKgceMonth(new BigDecimal("351200.0"))
                .productionProgressRate(new BigDecimal("92.5"))
                .energyLoadRate(new BigDecimal("81.6"))
                .schemeExecuteRate(new BigDecimal("95.2"))
                .warningCount(warningItems.size())
                .latestWarnings(warningItems)
                .build();
        return Result.ok(vo);
    }
}
