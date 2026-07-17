package com.xq.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xq.common.result.PageResult;
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
        LambdaQueryWrapper<EnergyRealtimeData> wrapper = new LambdaQueryWrapper<>();
        if (query.getStartTime() != null) {
            wrapper.ge(EnergyRealtimeData::getTimestamp, query.getStartTime());
        }
        if (query.getEndTime() != null) {
            wrapper.le(EnergyRealtimeData::getTimestamp, query.getEndTime());
        }
        wrapper.orderByAsc(EnergyRealtimeData::getTimestamp);

        List<EnergyRealtimeData> list = energyRealtimeDataMapper.selectList(wrapper);
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
}
