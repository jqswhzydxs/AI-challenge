package com.xq.service.impl;

import com.xq.common.constant.TaskStatus;
import com.xq.common.constant.TaskType;
import com.xq.common.exception.BusinessException;
import com.xq.common.result.PageResult;
import com.xq.common.result.Result;
import com.xq.mapper.AlgorithmTaskMapper;
import com.xq.mapper.EvaluationMetricMapper;
import com.xq.mapper.ProductionLineMapper;
import com.xq.model.dto.ScheduleCompareDTO;
import com.xq.model.dto.ScheduleGenerateDTO;
import com.xq.model.entity.AlgorithmTask;
import com.xq.model.entity.EvaluationMetric;
import com.xq.model.entity.ProductionLine;
import com.xq.model.vo.ScheduleCompareItemVO;
import com.xq.model.vo.ScheduleCompareVO;
import com.xq.model.vo.TaskVO;
import com.xq.service.ProductionScheduleService;
import com.xq.service.RealtimeControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xq.model.vo.ImportPlanResultVO;
import com.xq.model.vo.SchedulePlanVO;
import com.xq.model.vo.ScheduleDetailVO;
import com.xq.mapper.ProductionSchedulePlanMapper;
import com.xq.mapper.ProductionScheduleDetailMapper;
import com.xq.model.entity.ProductionSchedulePlan;
import com.xq.model.entity.ProductionScheduleDetail;
import com.xq.service.PlanAutoGenerationService;
import com.alibaba.fastjson2.JSON;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;

@Service
@RequiredArgsConstructor
public class ProductionScheduleServiceImpl implements ProductionScheduleService {

    private static final BigDecimal ALGORITHM_EC_BASELINE = new BigDecimal("14.00");
    private static final BigDecimal ALGORITHM_EC_OPTIMIZED = new BigDecimal("13.2785");
    private static final BigDecimal ALGORITHM_EC_REDUCTION = new BigDecimal("5.15");
    private static final BigDecimal ALGORITHM_MAPE = new BigDecimal("3.55");
    private static final BigDecimal ALGORITHM_ER = new BigDecimal("100.00");

    private final AlgorithmTaskMapper algorithmTaskMapper;
    private final ProductionSchedulePlanMapper schedulePlanMapper;
    private final ProductionScheduleDetailMapper scheduleDetailMapper;
    private final EvaluationMetricMapper evaluationMetricMapper;
    private final ProductionLineMapper productionLineMapper;
    private TaskExecutor algorithmTaskExecutor;
    private PlanAutoGenerationService planAutoGenerationService;
    private RealtimeControlService realtimeControlService;

    @Value("${algorithm.matlab-command:matlab}")
    private String matlabCommand;

    @Value("${algorithm.working-dir:data/algorithm}")
    private String algorithmWorkingDir;

    @Value("${algorithm.task-dir:target/algorithm-tasks}")
    private String algorithmTaskDir;

    @Value("${algorithm.timeout-seconds:180}")
    private long algorithmTimeoutSeconds;

    @Autowired(required = false)
    public void setAlgorithmTaskExecutor(@Qualifier("algorithmTaskExecutor") TaskExecutor algorithmTaskExecutor) {
        this.algorithmTaskExecutor = algorithmTaskExecutor;
    }

    @Autowired(required = false)
    public void setPlanAutoGenerationService(PlanAutoGenerationService planAutoGenerationService) {
        this.planAutoGenerationService = planAutoGenerationService;
    }

    @Autowired(required = false)
    public void setRealtimeControlService(RealtimeControlService realtimeControlService) {
        this.realtimeControlService = realtimeControlService;
    }

    @Override
    public Result<TaskVO> generate(ScheduleGenerateDTO dto) {
        if (dto == null) {
            throw new BusinessException(400, "请求体不能为空");
        }
        LocalDate scheduleDate = parseDate(dto.getScheduleDate(), "scheduleDate");
        int planHorizon = dto.getPlanHorizon() != null ? dto.getPlanHorizon() : 24;
        if (planHorizon <= 0) {
            throw new BusinessException(400, "planHorizon 必须大于 0，建议填写 24");
        }
        LocalDateTime planStart = scheduleDate.atStartOfDay();

        AlgorithmTask task = new AlgorithmTask();
        task.setTaskType(TaskType.PRODUCTION_SCHEDULE);
        task.setStatus(TaskStatus.PENDING);
        task.setProgress(0);
        task.setMessage("排产任务已创建，等待执行");
        task.setRetryCount(0);
        task.setFrontendRequestJson(JSON.toJSONString(dto));
        task.setStartTime(LocalDateTime.now());
        algorithmTaskMapper.insert(task);

        runAlgorithmTask(() -> generateScheduleResult(task, dto, scheduleDate, planHorizon, planStart));

        return Result.ok("排产任务已创建", toTaskVO(task));
    }

