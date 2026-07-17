package com.xq.service.impl;

import com.xq.common.constant.TaskStatus;
import com.xq.common.constant.TaskType;
import com.xq.common.exception.BusinessException;
import com.xq.common.result.Result;
import com.xq.mapper.AlgorithmTaskMapper;
import com.xq.model.dto.ScheduleGenerateDTO;
import com.xq.model.entity.AlgorithmTask;
import com.xq.model.vo.TaskVO;
import com.xq.service.ProductionScheduleService;
import com.xq.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xq.model.vo.ImportPlanResultVO;
import com.xq.model.vo.SchedulePlanVO;
import com.xq.model.vo.ScheduleDetailVO;
import com.xq.mapper.ProductionSchedulePlanMapper;
import com.xq.mapper.ProductionScheduleDetailMapper;
import com.xq.model.entity.ProductionSchedulePlan;
import com.xq.model.entity.ProductionScheduleDetail;
import com.alibaba.fastjson2.JSON;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductionScheduleServiceImpl implements ProductionScheduleService {

    private final AlgorithmTaskMapper algorithmTaskMapper;
    private final ProductionSchedulePlanMapper schedulePlanMapper;
    private final ProductionScheduleDetailMapper scheduleDetailMapper;
    private final TaskService taskService;

    @Override
    @Transactional
    public Result<TaskVO> generate(ScheduleGenerateDTO dto) {
        AlgorithmTask task = new AlgorithmTask();
        task.setTaskType(TaskType.PRODUCTION_SCHEDULE);
        task.setStatus(TaskStatus.PENDING);
        task.setProgress(0);
        task.setRetryCount(0);
        task.setFrontendRequestJson(JSON.toJSONString(dto));
        algorithmTaskMapper.insert(task);

        // Mock: 模拟异步调用算法
        task.setStatus(TaskStatus.SUCCESS);
        task.setProgress(100);
        algorithmTaskMapper.updateById(task);

        TaskVO vo = TaskVO.builder()
                .taskId(task.getId())
                .taskType(task.getTaskType())
                .status(task.getStatus())
                .build();
        return Result.ok("排产任务已创建", vo);
    }

    @Override
    public Result<SchedulePlanVO> getPlanDetail(Long scheduleId) {
        ProductionSchedulePlan plan = schedulePlanMapper.selectById(scheduleId);
        if (plan == null) {
            throw new BusinessException(404, "排产方案不存在");
        }

        List<ProductionScheduleDetail> details = scheduleDetailMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProductionScheduleDetail>()
                        .eq(ProductionScheduleDetail::getScheduleId, scheduleId)
                        .orderByAsc(ProductionScheduleDetail::getHourIndex)
        );

        List<ScheduleDetailVO> detailVOs = details.stream().map(d -> ScheduleDetailVO.builder()
                .detailId(d.getId())
                .hourIndex(d.getHourIndex())
                .startTime(d.getStartTime())
                .endTime(d.getEndTime())
                .demand(d.getDemand())
                .production(d.getProduction())
                .elecForecast(d.getElecForecast())
                .build()).collect(Collectors.toList());

        SchedulePlanVO vo = SchedulePlanVO.builder()
                .scheduleId(plan.getId())
                .taskId(plan.getTaskId())
                .scheduleName(plan.getScheduleName())
                .planStartTime(plan.getPlanStartTime())
                .planHorizon(plan.getPlanHorizon())
                .planUnit(plan.getPlanUnit())
                .dataGranularity(plan.getDataGranularity())
                .status(plan.getStatus())
                .elecCoefficient(plan.getElecCoefficient())
                .totalDemand(plan.getTotalDemand())
                .totalEnergy(plan.getTotalEnergy())
                .details(detailVOs)
                .build();
        return Result.ok(vo);
    }

    @Override
    @Transactional
    public Result<ImportPlanResultVO> importDailyPlan(Map<String, Object> dailyPlanJson) {
        String timestamp = (String) dailyPlanJson.get("timestamp");
        Integer planHorizon = ((Number) dailyPlanJson.get("plan_horizon")).intValue();
        String planUnit = (String) dailyPlanJson.get("unit");
        String dataGranularity = (String) dailyPlanJson.get("data_granularity");
        Number elecCoefficient = (Number) dailyPlanJson.get("elec_coefficient");
        Number totalDemand = (Number) dailyPlanJson.get("total_demand");
        Number totalEnergy = (Number) dailyPlanJson.get("total_energy");

        if (planHorizon != 24) {
            throw new BusinessException(400, "plan_horizon 必须为 24");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> schedule = (List<Map<String, Object>>) dailyPlanJson.get("schedule");
        if (schedule == null || schedule.size() != 24) {
            throw new BusinessException(400, "schedule 必须包含 24 条记录");
        }

        // 创建算法任务
        AlgorithmTask task = new AlgorithmTask();
        task.setTaskType(TaskType.PRODUCTION_SCHEDULE);
        task.setStatus(TaskStatus.SUCCESS);
        task.setProgress(100);
        task.setAlgorithmResponseJson(JSON.toJSONString(dailyPlanJson));
        task.setResultFileName("daily_plan.json");
        algorithmTaskMapper.insert(task);

        // 创建排产方案主表
        ProductionSchedulePlan plan = new ProductionSchedulePlan();
        plan.setTaskId(task.getId());
        plan.setScheduleName("日级排产方案");
        plan.setPlanStartTime(LocalDateTime.parse(timestamp, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        plan.setPlanHorizon(planHorizon);
        plan.setPlanUnit(planUnit != null ? planUnit : "hour");
        plan.setDataGranularity(dataGranularity != null ? dataGranularity : "1 minute");
        plan.setStatus(TaskStatus.SUCCESS);
        plan.setElecCoefficient(elecCoefficient != null ? new BigDecimal(elecCoefficient.toString()) : null);
        plan.setTotalDemand(totalDemand != null ? new BigDecimal(totalDemand.toString()) : null);
        plan.setTotalEnergy(totalEnergy != null ? new BigDecimal(totalEnergy.toString()) : null);
        plan.setRawPlanJson(JSON.toJSONString(dailyPlanJson));
        schedulePlanMapper.insert(plan);

        // 关联任务和结果
        task.setResultId(plan.getId());
        algorithmTaskMapper.updateById(task);

        // 创建明细
        LocalDateTime planStart = LocalDateTime.parse(timestamp, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        for (Map<String, Object> item : schedule) {
            int hour = ((Number) item.get("hour")).intValue();
            LocalDateTime startTime = planStart.plusHours(hour);
            LocalDateTime endTime = startTime.plusHours(1);

            ProductionScheduleDetail detail = new ProductionScheduleDetail();
            detail.setScheduleId(plan.getId());
            detail.setHourIndex(hour);
            detail.setStartTime(startTime);
            detail.setEndTime(endTime);
            detail.setDemand(new BigDecimal(item.get("demand").toString()));
            detail.setProduction(new BigDecimal(item.get("production").toString()));
            detail.setElecForecast(new BigDecimal(item.get("elec_forecast").toString()));
            detail.setConflictFlag(0);
            scheduleDetailMapper.insert(detail);
        }

        ImportPlanResultVO vo = ImportPlanResultVO.builder()
                .taskId(task.getId())
                .scheduleId(plan.getId())
                .detailCount(schedule.size())
                .build();
        return Result.ok("导入成功", vo);
    }
}
