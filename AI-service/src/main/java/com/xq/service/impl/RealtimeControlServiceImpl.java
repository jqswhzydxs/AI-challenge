package com.xq.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xq.common.exception.BusinessException;
import com.xq.common.result.PageResult;
import com.xq.common.result.Result;
import com.xq.mapper.MpcRealtimeControlMapper;
import com.xq.model.dto.PageQueryDTO;
import com.xq.model.entity.MpcRealtimeControl;
import com.xq.model.vo.RealtimeControlVO;
import com.xq.service.RealtimeControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 实时调控服务实现.
 *
 * @author XQ
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class RealtimeControlServiceImpl implements RealtimeControlService {

    private final MpcRealtimeControlMapper mpcRealtimeControlMapper;

    @Override
    public Result<RealtimeControlVO> getLatest() {
        MpcRealtimeControl latest = mpcRealtimeControlMapper.selectOne(
                new LambdaQueryWrapper<MpcRealtimeControl>()
                        .orderByDesc(MpcRealtimeControl::getCreateTime)
                        .last("limit 1")
        );
        if (latest == null) {
            throw new BusinessException(404, "暂无实时调控数据");
        }

        RealtimeControlVO vo = RealtimeControlVO.builder()
                .controlId(latest.getId())
                .controlDate(latest.getControlDate() != null ? latest.getControlDate().toString() : null)
                .timestamp(latest.getControlTime() != null ? latest.getControlTime().toString() : null)
                .boilerLoad(latest.getBoilerLoadMw())
                .turbineOutput(latest.getTurbineOutputMw())
                .gridPurchase(latest.getGridPurchaseKwh())
                .powerFactorTarget(latest.getPowerFactorTarget())
                .elecNext5min(latest.getElecNext5minKwh())
                .steamNext5min(latest.getSteamNext5minT())
                .createTime(latest.getCreateTime() != null ? latest.getCreateTime().toString() : null)
                .build();
        return Result.ok(vo);
    }

    @Override
    public Result<PageResult<RealtimeControlVO>> getHistory(PageQueryDTO query) {
        LambdaQueryWrapper<MpcRealtimeControl> wrapper = new LambdaQueryWrapper<>();
        if (query.getStartTime() != null) {
            wrapper.ge(MpcRealtimeControl::getControlTime, query.getStartTime());
        }
        if (query.getEndTime() != null) {
            wrapper.le(MpcRealtimeControl::getControlTime, query.getEndTime());
        }
        wrapper.orderByDesc(MpcRealtimeControl::getCreateTime);

        Page<MpcRealtimeControl> page = new Page<>(query.getPageNum(), query.getPageSize());
        Page<MpcRealtimeControl> result = mpcRealtimeControlMapper.selectPage(page, wrapper);

        List<RealtimeControlVO> records = result.getRecords().stream().map(r -> RealtimeControlVO.builder()
                .controlId(r.getId())
                .controlDate(r.getControlDate() != null ? r.getControlDate().toString() : null)
                .timestamp(r.getControlTime() != null ? r.getControlTime().toString() : null)
                .boilerLoad(r.getBoilerLoadMw())
                .turbineOutput(r.getTurbineOutputMw())
                .gridPurchase(r.getGridPurchaseKwh())
                .powerFactorTarget(r.getPowerFactorTarget())
                .elecNext5min(r.getElecNext5minKwh())
                .steamNext5min(r.getSteamNext5minT())
                .createTime(r.getCreateTime() != null ? r.getCreateTime().toString() : null)
                .build()).collect(Collectors.toList());

        return Result.ok(PageResult.of(result.getTotal(), query.getPageNum(), query.getPageSize(), records));
    }
}