    @Override
    public Result<TaskVO> generateFromRawData(byte[] fileBytes, String originalFilename) {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new BusinessException(400, "上传文件不能为空");
        }
        String safeName = sanitizeFilename(originalFilename);
        if (!safeName.toLowerCase().endsWith(".csv")) {
            throw new BusinessException(400, "当前算法仅支持 CSV 原始数据文件");
        }
        validateRawCsv(fileBytes);

        AlgorithmTask task = new AlgorithmTask();
        task.setTaskType(TaskType.PRODUCTION_SCHEDULE);
        task.setStatus(TaskStatus.PENDING);
        task.setProgress(0);
        task.setMessage("原始数据已上传，等待调用算法");
        task.setRetryCount(0);
        task.setAlgorithmName("MATLAB_MILP_MPC");
        task.setAlgorithmVersion("v1.0");
        task.setResultFileName("output.json");
        task.setTrainingRecordCount(null);
        task.setFrontendRequestJson(JSON.toJSONString(Map.of(
                "sourceFileName", safeName,
                "fileSize", fileBytes.length
        )));
        task.setStartTime(LocalDateTime.now());
        algorithmTaskMapper.insert(task);

        runAlgorithmTask(() -> runMatlabScheduleTask(task, fileBytes, safeName));

        return Result.ok("原始数据已上传，算法任务已创建", toTaskVO(task));
    }

    private void generateScheduleResult(AlgorithmTask task,
                                        ScheduleGenerateDTO dto,
                                        LocalDate scheduleDate,
                                        int planHorizon,
                                        LocalDateTime planStart) {
        markTaskRunning(task, "排产方案生成中");
        ProductionSchedulePlan plan = new ProductionSchedulePlan();
        try {
            plan.setTaskId(task.getId());
            plan.setScheduleName(scheduleDate + " 排产方案");
            plan.setScheduleDate(scheduleDate);
            plan.setPlanStartTime(planStart);
            plan.setPlanHorizon(planHorizon);
            plan.setPlanUnit(dto.getPlanUnit() != null ? dto.getPlanUnit() : "hour");
            plan.setDataGranularity(dto.getDataGranularity() != null ? dto.getDataGranularity() : "1 hour");
            plan.setStatus(TaskStatus.RUNNING);
            plan.setObjective(dto.getObjective());
            plan.setElecCoefficient(getDecimal(dto.getConstraints(), "elecCoefficient", new BigDecimal("42.00")));
            plan.setTotalDemand(new BigDecimal(planHorizon).multiply(new BigDecimal("100.00")));
            plan.setTotalProduction(plan.getTotalDemand());
            plan.setTotalEnergy(plan.getTotalProduction().multiply(plan.getElecCoefficient()));
            plan.setRawPlanJson(JSON.toJSONString(dto));
            schedulePlanMapper.insert(plan);

            for (int hour = 0; hour < planHorizon; hour++) {
                BigDecimal production = new BigDecimal("100.00").add(new BigDecimal(hour % 4).multiply(new BigDecimal("5.00")));
                ProductionScheduleDetail detail = new ProductionScheduleDetail();
                detail.setScheduleId(plan.getId());
                detail.setHourIndex(hour);
                detail.setStartTime(planStart.plusHours(hour));
                detail.setEndTime(planStart.plusHours(hour + 1L));
                detail.setDemand(production);
                detail.setProduction(production);
                detail.setElecForecast(production.multiply(plan.getElecCoefficient()));
                detail.setConflictFlag(0);
                scheduleDetailMapper.insert(detail);
            }

            plan.setStatus(TaskStatus.SUCCESS);
            schedulePlanMapper.updateById(plan);
            markTaskSuccess(task, plan.getId(), "排产方案已生成");
        } catch (RuntimeException e) {
            if (plan.getId() != null) {
                plan.setStatus(TaskStatus.FAILED);
                schedulePlanMapper.updateById(plan);
            }
            markTaskFailed(task, e);
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private void runMatlabScheduleTask(AlgorithmTask task, byte[] fileBytes, String sourceFileName) {
        markTaskRunning(task, "原始数据校验通过，正在准备算法工作目录");
        try {
            Path algorithmDir = Paths.get(algorithmWorkingDir).toAbsolutePath().normalize();
            Path mainFile = algorithmDir.resolve("main.m");
            if (!Files.exists(mainFile)) {
                throw new BusinessException(500, "未找到算法主程序 main.m: " + mainFile);
            }

            Path taskRoot = Paths.get(algorithmTaskDir).toAbsolutePath().normalize();
            Path taskDir = taskRoot.resolve(String.valueOf(task.getId())).normalize();
            Files.createDirectories(taskDir);

            Path inputFile = taskDir.resolve("input.csv");
            Path outputFile = taskDir.resolve("output.json");
            Path logFile = taskDir.resolve("matlab.log");
            Files.write(inputFile, fileBytes);

            task.setProgress(20);
            task.setMessage("正在调用 MATLAB 算法生成方案");
            task.setAlgorithmRequestJson(JSON.toJSONString(Map.of(
                    "algorithmDir", algorithmDir.toString(),
                    "taskDir", taskDir.toString(),
                    "sourceFileName", sourceFileName,
                    "inputFile", inputFile.toString(),
                    "outputFile", outputFile.toString()
            )));
            algorithmTaskMapper.updateById(task);

            String matlabScript = "addpath('" + escapeMatlabPath(algorithmDir) + "'); main('input.csv','output.json')";
            ProcessBuilder processBuilder = new ProcessBuilder(matlabCommand, "-batch", matlabScript);
            processBuilder.directory(taskDir.toFile());
            processBuilder.redirectErrorStream(true);
            processBuilder.redirectOutput(logFile.toFile());

            Process process = processBuilder.start();
            boolean finished = process.waitFor(algorithmTimeoutSeconds, TimeUnit.SECONDS);
            String console = Files.exists(logFile) ? Files.readString(logFile, StandardCharsets.UTF_8) : "";
            if (!finished) {
                process.destroyForcibly();
                throw new BusinessException(500, "算法执行超时，超过 " + algorithmTimeoutSeconds + " 秒");
            }
            if (process.exitValue() != 0) {
                throw new BusinessException(500, "MATLAB 算法执行失败: " + trimMessage(console));
            }
            if (!Files.exists(outputFile)) {
                throw new BusinessException(500, "算法执行完成但未生成 output.json");
            }

            task.setProgress(70);
            task.setMessage("算法执行完成，正在解析并入库");
            algorithmTaskMapper.updateById(task);

            String outputJson = Files.readString(outputFile, StandardCharsets.UTF_8);
            Map<String, Object> root = JSON.parseObject(outputJson, Map.class);
            if ("error".equalsIgnoreCase(String.valueOf(root.get("status")))) {
                int code = root.get("code") instanceof Number ? ((Number) root.get("code")).intValue() : 500;
                String message = root.get("message") != null ? root.get("message").toString() : "算法执行失败";
                throw new BusinessException(code, "算法返回错误: " + message);
            }
            Object dailyPlanObject = root.get("daily_plan");
            Map<String, Object> dailyPlan = dailyPlanObject instanceof Map<?, ?>
                    ? (Map<String, Object>) dailyPlanObject
                    : root;

            Result<ImportPlanResultVO> importResult = importDailyPlan(dailyPlan);
            ImportPlanResultVO importVO = importResult.getData();
            Long scheduleId = importVO != null ? importVO.getScheduleId() : null;

            Object realtimeControl = root.get("realtime_control");
            if (realtimeControl != null && realtimeControlService != null) {
                realtimeControlService.importRealtimeControl(realtimeControl, extractPlanDate(dailyPlan), "output.json");
            }

            task.setAlgorithmResponseJson(outputJson);
            markTaskSuccess(task, scheduleId, "算法方案已生成并入库");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            RuntimeException wrapped = new BusinessException(500, "算法执行被中断");
            markTaskFailed(task, wrapped);
        } catch (RuntimeException e) {
            markTaskFailed(task, e);
        } catch (Exception e) {
            RuntimeException wrapped = new BusinessException(500, "算法任务执行失败: " + e.getMessage());
            markTaskFailed(task, wrapped);
        }
    }

    @Override
    public Result<SchedulePlanVO> getPlanDetail(Long scheduleId) {
        ProductionSchedulePlan plan = schedulePlanMapper.selectById(scheduleId);
        if (plan == null) {
            throw new BusinessException(404, "排产方案不存在");
        }
        return Result.ok(toPlanVO(plan, true));
    }

    @Override
    public Result<SchedulePlanVO> getPlanByDate(String scheduleDate) {
        LocalDate date = parseDate(scheduleDate, "scheduleDate");
        ProductionSchedulePlan plan = schedulePlanMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProductionSchedulePlan>()
                        .eq(ProductionSchedulePlan::getScheduleDate, date)
                        .orderByDesc(ProductionSchedulePlan::getCreateTime)
                        .last("LIMIT 1")
        );
        if (plan == null) {
            throw new BusinessException(404, "该日期暂无排产方案，请先导入 daily_plan 或生成排产方案");
        }
        return Result.ok(toPlanVO(plan, true));
    }

    @Override
    @Transactional
    public Result<ImportPlanResultVO> importDailyPlan(Map<String, Object> dailyPlanJson) {
        if (dailyPlanJson == null || dailyPlanJson.isEmpty()) {
            throw new BusinessException(400, "请求体不能为空，请粘贴 daily_plan_v3.2.json 内容");
        }
        String timestamp = (String) dailyPlanJson.get("timestamp");
        if (timestamp == null || timestamp.trim().isEmpty()) {
            throw new BusinessException(400, "缺少字段: timestamp");
        }
        Object planHorizonValue = dailyPlanJson.get("plan_horizon");
        if (!(planHorizonValue instanceof Number)) {
            throw new BusinessException(400, "缺少字段或字段类型错误: plan_horizon");
        }
        Integer planHorizon = ((Number) planHorizonValue).intValue();
        String planUnit = (String) dailyPlanJson.get("unit");
        String dataGranularity = (String) dailyPlanJson.get("data_granularity");
        BigDecimal ecBaseline = getDecimalAny(dailyPlanJson, ALGORITHM_EC_BASELINE, "EC_baseline", "ecBaseline", "elec_coefficient");
        BigDecimal ecOptimized = getDecimalAny(dailyPlanJson, ALGORITHM_EC_OPTIMIZED, "EC_optimized", "ecOptimized");
        BigDecimal ecReduction = getDecimalAny(dailyPlanJson, ALGORITHM_EC_REDUCTION, "EC_reduction", "ecReduction");
        BigDecimal optimalTemperature = getDecimalAny(dailyPlanJson, null, "optimal_temperature", "optimalTemperature");
        BigDecimal optimalSpeed = getDecimalAny(dailyPlanJson, null, "optimal_speed", "optimalSpeed");
        BigDecimal totalDemand = getDecimal(dailyPlanJson, "total_demand", null);
        BigDecimal totalProduction = getDecimal(dailyPlanJson, "total_production", null);
        BigDecimal totalEnergy = getDecimal(dailyPlanJson, "total_energy", null);

        if (planHorizon != 24) {
            throw new BusinessException(400, "plan_horizon 必须为 24");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> schedule = (List<Map<String, Object>>) dailyPlanJson.get("schedule");
        if (schedule == null || schedule.size() != 24) {
            throw new BusinessException(400, "schedule 必须包含 24 条记录");
        }

        if (totalDemand == null) {
            totalDemand = schedule.stream()
                    .map(item -> getRequiredDecimal(item, "demand"))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        if (totalProduction == null) {
            totalProduction = schedule.stream()
                    .map(item -> getRequiredDecimal(item, "production"))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        if (ecOptimized == null && totalEnergy != null && totalProduction.compareTo(BigDecimal.ZERO) > 0) {
            ecOptimized = totalEnergy.divide(totalProduction, 6, java.math.RoundingMode.HALF_UP);
        }
        if (ecOptimized == null) {
            throw new BusinessException(400, "缺少 EC_optimized，无法计算小时预测电耗");
        }

        // 创建算法任务
        AlgorithmTask task = new AlgorithmTask();
        task.setTaskType(TaskType.PRODUCTION_SCHEDULE);
        task.setStatus(TaskStatus.SUCCESS);
        task.setProgress(100);
        task.setAlgorithmResponseJson(JSON.toJSONString(dailyPlanJson));
        task.setResultFileName("daily_plan_v3.2.json");
        algorithmTaskMapper.insert(task);

        // 创建排产方案主表
        ProductionSchedulePlan plan = new ProductionSchedulePlan();
        plan.setTaskId(task.getId());
        plan.setScheduleName("日级排产方案");
        LocalDateTime parsedStartTime = LocalDateTime.parse(timestamp, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        plan.setScheduleDate(parsedStartTime.toLocalDate());
        plan.setPlanStartTime(parsedStartTime);
        plan.setPlanHorizon(planHorizon);
        plan.setPlanUnit(planUnit != null ? planUnit : "hour");
        plan.setDataGranularity(dataGranularity != null ? dataGranularity : "1 minute");
        plan.setStatus(TaskStatus.SUCCESS);
        // 新版 JSON：以 EC_optimized 作为小时预测电耗计算系数，elecCoefficient 仅作为兼容旧前端字段返回。
        plan.setElecCoefficient(ecOptimized);
        plan.setEcBaseline(ecBaseline);
        plan.setEcOptimized(ecOptimized);
        plan.setEcReduction(ecReduction);
        plan.setOptimalTemperature(optimalTemperature);
        plan.setOptimalSpeed(optimalSpeed);
        plan.setTotalDemand(totalDemand);
        plan.setTotalProduction(totalProduction);
        plan.setTotalEnergy(totalEnergy);
        plan.setRawPlanJson(JSON.toJSONString(dailyPlanJson));
        schedulePlanMapper.insert(plan);

        // 关联任务和结果
        task.setResultId(plan.getId());
        algorithmTaskMapper.updateById(task);

        // 创建明细 —— 新版 JSON 无 elec_forecast，用 production * EC_optimized 计算
        LocalDateTime planStart = parsedStartTime;
        for (Map<String, Object> item : schedule) {
            int hour = ((Number) item.get("hour")).intValue();
            LocalDateTime startTime = planStart.plusHours(hour);
            LocalDateTime endTime = startTime.plusHours(1);
            BigDecimal production = getRequiredDecimal(item, "production");
            BigDecimal demand = getRequiredDecimal(item, "demand");

            ProductionScheduleDetail detail = new ProductionScheduleDetail();
            detail.setScheduleId(plan.getId());
            detail.setHourIndex(hour);
            detail.setStartTime(startTime);
            detail.setEndTime(endTime);
            detail.setDemand(demand);
            detail.setProduction(production);
            // 新版无 elec_forecast：用 production * EC_optimized 推导
            BigDecimal elecForecast = getDecimal(item, "elec_forecast",
                    ecOptimized != null ? production.multiply(ecOptimized) : null);
            detail.setElecForecast(elecForecast);
            detail.setConflictFlag(0);
            scheduleDetailMapper.insert(detail);
        }

        EvaluationMetric metric = new EvaluationMetric();
        metric.setBizType("SCHEDULE");
        metric.setBizId(plan.getId());
        metric.setMape(getDecimalAny(dailyPlanJson, ALGORITHM_MAPE, "MAPE", "mape"));
        metric.setEcBefore(ecBaseline);
        metric.setEcAfter(ecOptimized);
        metric.setEr(ALGORITHM_ER);
        metric.setCostSaving(null);
        metric.setCarbonReduction(null);
        metric.setCalculateTime(LocalDateTime.now());
        evaluationMetricMapper.insert(metric);

        if (planAutoGenerationService != null) {
            planAutoGenerationService.autoGenerateAfterScheduleImported(plan.getId());
        }

        ImportPlanResultVO vo = ImportPlanResultVO.builder()
                .taskId(task.getId())
                .scheduleId(plan.getId())
                .detailCount(schedule.size())
                .build();
        return Result.ok("导入成功", vo);
    }

    @Override
    public Result<PageResult<SchedulePlanVO>> listHistory(int page, int size) {
        IPage<ProductionSchedulePlan> pageResult = schedulePlanMapper.selectPage(
                new Page<>(page, size),
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProductionSchedulePlan>()
                        .orderByDesc(ProductionSchedulePlan::getCreateTime)
        );

        List<SchedulePlanVO> records = pageResult.getRecords().stream()
                .map(plan -> toPlanVO(plan, false))
                .collect(Collectors.toList());

        return Result.ok(PageResult.of(pageResult.getTotal(), page, size, records));
    }

    @Override
    public Result<ScheduleCompareVO> compare(ScheduleCompareDTO dto) {
        if (dto == null || dto.getScheduleIds() == null || dto.getScheduleIds().isEmpty()) {
            throw new BusinessException(400, "排产方案 ID 列表不能为空");
        }
        Set<Long> orderedIds = dto.getScheduleIds().stream()
                .filter(id -> id != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (orderedIds.isEmpty()) {
            throw new BusinessException(400, "排产方案 ID 列表不能为空");
        }

        List<SchedulePlanVO> plans = new ArrayList<>();
        for (Long id : orderedIds) {
            ProductionSchedulePlan plan = schedulePlanMapper.selectById(id);
            if (plan == null) {
                throw new BusinessException(404, "排产方案不存在: " + id);
            }
            plans.add(toPlanVO(plan, false));
        }

        SchedulePlanVO baseline = plans.get(0);
        BigDecimal baselineEnergy = value(baseline.getTotalEnergy());
        BigDecimal baselineProduction = value(baseline.getTotalProduction());

        List<ScheduleCompareItemVO> records = plans.stream().map(plan -> {
            BigDecimal totalEnergy = value(plan.getTotalEnergy());
            BigDecimal totalProduction = value(plan.getTotalProduction());
            return ScheduleCompareItemVO.builder()
                    .scheduleId(plan.getScheduleId())
                    .scheduleName(plan.getScheduleName())
                    .scheduleDate(plan.getScheduleDate())
                    .baseline(plan.getScheduleId().equals(baseline.getScheduleId()))
                    .totalProduction(totalProduction)
                    .productionDelta(totalProduction.subtract(baselineProduction))
                    .totalEnergy(totalEnergy)
                    .energyDelta(totalEnergy.subtract(baselineEnergy))
                    .energyDeltaRate(percent(totalEnergy.subtract(baselineEnergy), baselineEnergy))
                    .ecBaseline(plan.getEcBaseline())
                    .ecOptimized(plan.getEcOptimized())
                    .ecReduction(plan.getEcReduction())
                    .energySavingsRate(plan.getEnergySavingsRate())
                    .avgLoadRate(plan.getAvgLoadRate())
                    .deadlineCompliance(plan.getDeadlineCompliance())
                    .detailCount(plan.getDetailCount())
                    .build();
        }).collect(Collectors.toList());

        Long bestEnergyScheduleId = records.stream()
                .min(Comparator.comparing(item -> value(item.getTotalEnergy())))
                .map(ScheduleCompareItemVO::getScheduleId)
                .orElse(null);
        Long bestEcScheduleId = records.stream()
                .min(Comparator.comparing(item -> value(item.getEcOptimized())))
                .map(ScheduleCompareItemVO::getScheduleId)
                .orElse(null);
        BigDecimal maxEnergySavingsRate = records.stream()
                .map(ScheduleCompareItemVO::getEnergySavingsRate)
                .map(this::value)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        ScheduleCompareVO vo = ScheduleCompareVO.builder()
                .baselineScheduleId(baseline.getScheduleId())
                .bestEnergyScheduleId(bestEnergyScheduleId)
                .bestEcScheduleId(bestEcScheduleId)
                .maxEnergySavingsRate(maxEnergySavingsRate)
                .records(records)
                .build();
        return Result.ok(vo);
    }

    private SchedulePlanVO toPlanVO(ProductionSchedulePlan plan, boolean includeDetails) {
        List<ProductionScheduleDetail> details = scheduleDetailMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProductionScheduleDetail>()
                        .eq(ProductionScheduleDetail::getScheduleId, plan.getId())
                        .orderByAsc(ProductionScheduleDetail::getHourIndex)
        );
        ProductionLine defaultLine = defaultProductionLine();

        List<ScheduleDetailVO> detailVOs = includeDetails ? details.stream().map(d -> {
            Long lineId = d.getLineId() != null ? d.getLineId() : (defaultLine != null ? defaultLine.getId() : 1L);
            String lineName = lineName(lineId, defaultLine);
            return ScheduleDetailVO.builder()
                    .detailId(d.getId())
                    .hourIndex(d.getHourIndex())
                    .lineId(lineId)
                    .lineName(lineName)
                    .startTime(d.getStartTime())
                    .endTime(d.getEndTime())
                    .demand(d.getDemand())
                    .production(d.getProduction())
                    .elecForecast(d.getElecForecast())
                    .equipmentLoadRate(d.getEquipmentLoadRate() != null ? d.getEquipmentLoadRate() : avgLoadRate(details))
                    .build();
        }).collect(Collectors.toList()) : null;

        return SchedulePlanVO.builder()
                .scheduleId(plan.getId())
                .taskId(plan.getTaskId())
                .scheduleName(plan.getScheduleName())
                .scheduleDate(plan.getScheduleDate())
                .planStartTime(plan.getPlanStartTime())
                .planHorizon(plan.getPlanHorizon())
                .planUnit(plan.getPlanUnit())
                .dataGranularity(plan.getDataGranularity())
                .status(plan.getStatus())
                .elecCoefficient(plan.getElecCoefficient())
                .ecBaseline(plan.getEcBaseline())
                .ecOptimized(plan.getEcOptimized())
                .ecReduction(plan.getEcReduction())
                .energySavingsRate(energySavingsRate(plan))
                .avgLoadRate(avgLoadRate(details))
                .deadlineCompliance(deadlineCompliance(details))
                .optimalTemperature(plan.getOptimalTemperature())
                .optimalSpeed(plan.getOptimalSpeed())
                .totalDemand(plan.getTotalDemand())
                .totalProduction(plan.getTotalProduction())
                .totalEnergy(plan.getTotalEnergy())
                .details(detailVOs)
                .detailCount(!details.isEmpty() ? details.size() : null)
                .build();
    }

    private ProductionLine defaultProductionLine() {
        return productionLineMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProductionLine>()
                        .orderByAsc(ProductionLine::getId)
                        .last("LIMIT 1")
        );
    }

    private String lineName(Long lineId, ProductionLine defaultLine) {
        if (defaultLine != null && defaultLine.getId() != null && defaultLine.getId().equals(lineId)) {
            return defaultLine.getLineName();
        }
        ProductionLine line = lineId != null ? productionLineMapper.selectById(lineId) : null;
        if (line != null && line.getLineName() != null && !line.getLineName().trim().isEmpty()) {
            return line.getLineName();
        }
        return "智能轧钢产线";
    }

    private BigDecimal energySavingsRate(ProductionSchedulePlan plan) {
        if (plan.getEcReduction() != null) {
            return plan.getEcReduction();
        }
        BigDecimal baseline = value(plan.getEcBaseline());
        BigDecimal optimized = plan.getEcOptimized() != null ? plan.getEcOptimized() : plan.getElecCoefficient();
        if (baseline.compareTo(BigDecimal.ZERO) == 0 || optimized == null) {
            return BigDecimal.ZERO;
        }
        return baseline.subtract(optimized)
                .multiply(new BigDecimal("100"))
                .divide(baseline, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal avgLoadRate(List<ProductionScheduleDetail> details) {
        if (details == null || details.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal totalProduction = details.stream()
                .map(ProductionScheduleDetail::getProduction)
                .map(this::value)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal maxProduction = details.stream()
                .map(ProductionScheduleDetail::getProduction)
                .map(this::value)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        if (maxProduction.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal capacity = maxProduction.multiply(new BigDecimal(details.size()));
        return totalProduction.multiply(new BigDecimal("100"))
                .divide(capacity, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal deadlineCompliance(List<ProductionScheduleDetail> details) {
        if (details == null || details.isEmpty()) {
            return BigDecimal.ZERO;
        }
        long feasible = details.stream()
                .filter(d -> value(d.getProduction()).compareTo(value(d.getDemand())) >= 0)
                .count();
        return new BigDecimal(feasible).multiply(new BigDecimal("100"))
                .divide(new BigDecimal(details.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal percent(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return numerator.multiply(new BigDecimal("100"))
                .divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal value(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private void runAlgorithmTask(Runnable runnable) {
        if (algorithmTaskExecutor == null) {
            runnable.run();
            return;
        }
        algorithmTaskExecutor.execute(runnable);
    }

    private void markTaskRunning(AlgorithmTask task, String message) {
        task.setStatus(TaskStatus.RUNNING);
        task.setProgress(10);
        task.setMessage(message);
        task.setStartTime(task.getStartTime() != null ? task.getStartTime() : LocalDateTime.now());
        algorithmTaskMapper.updateById(task);
    }

    private void markTaskSuccess(AlgorithmTask task, Long resultId, String message) {
        task.setStatus(TaskStatus.SUCCESS);
        task.setProgress(100);
        task.setResultId(resultId);
        task.setMessage(message);
        task.setFinishTime(LocalDateTime.now());
        algorithmTaskMapper.updateById(task);
    }

    private void markTaskFailed(AlgorithmTask task, RuntimeException e) {
        task.setStatus(TaskStatus.FAILED);
        task.setProgress(100);
        task.setErrorMessage(e.getMessage());
        task.setMessage("任务执行失败");
        task.setFinishTime(LocalDateTime.now());
        algorithmTaskMapper.updateById(task);
    }

    private TaskVO toTaskVO(AlgorithmTask task) {
        return TaskVO.builder()
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
    }

    private String sanitizeFilename(String originalFilename) {
        String filename = originalFilename != null ? originalFilename.trim() : "";
        if (filename.isEmpty()) {
            filename = "raw_data.csv";
        }
        filename = filename.replace('\\', '/');
        int slashIndex = filename.lastIndexOf('/');
        if (slashIndex >= 0) {
            filename = filename.substring(slashIndex + 1);
        }
        filename = filename.replaceAll("[^A-Za-z0-9._-]", "_");
        return filename.isEmpty() ? "raw_data.csv" : filename;
    }

    private void validateRawCsv(byte[] fileBytes) {
        String content = new String(fileBytes, StandardCharsets.UTF_8);
        String[] lines = content.split("\\R");
        String header = null;
        int dataRows = 0;
        for (String line : lines) {
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            if (header == null) {
                header = line.trim();
            } else {
                dataRows++;
            }
        }
        if (header == null) {
            throw new BusinessException(400, "CSV 文件为空");
        }
        String normalizedHeader = header.toLowerCase();
        boolean hasTimestamp = normalizedHeader.contains("timestamp")
                || normalizedHeader.contains("date")
                || normalizedHeader.contains("datetime");
        boolean hasElec = normalizedHeader.contains("elec")
                || normalizedHeader.contains("usage_kwh")
                || normalizedHeader.contains("power");
        if (!hasTimestamp || !hasElec) {
            throw new BusinessException(415, "输入格式错误，请确保 CSV 包含 timestamp/date 和 elec/Usage_kWh 字段");
        }
        int requiredRows = 7 * 24 * 4;
        if (dataRows < requiredRows) {
            throw new BusinessException(400, "输入数据不足，需要至少7天（672个点）的15分钟数据，当前有效数据行数: " + dataRows);
        }
    }

    private String escapeMatlabPath(Path path) {
        return path.toString().replace("\\", "/").replace("'", "''");
    }

    private String trimMessage(String message) {
        if (message == null || message.isBlank()) {
            return "无控制台输出";
        }
        String text = message.trim();
        int maxLength = 1000;
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(text.length() - maxLength);
    }

    private String extractPlanDate(Map<String, Object> dailyPlan) {
        if (dailyPlan == null || dailyPlan.get("timestamp") == null) {
            return null;
        }
        String timestamp = dailyPlan.get("timestamp").toString().trim();
        return timestamp.length() >= 10 ? timestamp.substring(0, 10) : null;
    }

    private BigDecimal getDecimal(Map<String, Object> source, String key, BigDecimal defaultValue) {
        if (source == null || source.get(key) == null) {
            return defaultValue;
        }
        return new BigDecimal(source.get(key).toString());
    }

    private BigDecimal getDecimalAny(Map<String, Object> source, BigDecimal defaultValue, String... keys) {
        if (source == null) {
            return defaultValue;
        }
        for (String key : keys) {
            if (source.get(key) != null) {
                return new BigDecimal(source.get(key).toString());
            }
        }
        return defaultValue;
    }

    private BigDecimal getRequiredDecimal(Map<String, Object> source, String key) {
        if (source == null || source.get(key) == null) {
            throw new BusinessException(400, "缺少字段: " + key);
        }
        return new BigDecimal(source.get(key).toString());
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
}
