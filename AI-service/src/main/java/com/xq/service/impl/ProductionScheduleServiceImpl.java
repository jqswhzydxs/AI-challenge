package com.xq.service.impl;

import com.xq.common.constant.TaskStatus;
import com.xq.common.constant.TaskType;
import com.xq.common.exception.BusinessException;
import com.xq.common.result.PageResult;
import com.xq.common.result.Result;
import com.xq.mapper.AlgorithmTaskMapper;
import com.xq.mapper.EnergyPlanDetailMapper;
import com.xq.mapper.EnergyPlanMapper;
import com.xq.mapper.EnergyRealtimeDataMapper;
import com.xq.mapper.EvaluationMetricMapper;
import com.xq.mapper.ProductionLineMapper;
import com.xq.mapper.ProductionOrderMapper;
import com.xq.model.dto.ScheduleCompareDTO;
import com.xq.model.dto.ScheduleGenerateDTO;
import com.xq.model.entity.AlgorithmTask;
import com.xq.model.entity.EnergyPlan;
import com.xq.model.entity.EnergyPlanDetail;
import com.xq.model.entity.EnergyRealtimeData;
import com.xq.model.entity.EvaluationMetric;
import com.xq.model.entity.ProductionLine;
import com.xq.model.entity.ProductionOrder;
import com.xq.model.vo.ScheduleCompareItemVO;
import com.xq.model.vo.ScheduleCompareVO;
import com.xq.model.vo.TaskVO;
import com.xq.service.ProductionScheduleService;
import com.xq.service.RealtimeControlService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;

@Service
@RequiredArgsConstructor
public class ProductionScheduleServiceImpl implements ProductionScheduleService {

    private static final Logger log = LoggerFactory.getLogger(ProductionScheduleServiceImpl.class);

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
    private final ProductionOrderMapper productionOrderMapper;
    private TaskExecutor algorithmTaskExecutor;
    private PlanAutoGenerationService planAutoGenerationService;
    private RealtimeControlService realtimeControlService;
    private EnergyRealtimeDataMapper energyRealtimeDataMapper;
    private EnergyPlanMapper energyPlanMapper;
    private EnergyPlanDetailMapper energyPlanDetailMapper;

    @Value("${algorithm.runtime:python}")
    private String algorithmRuntime;

    @Value("${algorithm.command:python}")
    private String algorithmCommand;

    @Value("${algorithm.script:generate_plan.py}")
    private String algorithmScript;

    @Value("${algorithm.matlab-command:matlab}")
    private String matlabCommand;

    @Value("${algorithm.working-dir:data/algorithm}")
    private String algorithmWorkingDir;

    @Value("${algorithm.task-dir:target/algorithm-tasks}")
    private String algorithmTaskDir;

    @Value("${algorithm.timeout-seconds:180}")
    private long algorithmTimeoutSeconds;

    @Value("${algorithm.training-days:7}")
    private int algorithmTrainingDays;

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

    @Autowired(required = false)
    public void setEnergyRealtimeDataMapper(EnergyRealtimeDataMapper energyRealtimeDataMapper) {
        this.energyRealtimeDataMapper = energyRealtimeDataMapper;
    }

    @Autowired(required = false)
    public void setEnergyPlanMapper(EnergyPlanMapper energyPlanMapper) {
        this.energyPlanMapper = energyPlanMapper;
    }

    @Autowired(required = false)
    public void setEnergyPlanDetailMapper(EnergyPlanDetailMapper energyPlanDetailMapper) {
        this.energyPlanDetailMapper = energyPlanDetailMapper;
    }

