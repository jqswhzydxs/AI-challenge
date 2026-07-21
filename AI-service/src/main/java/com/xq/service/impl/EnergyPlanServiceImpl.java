package com.xq.service.impl;

import com.xq.common.constant.TaskStatus;
import com.xq.common.constant.TaskType;
import com.xq.common.exception.BusinessException;
import com.xq.common.result.Result;
import com.xq.mapper.AlgorithmTaskMapper;
import com.xq.mapper.EnergyEquipmentMapper;
import com.xq.mapper.EnergyRealtimeDataMapper;
import com.xq.mapper.ProductionScheduleDetailMapper;
import com.xq.mapper.ProductionSchedulePlanMapper;
import com.xq.model.dto.EnergyPlanGenerateDTO;
import com.xq.model.entity.AlgorithmTask;
import com.xq.model.entity.EnergyEquipment;
import com.xq.model.entity.EnergyRealtimeData;
import com.xq.model.entity.ProductionScheduleDetail;
import com.xq.model.entity.ProductionSchedulePlan;
import com.xq.model.vo.TaskVO;
import com.xq.service.EnergyPlanService;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xq.model.vo.EnergyPlanVO;
import com.xq.model.vo.EnergyPlanDetailVO;
import com.xq.mapper.EnergyPlanDetailMapper;
import com.xq.mapper.EnergyPlanMapper;
import com.xq.model.entity.EnergyPlan;
import com.xq.model.entity.EnergyPlanDetail;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EnergyPlanServiceImpl implements EnergyPlanService {

    private final AlgorithmTaskMapper algorithmTaskMapper;
    private final EnergyPlanMapper energyPlanMapper;
    private final EnergyPlanDetailMapper energyPlanDetailMapper;
    private final EnergyEquipmentMapper energyEquipmentMapper;
    private final EnergyRealtimeDataMapper energyRealtimeDataMapper;
    private final ProductionSchedulePlanMapper schedulePlanMapper;
    private final ProductionScheduleDetailMapper scheduleDetailMapper;

    @Override
    @Transactional
    public Result<TaskVO> generate(EnergyPlanGenerateDTO dto) {
        if (dto == null) {
            throw new BusinessException(400, "请求体不能为空");
        }
        LocalDate planDate = parseDate(dto.getPlanDate(), "planDate");
        LocalDateTime planStart = planDate.atStartOfDay();
        List<EnergyPlanDetail> derivedDetails = deriveEnergyDetails(dto, planDate, planStart);
        if (derivedDetails.isEmpty()) {
            throw new BusinessException(400, "没有可用于生成能源运行方案的数据，请先导入实时能源数据或日级排产方案");
        }

        AlgorithmTask task = new AlgorithmTask();
        task.setTaskType(TaskType.ENERGY_PLAN);
        task.setStatus(TaskStatus.PENDING);
        task.setProgress(0);
        task.setMessage("能源运行方案派生计算中");
        task.setRetryCount(0);
        task.setFrontendRequestJson(JSON.toJSONString(dto));
        task.setStartTime(LocalDateTime.now());
        algorithmTaskMapper.insert(task);

        EnergyPlan plan = new EnergyPlan();
        plan.setTaskId(task.getId());
        plan.setPlanDate(planDate);
        plan.setStatus(TaskStatus.SUCCESS);
        plan.setObjective(dto.getObjective());
        plan.setElectricPriceMode(dto.getElectricPriceMode());
        plan.setTimeInterval(60);
        BigDecimal electricityCost = BigDecimal.ZERO;
        BigDecimal steamCost = BigDecimal.ZERO;
        for (EnergyPlanDetail detail : derivedDetails) {
            electricityCost = electricityCost.add(detail.getElectricityConsumption().multiply(priceForHour(detail.getTimestamp().getHour())));
            steamCost = steamCost.add(detail.getSteamConsumption().multiply(steamUnitPrice(dto.getConstraints())));
        }
        plan.setElectricityCost(scale(electricityCost, 2));
        plan.setSteamCost(scale(steamCost, 2));
        plan.setTotalEnergyCost(plan.getElectricityCost().add(plan.getSteamCost()));
        energyPlanMapper.insert(plan);

        for (EnergyPlanDetail detail : derivedDetails) {
            detail.setPlanId(plan.getId());
            detail.setEnergyCost(scale(
                    detail.getElectricityConsumption().multiply(priceForHour(detail.getTimestamp().getHour()))
                            .add(detail.getSteamConsumption().multiply(steamUnitPrice(dto.getConstraints()))),
                    2
            ));
            energyPlanDetailMapper.insert(detail);
        }

        task.setStatus(TaskStatus.SUCCESS);
        task.setProgress(100);
        task.setMessage("能源运行方案已生成");
        task.setResultId(plan.getId());
        task.setFinishTime(LocalDateTime.now());
        algorithmTaskMapper.updateById(task);

        TaskVO vo = TaskVO.builder()
                .taskId(task.getId())
                .taskType(task.getTaskType())
                .status(task.getStatus())
                .progress(task.getProgress())
                .message(task.getMessage())
                .resultId(task.getResultId())
                .errorMessage(task.getErrorMessage())
                .createTime(task.getStartTime())
                .updateTime(task.getFinishTime())
                .build();
        return Result.ok("能源运行任务已创建", vo);
    }

    private List<EnergyPlanDetail> deriveEnergyDetails(EnergyPlanGenerateDTO dto, LocalDate planDate, LocalDateTime planStart) {
        List<EnergyRealtimeData> realtimeData = energyRealtimeDataMapper.selectList(
                new LambdaQueryWrapper<EnergyRealtimeData>()
                        .ge(EnergyRealtimeData::getTimestamp, planStart)
                        .lt(EnergyRealtimeData::getTimestamp, planStart.plusDays(1))
                        .orderByAsc(EnergyRealtimeData::getTimestamp)
        );
        if (!realtimeData.isEmpty()) {
            return aggregateRealtimeDataByHour(realtimeData, dto.getConstraints());
        }
        return deriveFromSchedule(planDate, dto.getConstraints());
    }

    private List<EnergyPlanDetail> aggregateRealtimeDataByHour(List<EnergyRealtimeData> records, Map<String, Object> constraints) {
        List<EnergyPlanDetail> details = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            final int currentHour = hour;
            List<EnergyRealtimeData> group = records.stream()
                    .filter(item -> item.getTimestamp() != null && item.getTimestamp().getHour() == currentHour)
                    .toList();
            if (group.isEmpty()) {
                continue;
            }
            BigDecimal electricity = sum(group.stream().map(EnergyRealtimeData::getElectricityConsumption).toList());
            BigDecimal steam = sum(group.stream().map(EnergyRealtimeData::getSteamConsumption).toList());
            BigDecimal carbon = sum(group.stream().map(EnergyRealtimeData::getCarbonEmissionTco2).toList());
            EnergyPlanDetail detail = new EnergyPlanDetail();
            detail.setTimestamp(group.get(0).getTimestamp().withMinute(0).withSecond(0).withNano(0));
            detail.setEquipmentId(equipmentId(constraints));
            detail.setElectricityConsumption(scale(electricity, 6));
            detail.setSteamConsumption(scale(steam, 6));
            detail.setCarbonEmissionTco2(scale(carbon, 6));
            detail.setOutput(scale(deriveOutput(electricity), 6));
            details.add(detail);
        }
        return details;
    }

    private List<EnergyPlanDetail> deriveFromSchedule(LocalDate planDate, Map<String, Object> constraints) {
        ProductionSchedulePlan schedulePlan = schedulePlanMapper.selectOne(
                new LambdaQueryWrapper<ProductionSchedulePlan>()
                        .eq(ProductionSchedulePlan::getScheduleDate, planDate)
                        .orderByDesc(ProductionSchedulePlan::getCreateTime)
                        .last("LIMIT 1")
        );
        if (schedulePlan == null) {
            return List.of();
        }
        List<ProductionScheduleDetail> scheduleDetails = scheduleDetailMapper.selectList(
                new LambdaQueryWrapper<ProductionScheduleDetail>()
                        .eq(ProductionScheduleDetail::getScheduleId, schedulePlan.getId())
                        .orderByAsc(ProductionScheduleDetail::getHourIndex)
        );
        List<EnergyPlanDetail> details = new ArrayList<>();
        for (ProductionScheduleDetail scheduleDetail : scheduleDetails) {
            BigDecimal electricity = value(scheduleDetail.getElecForecast());
            BigDecimal production = value(scheduleDetail.getProduction());
            EnergyPlanDetail detail = new EnergyPlanDetail();
            detail.setTimestamp(scheduleDetail.getStartTime());
            detail.setEquipmentId(equipmentId(constraints));
            detail.setOutput(scale(production.multiply(new BigDecimal("100")), 6));
            detail.setElectricityConsumption(scale(electricity, 6));
            detail.setSteamConsumption(scale(electricity.multiply(new BigDecimal("0.005")), 6));
            detail.setCarbonEmissionTco2(scale(electricity.multiply(new BigDecimal("0.00057")), 6));
            details.add(detail);
        }
        return details;
    }

    private LocalDate parseDate(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new BusinessException(400, fieldName + " 不能为空，格式应为 yyyy-MM-dd");
        }
        String text = value.trim();
        if (text.length() >= 10) {
            text = text.substring(0, 10);
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            throw new BusinessException(400, fieldName + " 格式错误，应为 yyyy-MM-dd，例如 2026-07-17");
        }
    }

    private BigDecimal priceForHour(int hour) {
        if (hour >= 0 && hour < 8) {
            return new BigDecimal("0.35");
        }
        if (hour >= 18 && hour < 22) {
            return new BigDecimal("1.05");
        }
        return new BigDecimal("0.65");
    }

    private BigDecimal steamUnitPrice(Map<String, Object> constraints) {
        Object value = constraints != null ? constraints.get("steamUnitPrice") : null;
        return value != null ? new BigDecimal(value.toString()) : new BigDecimal("180.00");
    }

    private Long equipmentId(Map<String, Object> constraints) {
        Object value = constraints != null ? constraints.get("equipmentId") : null;
        return value != null ? Long.valueOf(value.toString()) : 1L;
    }

    private BigDecimal deriveOutput(BigDecimal electricity) {
        return value(electricity).multiply(new BigDecimal("0.08"));
    }

    private BigDecimal sum(List<BigDecimal> values) {
        return values.stream().map(this::value).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal value(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private BigDecimal scale(BigDecimal value, int scale) {
        return value(value).setScale(scale, RoundingMode.HALF_UP);
    }

    @Override
    public Result<EnergyPlanVO> getPlanDetail(String planDate) {
        LocalDate date = parseDate(planDate, "planDate");
        EnergyPlan plan = energyPlanMapper.selectOne(
                new LambdaQueryWrapper<EnergyPlan>()
                        .eq(EnergyPlan::getPlanDate, date)
                        .orderByDesc(EnergyPlan::getCreateTime)
                        .last("LIMIT 1")
        );
        if (plan == null) {
            throw new BusinessException(404, "该日期能源方案不存在");
        }

        List<EnergyPlanDetail> details = energyPlanDetailMapper.selectList(
                new LambdaQueryWrapper<EnergyPlanDetail>()
                        .eq(EnergyPlanDetail::getPlanId, plan.getId())
                        .orderByAsc(EnergyPlanDetail::getTimestamp)
        );

        Map<Long, String> equipmentNameMap = details.stream()
                .map(EnergyPlanDetail::getEquipmentId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toMap(id -> id, this::equipmentName));

        List<EnergyPlanDetailVO> detailVOs = details.stream().map(d -> EnergyPlanDetailVO.builder()
                .timestamp(d.getTimestamp())
                .equipmentId(d.getEquipmentId())
                .equipmentName(equipmentNameMap.getOrDefault(d.getEquipmentId(), inferEquipmentName(d.getEquipmentId())))
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

    private String equipmentName(Long equipmentId) {
        if (equipmentId == null) {
            return null;
        }
        EnergyEquipment equipment = energyEquipmentMapper.selectById(equipmentId);
        if (equipment == null || equipment.getEquipmentName() == null || equipment.getEquipmentName().trim().isEmpty()) {
            return inferEquipmentName(equipmentId);
        }
        return equipment.getEquipmentName();
    }

    private String inferEquipmentName(Long equipmentId) {
        if (equipmentId == null) {
            return "能源设备";
        }
        if (equipmentId == 1L) {
            return "锅炉1";
        }
        return "能源设备-" + equipmentId;
    }
}
