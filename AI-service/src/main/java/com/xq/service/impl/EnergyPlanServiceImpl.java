package com.xq.service.impl;

import com.xq.common.constant.TaskStatus;
import com.xq.common.constant.TaskType;
import com.xq.common.exception.BusinessException;
import com.xq.common.result.Result;
import com.xq.mapper.AlgorithmTaskMapper;
import com.xq.model.dto.EnergyPlanGenerateDTO;
import com.xq.model.entity.AlgorithmTask;
import com.xq.model.vo.TaskVO;
import com.xq.service.EnergyPlanService;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.xq.model.vo.EnergyPlanVO;
import com.xq.model.vo.EnergyPlanDetailVO;
import com.xq.mapper.EnergyPlanDetailMapper;
import com.xq.mapper.EnergyPlanMapper;
import com.xq.model.entity.EnergyPlan;
import com.xq.model.entity.EnergyPlanDetail;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EnergyPlanServiceImpl implements EnergyPlanService {

    private final AlgorithmTaskMapper algorithmTaskMapper;
    private final EnergyPlanMapper energyPlanMapper;
    private final EnergyPlanDetailMapper energyPlanDetailMapper;

    @Override
    public Result<TaskVO> generate(EnergyPlanGenerateDTO dto) {
        LocalDate planDate = LocalDate.parse(dto.getPlanDate());
        LocalDateTime planStart = planDate.atStartOfDay();

        AlgorithmTask task = new AlgorithmTask();
        task.setTaskType(TaskType.ENERGY_PLAN);
        task.setStatus(TaskStatus.PENDING);
        task.setProgress(0);
        task.setRetryCount(0);
        task.setFrontendRequestJson(JSON.toJSONString(dto));
        algorithmTaskMapper.insert(task);

        EnergyPlan plan = new EnergyPlan();
        plan.setTaskId(task.getId());
        plan.setPlanDate(planDate);
        plan.setStatus(TaskStatus.SUCCESS);
        plan.setObjective(dto.getObjective());
        plan.setElectricPriceMode(dto.getElectricPriceMode());
        plan.setTimeInterval(60);
        plan.setElectricityCost(new BigDecimal("23500.00"));
        plan.setSteamCost(new BigDecimal("12800.00"));
        plan.setTotalEnergyCost(plan.getElectricityCost().add(plan.getSteamCost()));
        energyPlanMapper.insert(plan);

        for (int hour = 0; hour < 24; hour++) {
            EnergyPlanDetail detail = new EnergyPlanDetail();
            detail.setPlanId(plan.getId());
            detail.setTimestamp(planStart.plusHours(hour));
            detail.setEquipmentId(1L);
            detail.setOutput(new BigDecimal("80.00").add(new BigDecimal(hour % 5)));
            detail.setElectricityConsumption(new BigDecimal("980.00").add(new BigDecimal(hour * 8L)));
            detail.setSteamConsumption(new BigDecimal("210.00").add(new BigDecimal(hour % 6)));
            detail.setCarbonEmissionTco2(new BigDecimal("12.50").add(new BigDecimal(hour).multiply(new BigDecimal("0.03"))));
            detail.setEnergyCost(new BigDecimal("1450.00").add(new BigDecimal(hour * 12L)));
            energyPlanDetailMapper.insert(detail);
        }

        task.setStatus(TaskStatus.SUCCESS);
        task.setProgress(100);
        task.setResultId(plan.getId());
        algorithmTaskMapper.updateById(task);

        TaskVO vo = TaskVO.builder()
                .taskId(task.getId())
                .taskType(task.getTaskType())
                .status(task.getStatus())
                .progress(task.getProgress())
                .resultId(task.getResultId())
                .build();
        return Result.ok("能源运行任务已创建", vo);
    }

    @Override
    public Result<EnergyPlanVO> getPlanDetail(Long planId) {
        EnergyPlan plan = energyPlanMapper.selectById(planId);
        if (plan == null) {
            throw new BusinessException(404, "能源方案不存在");
        }

        List<EnergyPlanDetail> details = energyPlanDetailMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EnergyPlanDetail>()
                        .eq(EnergyPlanDetail::getPlanId, planId)
                        .orderByAsc(EnergyPlanDetail::getTimestamp)
        );

        List<EnergyPlanDetailVO> detailVOs = details.stream().map(d -> EnergyPlanDetailVO.builder()
                .timestamp(d.getTimestamp())
                .equipmentId(d.getEquipmentId())
                .output(d.getOutput())
                .electricityConsumption(d.getElectricityConsumption())
                .steamConsumption(d.getSteamConsumption())
                .carbonEmissionTco2(d.getCarbonEmissionTco2())
                .energyCost(d.getEnergyCost())
                .build()).collect(Collectors.toList());

        EnergyPlanVO vo = EnergyPlanVO.builder()
                .planId(plan.getId())
                .taskId(plan.getTaskId())
                .planDate(plan.getPlanDate())
                .status(plan.getStatus())
                .electricityCost(plan.getElectricityCost())
                .steamCost(plan.getSteamCost())
                .totalEnergyCost(plan.getTotalEnergyCost())
                .details(detailVOs)
                .build();
        return Result.ok(vo);
    }
}