    @Override
    public Result<TaskVO> generate(ScheduleGenerateDTO dto) {
        if (dto == null) {
            throw new BusinessException(400, "请求体不能为空");
        }
        if (dto.getPlannedQuantity() != null) {
            return generateFromCollectedData(dto);
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
    public Result<TaskVO> generateFromCollectedData(ScheduleGenerateDTO dto) {
        if (dto == null) {
            throw new BusinessException(400, "请求体不能为空");
        }
        LocalDate scheduleDate = parseDate(dto.getScheduleDate(), "scheduleDate");
        int planHorizon = dto.getPlanHorizon() != null ? dto.getPlanHorizon() : 24;
        if (planHorizon != 24) {
            throw new BusinessException(400, "当前算法固定生成 24 小时日计划，planHorizon 请填写 24 或不填");
        }
        if (dto.getPlannedQuantity() == null || dto.getPlannedQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "plannedQuantity 必须填写为正数，前端只需录入次日订单产量");
        }
        // 订单表即排产预约表：先建订单记录未来生产需求
        ensureSimpleOrder(scheduleDate, dto);

        LocalDate today = LocalDate.now();
        if (scheduleDate.isAfter(today)) {
            // 未来日期：只预约不生成，等待到期后由 autoGeneratePendingSchedules 定时任务自动生成
            AlgorithmTask task = new AlgorithmTask();
            task.setTaskType(TaskType.PRODUCTION_SCHEDULE);
            task.setStatus(TaskStatus.PENDING);
            task.setProgress(0);
            task.setMessage("排产已预约，等待 " + scheduleDate + " 到期后自动生成方案");
            task.setRetryCount(0);
            Map<String, Object> frontendRequest = new LinkedHashMap<>();
            frontendRequest.put("source", "order_reservation");
            frontendRequest.put("scheduleDate", scheduleDate.toString());
            frontendRequest.put("plannedQuantity", dto.getPlannedQuantity());
            frontendRequest.put("reservation", true);
            frontendRequest.put("autoGenerateAfter", scheduleDate.toString());
            task.setFrontendRequestJson(JSON.toJSONString(frontendRequest));
            task.setStartTime(LocalDateTime.now());
            algorithmTaskMapper.insert(task);
            log.info("排产已预约 scheduleDate={} plannedQuantity={} taskId={}，到期后自动生成",
                    scheduleDate, dto.getPlannedQuantity(), task.getId());
            return Result.ok("排产已预约，将在 " + scheduleDate + " 到期后自动生成方案", toTaskVO(task));
        }

        // 已到期（今天/过去）：立即生成排产方案
        return generateScheduleForDate(scheduleDate);
    }

    /**
     * 为指定日期生成排产方案（幂等：已有成功方案则跳过）.
     * <p>
     * 供 {@link #generateFromCollectedData} 到期立即生成和 {@link #autoGeneratePendingSchedules} 定时扫描调用。
     * </p>
     */
    private Result<TaskVO> generateScheduleForDate(LocalDate scheduleDate) {
        // 幂等：只要存在非失败状态的排产方案就跳过（避免今天的 RUNNING 方案被重复生成）
        Long existingCount = schedulePlanMapper.selectCount(
                new LambdaQueryWrapper<ProductionSchedulePlan>()
                        .eq(ProductionSchedulePlan::getScheduleDate, scheduleDate)
                        .notIn(ProductionSchedulePlan::getStatus, TaskStatus.FAILED)
        );
        if (existingCount != null && existingCount > 0) {
            log.info("日期 {} 已有非失败的排产方案，跳过生成", scheduleDate);
            throw new BusinessException(409, "日期 " + scheduleDate + " 已有排产方案，无需重复生成");
        }

        List<ProductionOrder> orders = selectOrdersForScheduleDate(scheduleDate);
        if (orders.isEmpty()) {
            log.info("日期 {} 无待排产订单，跳过生成", scheduleDate);
            throw new BusinessException(400, "日期 " + scheduleDate + " 无待排产订单");
        }

        List<EnergyRealtimeData> energyRows = selectEnergyRowsForAlgorithm(algorithmTrainingDays);
        byte[] inputBytes = buildEnergyCsv(energyRows).getBytes(StandardCharsets.UTF_8);

        AlgorithmTask task = new AlgorithmTask();
        task.setTaskType(TaskType.PRODUCTION_SCHEDULE);
        task.setStatus(TaskStatus.PENDING);
        task.setProgress(0);
        task.setMessage("已读取采集能源数据，等待调用算法");
        task.setRetryCount(0);
        task.setAlgorithmName(isMatlabRuntime() ? "MATLAB_MILP_MPC" : "PYTHON_MILP_MPC");
        task.setAlgorithmVersion(isMatlabRuntime() ? "v1.1" : "v1.0");
        task.setResultFileName("output.json");
        task.setTrainingRecordCount(energyRows.size());
        Map<String, Object> frontendRequest = new LinkedHashMap<>();
        frontendRequest.put("source", "energy_realtime_data");
        frontendRequest.put("scheduleDate", scheduleDate.toString());
        frontendRequest.put("trainingDays", algorithmTrainingDays);
        frontendRequest.put("trainingRecordCount", energyRows.size());
        frontendRequest.put("orderCount", orders.size());
        task.setFrontendRequestJson(JSON.toJSONString(frontendRequest));
        task.setStartTime(LocalDateTime.now());
        algorithmTaskMapper.insert(task);

        runAlgorithmTask(() -> runExternalScheduleTask(task, inputBytes, "collected_energy_realtime_data.csv", scheduleDate));

        return Result.ok("已使用采集能源数据创建算法任务", toTaskVO(task));
    }

    /**
     * 定时扫描到期/过期的待排产订单，自动生成排产方案.
     * <p>
     * 订单表即排产预约表。本任务每 30 分钟扫描一次，查找 dueTime <= 今天 且 status != COMPLETED 的订单，
     * 按到期日期分组，对每个尚无成功排产方案的日期触发自动生成。
     * </p>
     */
    @Scheduled(fixedDelayString = "${schedule.auto-generate.interval-ms:1800000}",
            initialDelayString = "${schedule.auto-generate.initial-delay-ms:60000}")
    public void autoGeneratePendingSchedules() {
        LocalDate today = LocalDate.now();

        // 把历史排产方案标记为已完成（已完成方案不再参与后续自动能源派生）
        markPastSchedulesCompleted(today);

        List<ProductionOrder> pendingOrders = productionOrderMapper.selectList(
                new LambdaQueryWrapper<ProductionOrder>()
                        .le(ProductionOrder::getDueTime, today.atTime(23, 59, 59))
                        .ne(ProductionOrder::getStatus, "COMPLETED")
        );
        if (pendingOrders.isEmpty()) {
            return;
        }
        Set<LocalDate> dueDates = new LinkedHashSet<>();
        for (ProductionOrder order : pendingOrders) {
            if (order.getDueTime() != null) {
                dueDates.add(order.getDueTime().toLocalDate());
            }
        }
        log.info("定时扫描：发现 {} 个到期/过期日期待检查排产方案生成", dueDates.size());
        for (LocalDate dueDate : dueDates) {
            try {
                generateScheduleForDate(dueDate);
                log.info("已触发 {} 排产方案自动生成", dueDate);
            } catch (RuntimeException e) {
                log.info("跳过 {} 排产方案自动生成: {}", dueDate, e.getMessage());
            }
        }
    }

    /**
     * 将排产日期早于今天的非已完成方案统一标记为已完成。
     */
    private void markPastSchedulesCompleted(LocalDate today) {
        int rows = schedulePlanMapper.update(null,
                new LambdaUpdateWrapper<ProductionSchedulePlan>()
                        .lt(ProductionSchedulePlan::getScheduleDate, today)
                        .ne(ProductionSchedulePlan::getStatus, TaskStatus.SUCCESS)
                        .set(ProductionSchedulePlan::getStatus, TaskStatus.SUCCESS)
        );
        if (rows > 0) {
            log.info("已将 {} 条历史排产方案标记为已完成", rows);
        }
    }

    /**
     * 查询兜底：历史方案（日期早于今天）应为已完成；今天及未来不应为已完成。
     */
    private void ensureScheduleStatusCorrect(ProductionSchedulePlan plan) {
        if (plan == null || plan.getScheduleDate() == null) {
            return;
        }
        LocalDate today = LocalDate.now();
        if (plan.getScheduleDate().isBefore(today)
                && !TaskStatus.SUCCESS.equals(plan.getStatus())) {
            plan.setStatus(TaskStatus.SUCCESS);
            schedulePlanMapper.updateById(plan);
        } else if (!plan.getScheduleDate().isBefore(today)
                && TaskStatus.SUCCESS.equals(plan.getStatus())) {
            plan.setStatus(TaskStatus.RUNNING);
            schedulePlanMapper.updateById(plan);
        }
    }

    @Override
    public Result<TaskVO> generateFromRawData(byte[] fileBytes, String originalFilename, String scheduleDate) {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new BusinessException(400, "上传文件不能为空");
        }
        String safeName = sanitizeFilename(originalFilename);
        if (!safeName.toLowerCase().endsWith(".csv")) {
            throw new BusinessException(400, "当前算法仅支持 CSV 原始数据文件");
        }
        validateRawCsv(fileBytes);
        LocalDate requestedScheduleDate = hasText(scheduleDate) ? parseDate(scheduleDate, "scheduleDate") : null;

        AlgorithmTask task = new AlgorithmTask();
        task.setTaskType(TaskType.PRODUCTION_SCHEDULE);
        task.setStatus(TaskStatus.PENDING);
        task.setProgress(0);
        task.setMessage("原始数据已上传，等待调用算法");
        task.setRetryCount(0);
        task.setAlgorithmName(isMatlabRuntime() ? "MATLAB_MILP_MPC" : "PYTHON_MILP_MPC");
        task.setAlgorithmVersion(isMatlabRuntime() ? "v1.1" : "v1.0");
        task.setResultFileName("output.json");
        task.setTrainingRecordCount(null);
        Map<String, Object> frontendRequest = new LinkedHashMap<>();
        frontendRequest.put("sourceFileName", safeName);
        frontendRequest.put("fileSize", fileBytes.length);
        if (requestedScheduleDate != null) {
            frontendRequest.put("scheduleDate", requestedScheduleDate.toString());
        }
        task.setFrontendRequestJson(JSON.toJSONString(frontendRequest));
        task.setStartTime(LocalDateTime.now());
        algorithmTaskMapper.insert(task);

        runAlgorithmTask(() -> runExternalScheduleTask(task, fileBytes, safeName, requestedScheduleDate));

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

            // 今天及未来的方案标记为执行中，只有历史日期才显示已完成
            plan.setStatus(scheduleDate.isBefore(LocalDate.now())
                    ? TaskStatus.SUCCESS : TaskStatus.RUNNING);
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
    private void runExternalScheduleTask(AlgorithmTask task,
                                         byte[] fileBytes,
                                         String sourceFileName,
                                         LocalDate requestedScheduleDate) {
        markTaskRunning(task, "原始数据校验通过，正在准备算法工作目录");
        String phase = "准备算法工作目录";
        try {
            Path algorithmDir = Paths.get(algorithmWorkingDir).toAbsolutePath().normalize();
            Path scriptFile = resolveAlgorithmScript(algorithmDir);
            if (!Files.exists(scriptFile)) {
                throw new BusinessException(500, "未找到算法主程序: " + scriptFile);
            }

            Path taskRoot = Paths.get(algorithmTaskDir).toAbsolutePath().normalize();
            Path taskDir = taskRoot.resolve(String.valueOf(task.getId())).normalize();
            Files.createDirectories(taskDir);

            Path inputFile = taskDir.resolve("input.csv");
            Path outputFile = taskDir.resolve("output.json");
            Path logFile = taskDir.resolve("algorithm.log");
            Path ordersFile = taskDir.resolve("orders.csv");
            Files.write(inputFile, fileBytes);

            LocalDate orderScheduleDate = resolveOrderScheduleDate(requestedScheduleDate);
            List<ProductionOrder> orders = orderScheduleDate != null
                    ? selectOrdersForScheduleDate(orderScheduleDate)
                    : List.of();
            if (!orders.isEmpty()) {
                Files.writeString(ordersFile, buildOrdersCsv(orders), StandardCharsets.UTF_8);
            }

            phase = "调用算法程序";
            task.setProgress(20);
            task.setMessage("正在调用 " + algorithmRuntime + " 算法生成方案");
            Map<String, Object> algorithmRequest = new LinkedHashMap<>();
            algorithmRequest.put("runtime", algorithmRuntime);
            algorithmRequest.put("command", isMatlabRuntime() ? matlabCommand : algorithmCommand);
            algorithmRequest.put("algorithmDir", algorithmDir.toString());
            algorithmRequest.put("scriptFile", scriptFile.toString());
            algorithmRequest.put("taskDir", taskDir.toString());
            algorithmRequest.put("sourceFileName", sourceFileName);
            algorithmRequest.put("inputFile", inputFile.toString());
            algorithmRequest.put("outputFile", outputFile.toString());
            if (orderScheduleDate != null) {
                algorithmRequest.put("scheduleDate", orderScheduleDate.toString());
            }
            algorithmRequest.put("orderCount", orders.size());
            if (!orders.isEmpty()) {
                algorithmRequest.put("ordersFile", ordersFile.toString());
            }
            task.setAlgorithmRequestJson(JSON.toJSONString(algorithmRequest));
            algorithmTaskMapper.updateById(task);

            ProcessBuilder processBuilder = buildAlgorithmProcessBuilder(
                    algorithmDir,
                    scriptFile,
                    inputFile,
                    outputFile,
                    !orders.isEmpty() ? ordersFile : null
            );
            processBuilder.directory(taskDir.toFile());
            processBuilder.redirectErrorStream(true);
            processBuilder.redirectOutput(logFile.toFile());

            Process process = processBuilder.start();
            boolean finished = process.waitFor(algorithmTimeoutSeconds, TimeUnit.SECONDS);
            phase = "读取算法日志";
            String console = readTextLeniently(logFile);
            if (!finished) {
                process.destroyForcibly();
                throw new BusinessException(500, "算法执行超时，超过 " + algorithmTimeoutSeconds + " 秒");
            }
            if (process.exitValue() != 0) {
                throw new BusinessException(500, algorithmRuntime + " 算法执行失败: " + trimMessage(console));
            }
            if (!Files.exists(outputFile)) {
                throw new BusinessException(500, "算法执行完成但未生成 output.json");
            }

            phase = "解析算法输出";
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

            phase = "导入排产方案";
            Result<ImportPlanResultVO> importResult = importDailyPlan(dailyPlan);
            ImportPlanResultVO importVO = importResult.getData();
            Long scheduleId = importVO != null ? importVO.getScheduleId() : null;
            markOrdersScheduled(orders, orderScheduleDate);

            String energyImportWarning = null;
            Object algorithmEnergyPlan = root.get("energy_plan");
            if (algorithmEnergyPlan != null) {
                phase = "import algorithm energy_plan";
                try {
                    importAlgorithmEnergyPlan(algorithmEnergyPlan, scheduleId, task.getId(), dailyPlan);
                    if (planAutoGenerationService != null) {
                        planAutoGenerationService.autoGenerateAfterScheduleImported(scheduleId);
                    }
                } catch (RuntimeException energyException) {
                    energyImportWarning = trimMessage(energyException.getMessage());
                }
            }

            // 算法生成的 realtime_control 是一次性模拟快照，不应入库到 mpc_realtime_control 表。
            // 原因：其 timestamp 由 generate_plan.py 拼接为「计划日 + 当前时分秒」，
            // 若计划日为次日，control_date 会落在未来，污染滚动 MPC 的实时数据
            // （getLatest 按 control_date DESC 排序会把这条未来记录永远排在最前）。
            // 该数据已随 outputJson 存入 algorithm_task.algorithm_response_json 供查阅，
            // 真实控制指令只应来自 RealtimeMpcControlTask 每分钟的滚动计算。
            task.setAlgorithmResponseJson(outputJson);
            markTaskSuccess(task, scheduleId, "算法方案已生成并入库");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            RuntimeException wrapped = new BusinessException(500, "算法执行被中断，阶段: " + phase);
            markTaskFailed(task, wrapped);
        } catch (RuntimeException e) {
            markTaskFailed(task, withPhase(phase, e));
        } catch (Exception e) {
            RuntimeException wrapped = new BusinessException(500, "算法任务执行失败，阶段: " + phase + "，原因: " + e.getMessage());
            markTaskFailed(task, wrapped);
        }
    }

    @SuppressWarnings("unchecked")
    private void importAlgorithmEnergyPlan(Object energyPlanObject,
                                           Long scheduleId,
                                           Long taskId,
                                           Map<String, Object> dailyPlan) {
        if (scheduleId == null) {
            throw new BusinessException(500, "scheduleId is required before importing algorithm energy_plan");
        }
        if (energyPlanMapper == null || energyPlanDetailMapper == null) {
            throw new BusinessException(500, "EnergyPlan mapper is not initialized");
        }
        if (!(energyPlanObject instanceof Map<?, ?>)) {
            throw new BusinessException(400, "energy_plan must be a JSON object");
        }
        Map<String, Object> energyPlanJson = (Map<String, Object>) energyPlanObject;
        Object detailsObject = energyPlanJson.get("details");
        if (!(detailsObject instanceof List<?> details) || details.isEmpty()) {
            throw new BusinessException(400, "energy_plan.details must not be empty");
        }

        deleteExistingEnergyPlans(scheduleId);

        LocalDateTime planStart = parsePlanStart(energyPlanJson, dailyPlan);
        LocalDate planDate = planStart.toLocalDate();
        EnergyPlan plan = new EnergyPlan();
        plan.setTaskId(taskId);
        plan.setSourceScheduleId(scheduleId);
        plan.setPlanDate(planDate);
        plan.setStatus(planDate.isBefore(LocalDate.now()) ? TaskStatus.SUCCESS : TaskStatus.RUNNING);
        plan.setObjective(textValue(energyPlanJson, "objective", "cost_energy_carbon_executability"));
        plan.setElectricPriceMode(textValue(energyPlanJson, "electric_price_mode", "PEAK_VALLEY"));
        plan.setTimeInterval(intValue(energyPlanJson.get("time_interval"), 60));
        plan.setRemark("Generated by algorithm energy_plan from output.json");

        BigDecimal electricityCost = getDecimalAny(energyPlanJson, null, "electricity_cost", "electricityCost");
        BigDecimal steamCost = getDecimalAny(energyPlanJson, null, "steam_cost", "steamCost");
        BigDecimal totalCost = getDecimalAny(energyPlanJson, null, "total_energy_cost", "totalEnergyCost");
        if (electricityCost == null || steamCost == null || totalCost == null) {
            BigDecimal[] costs = calculateAlgorithmEnergyCosts((List<?>) details);
            electricityCost = electricityCost != null ? electricityCost : costs[0];
            steamCost = steamCost != null ? steamCost : costs[1];
            totalCost = totalCost != null ? totalCost : electricityCost.add(steamCost);
        }
        plan.setElectricityCost(scale(electricityCost, 2));
        plan.setSteamCost(scale(steamCost, 2));
        plan.setTotalEnergyCost(scale(totalCost, 2));
        energyPlanMapper.insert(plan);

        for (Object itemObject : details) {
            if (!(itemObject instanceof Map<?, ?>)) {
                continue;
            }
            Map<String, Object> item = (Map<String, Object>) itemObject;
            EnergyPlanDetail detail = new EnergyPlanDetail();
            detail.setPlanId(plan.getId());
            detail.setTimestamp(parseDetailTimestamp(item, planStart));
            detail.setEquipmentId(longValue(item.get("equipment_id"), 1L));
            detail.setOutput(scale(getDecimalAny(item, BigDecimal.ZERO, "output", "boiler_output", "boilerLoad", "boiler_load"), 6));
            detail.setElectricityConsumption(scale(getDecimalAny(item, BigDecimal.ZERO, "electricity_consumption", "electricity", "elec"), 6));
            detail.setSteamConsumption(scale(getDecimalAny(item, BigDecimal.ZERO, "steam_consumption", "steam"), 6));
            detail.setCarbonEmissionTco2(scale(getDecimalAny(item, BigDecimal.ZERO, "carbon_emission_tco2", "carbon", "co2"), 6));
            BigDecimal energyCost = getDecimalAny(item, null, "energy_cost", "energyCost");
            if (energyCost == null) {
                energyCost = detail.getElectricityConsumption().multiply(priceForHour(detail.getTimestamp().getHour()))
                        .add(detail.getSteamConsumption().multiply(new BigDecimal("180.00")));
            }
            detail.setEnergyCost(scale(energyCost, 2));
            energyPlanDetailMapper.insert(detail);
        }
    }

    private void deleteExistingEnergyPlans(Long scheduleId) {
        List<EnergyPlan> existingPlans = energyPlanMapper.selectList(
                new LambdaQueryWrapper<EnergyPlan>()
                        .eq(EnergyPlan::getSourceScheduleId, scheduleId)
        );
        if (existingPlans == null || existingPlans.isEmpty()) {
            return;
        }
        List<Long> planIds = existingPlans.stream()
                .map(EnergyPlan::getId)
                .filter(id -> id != null)
                .toList();
        if (!planIds.isEmpty()) {
            energyPlanDetailMapper.delete(
                    new LambdaQueryWrapper<EnergyPlanDetail>()
                            .in(EnergyPlanDetail::getPlanId, planIds)
            );
        }
        energyPlanMapper.delete(
                new LambdaQueryWrapper<EnergyPlan>()
                        .eq(EnergyPlan::getSourceScheduleId, scheduleId)
        );
    }

    private BigDecimal[] calculateAlgorithmEnergyCosts(List<?> details) {
        BigDecimal electricityCost = BigDecimal.ZERO;
        BigDecimal steamCost = BigDecimal.ZERO;
        for (Object itemObject : details) {
            if (!(itemObject instanceof Map<?, ?>)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> item = (Map<String, Object>) itemObject;
            LocalDateTime timestamp = parseDetailTimestamp(item, LocalDateTime.now().withMinute(0).withSecond(0).withNano(0));
            BigDecimal electricity = getDecimalAny(item, BigDecimal.ZERO, "electricity_consumption", "electricity", "elec");
            BigDecimal steam = getDecimalAny(item, BigDecimal.ZERO, "steam_consumption", "steam");
            electricityCost = electricityCost.add(electricity.multiply(priceForHour(timestamp.getHour())));
            steamCost = steamCost.add(steam.multiply(new BigDecimal("180.00")));
        }
        return new BigDecimal[]{electricityCost, steamCost};
    }

    private LocalDateTime parsePlanStart(Map<String, Object> energyPlanJson, Map<String, Object> dailyPlan) {
        Object timestamp = energyPlanJson.get("timestamp");
        if (timestamp == null && dailyPlan != null) {
            timestamp = dailyPlan.get("timestamp");
        }
        if (timestamp != null) {
            return parseDateTime(timestamp.toString());
        }
        Object planDate = energyPlanJson.get("plan_date");
        if (planDate != null) {
            return parseDate(planDate.toString(), "plan_date").atStartOfDay();
        }
        throw new BusinessException(400, "energy_plan timestamp or plan_date is required");
    }

    private LocalDateTime parseDetailTimestamp(Map<String, Object> item, LocalDateTime planStart) {
        Object timestamp = item.get("timestamp");
        if (timestamp != null) {
            return parseDateTime(timestamp.toString());
        }
        int hour = intValue(item.get("hour"), 0);
        return planStart.plusHours(hour);
    }

    private LocalDateTime parseDateTime(String value) {
        String text = value != null ? value.trim().replace('T', ' ') : "";
        if (text.length() > 19) {
            text = text.substring(0, 19);
        }
        return LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private BigDecimal priceForHour(int hour) {
        if ((hour >= 0 && hour < 8) || (hour >= 22 && hour < 24)) {
            return new BigDecimal("0.35");
        }
        if (hour >= 8 && hour < 22) {
            return new BigDecimal("1.05");
        }
        return new BigDecimal("0.65");
    }

    private int intValue(Object value, int defaultValue) {
        return value instanceof Number ? ((Number) value).intValue()
                : value != null ? Integer.parseInt(value.toString()) : defaultValue;
    }

    private Long longValue(Object value, Long defaultValue) {
        return value instanceof Number ? ((Number) value).longValue()
                : value != null ? Long.parseLong(value.toString()) : defaultValue;
    }

    private String textValue(Map<String, Object> source, String key, String defaultValue) {
        Object value = source != null ? source.get(key) : null;
        return value != null && !value.toString().isBlank() ? value.toString() : defaultValue;
    }

    @Override
    public Result<SchedulePlanVO> getPlanDetail(Long scheduleId) {
        ProductionSchedulePlan plan = schedulePlanMapper.selectById(scheduleId);
        if (plan == null) {
            throw new BusinessException(404, "排产方案不存在");
        }
        ensureScheduleStatusCorrect(plan);
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
        ensureScheduleStatusCorrect(plan);
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
                .peek(this::ensureScheduleStatusCorrect)
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
                .deadlineCompliance(deadlineCompliance(plan, details))
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

    private BigDecimal deadlineCompliance(ProductionSchedulePlan plan, List<ProductionScheduleDetail> details) {
        if (details == null || details.isEmpty()) {
            return BigDecimal.ZERO;
        }
        List<ProductionOrder> orders = plan != null && plan.getScheduleDate() != null
                ? selectAllOrdersForScheduleDate(plan.getScheduleDate())
                : List.of();
        if (!orders.isEmpty()) {
            BigDecimal[] cumulativeProduction = new BigDecimal[24];
            BigDecimal runningProduction = BigDecimal.ZERO;
            Map<Integer, BigDecimal> productionByHour = details.stream()
                    .collect(Collectors.toMap(
                            ProductionScheduleDetail::getHourIndex,
                            detail -> value(detail.getProduction()),
                            BigDecimal::add
                    ));
            for (int hour = 0; hour < 24; hour++) {
                runningProduction = runningProduction.add(productionByHour.getOrDefault(hour, BigDecimal.ZERO));
                cumulativeProduction[hour] = runningProduction;
            }

            BigDecimal required = BigDecimal.ZERO;
            long fulfilled = 0;
            for (ProductionOrder order : orders) {
                required = required.add(value(order.getPlannedQuantity()));
                int dueHour = Math.min(Math.max(order.getDueTime().getHour(), 0), 23);
                if (cumulativeProduction[dueHour].compareTo(required) >= 0) {
                    fulfilled++;
                }
            }
            return BigDecimal.valueOf(fulfilled).multiply(new BigDecimal("100"))
                    .divide(BigDecimal.valueOf(orders.size()), 2, RoundingMode.HALF_UP);
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

    private BigDecimal scale(BigDecimal value, int scale) {
        return value(value).setScale(scale, RoundingMode.HALF_UP);
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
        int requiredRows = 30 * 24 * 4;
        if (dataRows < requiredRows) {
            throw new BusinessException(400, "输入数据不足，需要至少30天数据；15分钟粒度不少于2880行，1分钟粒度建议不少于43200行，当前有效数据行数: " + dataRows);
        }
    }

    private void ensureSimpleOrder(LocalDate scheduleDate, ScheduleGenerateDTO dto) {
        String orderNo = "AUTO-" + scheduleDate;
        ProductionOrder order = new ProductionOrder();
        order.setOrderNo(orderNo);
        order.setProductName(hasText(dto.getProductName()) ? dto.getProductName().trim() : "次日轧钢计划");
        order.setProductSpec("AUTO");
        order.setPlannedQuantity(dto.getPlannedQuantity());
        order.setUnit("t");
        order.setDueTime(scheduleDate.atTime(23, 59, 59));
        order.setPriority(1);
        order.setStatus("PENDING");
        order.setDeleted(0);
        order.setRemark("前端录入产量自动生成");

        ProductionOrder existing = productionOrderMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProductionOrder>()
                        .eq(ProductionOrder::getOrderNo, orderNo)
                        .last("LIMIT 1")
        );
        if (existing == null) {
            productionOrderMapper.insert(order);
            return;
        }
        order.setId(existing.getId());
        productionOrderMapper.updateById(order);
    }

    private List<EnergyRealtimeData> selectEnergyRowsForAlgorithm(int trainingDays) {
        if (energyRealtimeDataMapper == null) {
            throw new BusinessException(500, "能源实时数据 Mapper 未初始化，无法从数据库组装算法输入");
        }
        int safeDays = Math.max(trainingDays, 1);
        int minRowsFor15Minute = safeDays * 24 * 4;
        int rowsFor1Minute = safeDays * 24 * 60;

        List<EnergyRealtimeData> mockRows = selectEnergyRowsBySource("MOCK_DEVICE", rowsFor1Minute);
        List<EnergyRealtimeData> rows = mockRows.size() >= rowsFor1Minute
                ? mockRows
                : selectLatestEnergyRows(rowsFor1Minute * 2);
        rows = deduplicateAndSortEnergyRows(rows);
        if (rows.size() > rowsFor1Minute) {
            rows = rows.subList(rows.size() - rowsFor1Minute, rows.size());
        }
        if (rows.size() < minRowsFor15Minute) {
            throw new BusinessException(400, "能源采集数据不足，至少需要 " + safeDays
                    + " 天历史数据；当前可用记录数: " + rows.size()
                    + "，请等待模拟装置补齐或调用 /api/energy/realtime/push 补录");
        }
        return rows;
    }

    private List<EnergyRealtimeData> selectEnergyRowsBySource(String source, int limit) {
        List<EnergyRealtimeData> rows = energyRealtimeDataMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EnergyRealtimeData>()
                        .eq(EnergyRealtimeData::getSource, source)
                        .orderByDesc(EnergyRealtimeData::getTimestamp)
                        .last("LIMIT " + Math.max(limit, 1))
        );
        return deduplicateAndSortEnergyRows(rows);
    }

    private List<EnergyRealtimeData> selectLatestEnergyRows(int limit) {
        List<EnergyRealtimeData> rows = energyRealtimeDataMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EnergyRealtimeData>()
                        .orderByDesc(EnergyRealtimeData::getTimestamp)
                        .last("LIMIT " + Math.max(limit, 1))
        );
        return deduplicateAndSortEnergyRows(rows);
    }

    private List<EnergyRealtimeData> deduplicateAndSortEnergyRows(List<EnergyRealtimeData> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Map<LocalDateTime, EnergyRealtimeData> byTimestamp = new LinkedHashMap<>();
        rows.stream()
                .filter(row -> row.getTimestamp() != null && row.getElectricityConsumption() != null)
                .sorted(Comparator.comparing(EnergyRealtimeData::getTimestamp))
                .forEach(row -> byTimestamp.putIfAbsent(row.getTimestamp(), row));
        return new ArrayList<>(byTimestamp.values());
    }

    private String buildEnergyCsv(List<EnergyRealtimeData> rows) {
        StringBuilder builder = new StringBuilder("timestamp,elec,steam\n");
        for (EnergyRealtimeData row : rows) {
            builder.append(row.getTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                    .append(',')
                    .append(row.getElectricityConsumption() != null ? row.getElectricityConsumption().toPlainString() : "")
                    .append(',')
                    .append(row.getSteamConsumption() != null ? row.getSteamConsumption().toPlainString() : "")
                    .append('\n');
        }
        return builder.toString();
    }

    private LocalDate resolveOrderScheduleDate(LocalDate requestedScheduleDate) {
        if (requestedScheduleDate != null) {
            return requestedScheduleDate;
        }
        ProductionOrder earliest = productionOrderMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProductionOrder>()
                        .ne(ProductionOrder::getStatus, "COMPLETED")
                        .orderByAsc(ProductionOrder::getDueTime)
                        .last("LIMIT 1")
        );
        return earliest != null && earliest.getDueTime() != null ? earliest.getDueTime().toLocalDate() : null;
    }

    private List<ProductionOrder> selectOrdersForScheduleDate(LocalDate scheduleDate) {
        LocalDateTime start = scheduleDate.atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        List<ProductionOrder> orders = productionOrderMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProductionOrder>()
                        .ge(ProductionOrder::getDueTime, start)
                        .lt(ProductionOrder::getDueTime, end)
                        .ne(ProductionOrder::getStatus, "COMPLETED")
                        .orderByAsc(ProductionOrder::getPriority)
                        .orderByAsc(ProductionOrder::getDueTime)
        );
        return orders != null ? orders : List.of();
    }

    private List<ProductionOrder> selectAllOrdersForScheduleDate(LocalDate scheduleDate) {
        LocalDateTime start = scheduleDate.atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        List<ProductionOrder> orders = productionOrderMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProductionOrder>()
                        .ge(ProductionOrder::getDueTime, start)
                        .lt(ProductionOrder::getDueTime, end)
                        .orderByAsc(ProductionOrder::getDueTime)
                        .orderByAsc(ProductionOrder::getPriority)
        );
        return orders != null ? orders : List.of();
    }

    private String buildOrdersCsv(List<ProductionOrder> orders) {
        StringBuilder builder = new StringBuilder("orderNo,plannedQuantity,dueTime,priority,status\n");
        for (ProductionOrder order : orders) {
            builder.append(csv(order.getOrderNo())).append(',')
                    .append(order.getPlannedQuantity() != null ? order.getPlannedQuantity().toPlainString() : "0")
                    .append(',')
                    .append(csv(order.getDueTime() != null
                            ? order.getDueTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                            : ""))
                    .append(',')
                    .append(order.getPriority() != null ? order.getPriority() : 1)
                    .append(',')
                    .append(csv(order.getStatus()))
                    .append('\n');
        }
        return builder.toString();
    }

    private String csv(String value) {
        String text = value != null ? value : "";
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private void markOrdersScheduled(List<ProductionOrder> orders, LocalDate scheduleDate) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        LocalDate today = LocalDate.now();
        boolean isPast = scheduleDate != null && scheduleDate.isBefore(today);
        for (ProductionOrder order : orders) {
            if (order.getId() == null || "COMPLETED".equals(order.getStatus())) {
                continue;
            }
            order.setStatus(isPast ? "COMPLETED" : "SCHEDULED");
            productionOrderMapper.updateById(order);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private boolean isMatlabRuntime() {
        return "matlab".equalsIgnoreCase(algorithmRuntime != null ? algorithmRuntime.trim() : "");
    }

    private Path resolveAlgorithmScript(Path algorithmDir) {
        String scriptName = isMatlabRuntime() ? "main.m" : algorithmScript;
        Path scriptPath = Paths.get(scriptName);
        return scriptPath.isAbsolute() ? scriptPath.normalize() : algorithmDir.resolve(scriptPath).normalize();
    }

    private ProcessBuilder buildAlgorithmProcessBuilder(Path algorithmDir,
                                                        Path scriptFile,
                                                        Path inputFile,
                                                        Path outputFile,
                                                        Path ordersFile) {
        if (isMatlabRuntime()) {
            String matlabScript = "addpath('" + escapeMatlabPath(algorithmDir) + "'); main('"
                    + escapeMatlabPath(inputFile) + "','"
                    + escapeMatlabPath(outputFile) + "')";
            return new ProcessBuilder(matlabCommand, "-batch", matlabScript);
        }
        if (ordersFile != null) {
            return new ProcessBuilder(
                    algorithmCommand,
                    scriptFile.toString(),
                    inputFile.toString(),
                    outputFile.toString(),
                    ordersFile.toString()
            );
        }
        return new ProcessBuilder(
                algorithmCommand,
                scriptFile.toString(),
                inputFile.toString(),
                outputFile.toString()
        );
    }

    private RuntimeException withPhase(String phase, RuntimeException e) {
        String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        if (message.contains("阶段:")) {
            return e;
        }
        int code = e instanceof BusinessException businessException ? businessException.getCode() : 500;
        return new BusinessException(code, "算法任务执行失败，阶段: " + phase + "，原因: " + message);
    }

    private String readTextLeniently(Path path) {
        if (!Files.exists(path)) {
            return "";
        }
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "日志读取失败: " + e.getMessage();
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
