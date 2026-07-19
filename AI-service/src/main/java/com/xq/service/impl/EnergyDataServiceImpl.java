package com.xq.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xq.common.result.Result;
import com.xq.mapper.EnergyRealtimeDataMapper;
import com.xq.model.dto.PageQueryDTO;
import com.xq.model.entity.EnergyRealtimeData;
import com.xq.model.vo.RealtimeDataPointVO;
import com.xq.model.vo.RealtimeDataVO;
import com.xq.service.EnergyDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EnergyDataServiceImpl implements EnergyDataService {

    private final EnergyRealtimeDataMapper energyRealtimeDataMapper;

    @Override
    public Result<RealtimeDataVO> getRealtime(PageQueryDTO query) {
        if (query == null) {
            query = new PageQueryDTO();
        }
        int pageNum = query.getPageNum() != null && query.getPageNum() > 0 ? query.getPageNum() : 1;
        int pageSize = query.getPageSize() != null && query.getPageSize() > 0 ? query.getPageSize() : 50;
        String startTime = firstText(query.getStartTime(), query.getStartDate());
        String endTime = firstText(query.getEndTime(), query.getEndDate());

        LambdaQueryWrapper<EnergyRealtimeData> wrapper = new LambdaQueryWrapper<>();
        if (hasText(startTime)) {
            wrapper.ge(EnergyRealtimeData::getTimestamp, startTime);
        }
        if (hasText(endTime)) {
            wrapper.le(EnergyRealtimeData::getTimestamp, endTime);
        }
        wrapper.orderByAsc(EnergyRealtimeData::getTimestamp);

        Page<EnergyRealtimeData> page = energyRealtimeDataMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<EnergyRealtimeData> list = page.getRecords();
        List<RealtimeDataPointVO> points = list.stream().map(d -> RealtimeDataPointVO.builder()
                .timestamp(d.getTimestamp() != null ? d.getTimestamp().toString() : null)
                .electricityConsumption(d.getElectricityConsumption())
                .steamConsumption(d.getSteamConsumption())
                .carbonEmissionTco2(d.getCarbonEmissionTco2())
                .laggingReactivePowerKvarh(d.getLaggingReactivePowerKvarh())
                .leadingReactivePowerKvarh(d.getLeadingReactivePowerKvarh())
                .laggingPowerFactor(d.getLaggingPowerFactor())
                .leadingPowerFactor(d.getLeadingPowerFactor())
                .nsm(d.getNsm())
                .weekStatus(d.getWeekStatus())
                .dayOfWeek(d.getDayOfWeek())
                .loadType(d.getLoadType())
                .build()).collect(Collectors.toList());

        RealtimeDataVO vo = RealtimeDataVO.builder()
                .timeInterval(query.getInterval() != null ? query.getInterval() : 15)
                .points(points)
                .build();
        return Result.ok(vo);
    }

    private String firstText(String first, String second) {
        if (hasText(first)) {
            return first.trim();
        }
        if (hasText(second)) {
            return second.trim();
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
