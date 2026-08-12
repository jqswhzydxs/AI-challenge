package com.xq.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xq.common.constant.TaskStatus;
import com.xq.common.constant.TaskType;
import com.xq.common.exception.BusinessException;
import com.xq.common.result.PageResult;
import com.xq.common.result.Result;
import com.xq.mapper.AlgorithmTaskMapper;
import com.xq.mapper.EnergyRealtimeDataMapper;
import com.xq.mapper.MpcRealtimeControlMapper;
import com.xq.mapper.ProductionScheduleDetailMapper;
import com.xq.mapper.ProductionSchedulePlanMapper;
import com.xq.model.dto.PageQueryDTO;
import com.xq.model.entity.AlgorithmTask;
import com.xq.model.entity.EnergyRealtimeData;
import com.xq.model.entity.MpcRealtimeControl;
import com.xq.model.entity.ProductionScheduleDetail;
import com.xq.model.entity.ProductionSchedulePlan;
import com.xq.model.vo.RealtimeControlImportResultVO;
import com.xq.model.vo.RealtimeControlVO;
import com.xq.service.RealtimeControlService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 实时调控服务实现.
 *
 * @author XQ
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class RealtimeControlServiceImpl implements RealtimeControlService {

    private static final Logger log = LoggerFactory.getLogger(RealtimeControlServiceImpl.class);

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("H:mm:ss");
    private static final BigDecimal BOILER_MIN_LOAD_MW = new BigDecimal("20.00");
    private static final BigDecimal BOILER_MAX_LOAD_MW = new BigDecimal("80.00");
    private static final BigDecimal TURBINE_MIN_OUTPUT_MW = new BigDecimal("5.00");
    private static final BigDecimal TURBINE_MAX_OUTPUT_MW = new BigDecimal("30.00");

    private final AlgorithmTaskMapper algorithmTaskMapper;
    private final MpcRealtimeControlMapper mpcRealtimeControlMapper;
    private EnergyRealtimeDataMapper energyRealtimeDataMapper;
    private ProductionSchedulePlanMapper productionSchedulePlanMapper;
    private ProductionScheduleDetailMapper productionScheduleDetailMapper;

    @Autowired(required = false)
    public void setEnergyRealtimeDataMapper(EnergyRealtimeDataMapper energyRealtimeDataMapper) {
        this.energyRealtimeDataMapper = energyRealtimeDataMapper;
    }

    @Autowired(required = false)
    public void setProductionSchedulePlanMapper(ProductionSchedulePlanMapper productionSchedulePlanMapper) {
        this.productionSchedulePlanMapper = productionSchedulePlanMapper;
    }

    @Autowired(required = false)
    public void setProductionScheduleDetailMapper(ProductionScheduleDetailMapper productionScheduleDetailMapper) {
        this.productionScheduleDetailMapper = productionScheduleDetailMapper;
    }

    @Override
    public Result<RealtimeControlVO> getLatest() {
        RealtimeControlVO latest = mpcRealtimeControlMapper.selectLatestVO();
        if (latest == null) {
            throw new BusinessException(404, "暂无 MPC 实时调控数据，请先检查 mpc_realtime_control 表是否有数据");
        }
        return Result.ok(latest);
    }

    @Override
    public Result<PageResult<RealtimeControlVO>> getHistory(PageQueryDTO query) {
        if (query == null) {
            query = new PageQueryDTO();
        }
        int pageNum = query.getPageNum() != null && query.getPageNum() > 0 ? query.getPageNum() : 1;
        int pageSize = query.getPageSize() != null && query.getPageSize() > 0 ? query.getPageSize() : 10;
        String startDate = toDate(firstNonBlank(query.getStartTime(), query.getStartDate(), query.getDate()));
        String endDate = toDate(firstNonBlank(query.getEndTime(), query.getEndDate(), query.getDate()));
        long offset = (long) (pageNum - 1) * pageSize;

        long total = mpcRealtimeControlMapper.countHistory(startDate, endDate);
        List<RealtimeControlVO> records = mpcRealtimeControlMapper.selectHistoryVO(startDate, endDate, offset, pageSize);
        return Result.ok(PageResult.of(total, pageNum, pageSize, records));
    }

    @Override
    @Transactional
    public Result<RealtimeControlVO> runRealtimeMpcTick() {
        EnergyRealtimeData latestEnergy = selectLatestEnergyPoint();
        ProductionSchedulePlan plan = selectPlanForMpc(latestEnergy.getTimestamp().toLocalDate());
        ProductionScheduleDetail detail = selectDetailForMpc(plan, latestEnergy.getTimestamp().getHour());
        MpcRealtimeControl previous = selectPreviousControl();

        BigDecimal plannedElecPerMinute = value(detail.getElecForecast())
                .divide(new BigDecimal("60"), 6, RoundingMode.HALF_UP);
        BigDecimal actualElec = value(latestEnergy.getElectricityConsumption());
        BigDecimal actualSteam = value(latestEnergy.getSteamConsumption());
        BigDecimal elecDeviationRate = clampDeviation(percentDeviation(actualElec, plannedElecPerMinute));
        BigDecimal steamPlan = plannedElecPerMinute.multiply(new BigDecimal("0.0052")).add(new BigDecimal("0.8"));
        BigDecimal steamDeviationRate = clampDeviation(percentDeviation(actualSteam, steamPlan));

        // 锅炉/汽机目标出力改为基于实时电量(actualElec)浮动。
        // 原 24+production×2.10 依赖排产 production(0.15-0.95，基线 fallback=100)，
        // 且 elecDeviationRate 因 elec_forecast(1.99) 与 actualElec(2.95/分钟) 量级不匹配常达数千%，
        // 使 elecCorrection 爆炸把 boilerTarget 推到上限被 clamp 钉死。
        // 现基于 actualElec(分钟 kWh，量级稳定) 并 clamp 偏差率，保证落在出力区间内浮动。
        BigDecimal boilerBase = new BigDecimal("30.00").add(actualElec.multiply(new BigDecimal("8.00")));
        BigDecimal turbineBase = new BigDecimal("10.00").add(actualElec.multiply(new BigDecimal("3.00")));
        BigDecimal elecCorrection = elecDeviationRate.multiply(new BigDecimal("0.12"));
        BigDecimal steamCorrection = steamDeviationRate.multiply(new BigDecimal("0.08"));

        BigDecimal boilerTarget = boilerBase.add(steamCorrection).add(elecCorrection);
        BigDecimal turbineTarget = turbineBase.add(elecCorrection.multiply(new BigDecimal("0.35")));
        if (previous != null) {
            boilerTarget = applyRamp(previous.getBoilerLoadMw(), boilerTarget, new BigDecimal("5.00"));
            turbineTarget = applyRamp(previous.getTurbineOutputMw(), turbineTarget, new BigDecimal("2.00"));
        }

        MpcRealtimeControl control = new MpcRealtimeControl();
        control.setTaskId(null);
        control.setControlDate(latestEnergy.getTimestamp().toLocalDate());
        control.setControlTime(latestEnergy.getTimestamp().toLocalTime().withSecond(0).withNano(0).format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        control.setRawTimestamp(latestEnergy.getTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        control.setBoilerLoadMw(clamp(boilerTarget, BOILER_MIN_LOAD_MW, BOILER_MAX_LOAD_MW));
        control.setTurbineOutputMw(clamp(turbineTarget, TURBINE_MIN_OUTPUT_MW, TURBINE_MAX_OUTPUT_MW));
        // 外购电 = 厂用电功率(actualElec×60, kWh/分钟→kW) − 汽机自发电抵消(turbineOutput MW→kW, 仅 5‰ 抵消厂用电)
        // 原公式 actualElec − turbine×0.50 单位不匹配(分钟kWh vs MW)恒为负被 max(0) 钳死，现修正为正值浮动。
        control.setGridPurchaseKwh(actualElec.multiply(new BigDecimal("60"))
                .subtract(control.getTurbineOutputMw().multiply(new BigDecimal("1000")).multiply(new BigDecimal("0.005")))
                .max(BigDecimal.ZERO).setScale(6, RoundingMode.HALF_UP));
        // 功率因数目标随用电偏差微调(±0.05)，避免恒定 0.95；clamp 到 0.90-0.99
        BigDecimal powerFactorTarget = new BigDecimal("0.95").add(elecDeviationRate.multiply(new BigDecimal("0.0005")));
        control.setPowerFactorTarget(clamp(powerFactorTarget, new BigDecimal("0.90"), new BigDecimal("0.99")));
        control.setElecNext5minKwh(actualElec.multiply(new BigDecimal("5")).add(elecDeviationRate.multiply(new BigDecimal("0.02"))).max(BigDecimal.ZERO).setScale(6, RoundingMode.HALF_UP));
        control.setSteamNext5minT(actualSteam.multiply(new BigDecimal("5")).add(steamDeviationRate.multiply(new BigDecimal("0.01"))).max(BigDecimal.ZERO).setScale(6, RoundingMode.HALF_UP));
        control.setSourceFileName("realtime_mpc_tick");
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("mode", "ROLLING_MPC");
        raw.put("energyDataId", latestEnergy.getId());
        raw.put("scheduleId", plan.getId());
        raw.put("scheduleDetailId", detail.getId());
        raw.put("hourIndex", detail.getHourIndex());
        raw.put("actualElec", actualElec);
        raw.put("plannedElecPerMinute", plannedElecPerMinute);
        raw.put("elecDeviationRate", elecDeviationRate);
        raw.put("actualSteam", actualSteam);
        raw.put("plannedSteamPerMinute", steamPlan);
        raw.put("steamDeviationRate", steamDeviationRate);
        raw.put("previousControlId", previous != null ? previous.getId() : null);
        control.setRawJson(JSON.toJSONString(raw));
        validateControlBounds(control);

        MpcRealtimeControl existing = mpcRealtimeControlMapper.selectOne(
                new LambdaQueryWrapper<MpcRealtimeControl>()
                        .eq(MpcRealtimeControl::getControlDate, control.getControlDate())
                        .eq(MpcRealtimeControl::getControlTime, control.getControlTime())
                        .last("LIMIT 1")
        );
        if (existing == null) {
            mpcRealtimeControlMapper.insert(control);
        } else {
            control.setId(existing.getId());
            mpcRealtimeControlMapper.updateById(control);
        }

        return getLatest();
    }

    private EnergyRealtimeData selectLatestEnergyPoint() {
        if (energyRealtimeDataMapper == null) {
            throw new BusinessException(500, "能源实时数据 Mapper 未初始化，无法执行滚动 MPC");
        }
        EnergyRealtimeData latest = energyRealtimeDataMapper.selectOne(
                new LambdaQueryWrapper<EnergyRealtimeData>()
                        .orderByDesc(EnergyRealtimeData::getTimestamp)
                        .orderByDesc(EnergyRealtimeData::getId)
                        .last("LIMIT 1")
        );
        if (latest == null) {
            throw new BusinessException(404, "暂无能源实时采集数据，无法执行滚动 MPC");
        }
        return latest;
    }

    private ProductionSchedulePlan selectPlanForMpc(LocalDate controlDate) {
        if (productionSchedulePlanMapper == null) {
            log.warn("排产方案 Mapper 未初始化，滚动 MPC 使用默认基线运行");
            return defaultBaselinePlan(controlDate);
        }
        // 1. 优先取当天的排产方案
        ProductionSchedulePlan sameDay = productionSchedulePlanMapper.selectOne(
                new LambdaQueryWrapper<ProductionSchedulePlan>()
                        .eq(ProductionSchedulePlan::getScheduleDate, controlDate)
                        .eq(ProductionSchedulePlan::getStatus, TaskStatus.SUCCESS)
                        .orderByDesc(ProductionSchedulePlan::getCreateTime)
                        .last("LIMIT 1")
        );
        if (sameDay != null) {
            return sameDay;
        }
        // 2. 当天没有则取「当前生效的计划」：schedule_date <= 今天 且 SUCCESS 里 schedule_date 最大的
        //    语义：用不超过今天的、最近一份排产方案来滚动调控，避免误用「次日排产方案」调今天设备
        ProductionSchedulePlan active = productionSchedulePlanMapper.selectOne(
                new LambdaQueryWrapper<ProductionSchedulePlan>()
                        .le(ProductionSchedulePlan::getScheduleDate, controlDate)
                        .eq(ProductionSchedulePlan::getStatus, TaskStatus.SUCCESS)
                        .orderByDesc(ProductionSchedulePlan::getScheduleDate)
                        .orderByDesc(ProductionSchedulePlan::getCreateTime)
                        .last("LIMIT 1")
        );
        if (active != null) {
            return active;
        }
        // 3. 任何方案都没有时（如首次启动），用内置默认基线，避免 MPC 停摆
        log.warn("未找到 schedule_date <= {} 的成功排产方案，滚动 MPC 使用默认基线运行", controlDate);
        return defaultBaselinePlan(controlDate);
    }

    /**
     * 构造内存中的默认基线排产方案（不入库）.
     * <p>
     * 用于系统首次启动或无任何排产方案时，保证 MPC 滚动调控不中断。
     * production=100 t/h、EC=14.00 kWh/t 与算法 BASE_ELEC_COEFF / MIN_PRODUCTION 对齐。
     * </p>
     */
    private ProductionSchedulePlan defaultBaselinePlan(LocalDate controlDate) {
        ProductionSchedulePlan plan = new ProductionSchedulePlan();
        plan.setId(-1L);
        plan.setScheduleDate(controlDate);
        plan.setScheduleName("默认基线方案（无排产方案时使用）");
        plan.setPlanStartTime(controlDate.atStartOfDay());
        plan.setPlanHorizon(24);
        plan.setStatus(TaskStatus.SUCCESS);
        plan.setElecCoefficient(new BigDecimal("14.00"));
        return plan;
    }

    private ProductionScheduleDetail selectDetailForMpc(ProductionSchedulePlan plan, int hour) {
        int safeHour = Math.min(Math.max(hour, 0), 23);
        // 默认基线方案（id=-1）不入库，直接构造内存明细
        if (plan.getId() == null || plan.getId() < 0) {
            return defaultBaselineDetail(plan, safeHour);
        }
        if (productionScheduleDetailMapper == null) {
            log.warn("排产明细 Mapper 未初始化，滚动 MPC 使用默认基线明细");
            return defaultBaselineDetail(plan, safeHour);
        }
        ProductionScheduleDetail detail = productionScheduleDetailMapper.selectOne(
                new LambdaQueryWrapper<ProductionScheduleDetail>()
                        .eq(ProductionScheduleDetail::getScheduleId, plan.getId())
                        .eq(ProductionScheduleDetail::getHourIndex, safeHour)
                        .last("LIMIT 1")
        );
        if (detail != null) {
            return detail;
        }
        detail = productionScheduleDetailMapper.selectOne(
                new LambdaQueryWrapper<ProductionScheduleDetail>()
                        .eq(ProductionScheduleDetail::getScheduleId, plan.getId())
                        .orderByAsc(ProductionScheduleDetail::getHourIndex)
                        .last("LIMIT 1")
        );
        if (detail == null) {
            log.warn("排产方案 {} 缺少小时明细，滚动 MPC 使用默认基线明细", plan.getId());
            return defaultBaselineDetail(plan, safeHour);
        }
        return detail;
    }

    /**
     * 构造内存中的默认基线小时明细（不入库）.
     * production=100 t/h、elecForecast=production*EC，与算法 MIN_PRODUCTION 对齐。
     */
    private ProductionScheduleDetail defaultBaselineDetail(ProductionSchedulePlan plan, int hour) {
        ProductionScheduleDetail detail = new ProductionScheduleDetail();
        detail.setId(-1L);
        detail.setScheduleId(plan.getId());
        detail.setHourIndex(hour);
        LocalDateTime start = plan.getPlanStartTime() != null
                ? plan.getPlanStartTime().plusHours(hour)
                : plan.getScheduleDate().atStartOfDay().plusHours(hour);
        detail.setStartTime(start);
        detail.setEndTime(start.plusHours(1));
        BigDecimal production = new BigDecimal("100.00");
        detail.setProduction(production);
        detail.setDemand(production);
        BigDecimal ec = plan.getElecCoefficient() != null
                ? plan.getElecCoefficient()
                : new BigDecimal("14.00");
        detail.setElecForecast(production.multiply(ec));
        return detail;
    }

    private MpcRealtimeControl selectPreviousControl() {
        return mpcRealtimeControlMapper.selectOne(
                new LambdaQueryWrapper<MpcRealtimeControl>()
                        .orderByDesc(MpcRealtimeControl::getControlDate)
                        .orderByDesc(MpcRealtimeControl::getControlTime)
                        .orderByDesc(MpcRealtimeControl::getId)
                        .last("LIMIT 1")
        );
    }

    private BigDecimal percentDeviation(BigDecimal actual, BigDecimal planned) {
        if (planned == null || planned.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return actual.subtract(planned)
                .multiply(new BigDecimal("100"))
                .divide(planned.abs(), 6, RoundingMode.HALF_UP);
    }

    /** 将偏差率限制在 ±100%，避免 elec_forecast 与 actualElec 量级不匹配时偏差爆炸把修正项推飞。 */
    private BigDecimal clampDeviation(BigDecimal rate) {
        if (rate == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal lower = new BigDecimal("-100");
        BigDecimal upper = new BigDecimal("100");
        if (rate.compareTo(lower) < 0) {
            return lower;
        }
        if (rate.compareTo(upper) > 0) {
            return upper;
        }
        return rate;
    }

    private BigDecimal applyRamp(BigDecimal previous, BigDecimal target, BigDecimal maxStep) {
        if (previous == null) {
            return target;
        }
        BigDecimal delta = target.subtract(previous);
        if (delta.compareTo(maxStep) > 0) {
            return previous.add(maxStep);
        }
        if (delta.compareTo(maxStep.negate()) < 0) {
            return previous.subtract(maxStep);
        }
        return target;
    }

    private BigDecimal clamp(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (value.compareTo(min) < 0) {
            return min.setScale(6, RoundingMode.HALF_UP);
        }
        if (value.compareTo(max) > 0) {
            return max.setScale(6, RoundingMode.HALF_UP);
        }
        return value.setScale(6, RoundingMode.HALF_UP);
    }

    private BigDecimal value(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    @Override
    @Transactional
    public Result<RealtimeControlImportResultVO> importRealtimeControl(Object realtimeControlJson,
                                                                       String controlDate,
                                                                       String sourceFileName) {
        if (realtimeControlJson == null) {
            throw new BusinessException(400, "请求体不能为空，请粘贴 realtime_control.json 内容");
        }

        LocalDate defaultControlDate = parseControlDate(controlDate, null);
        List<Map<String, Object>> records = extractRecords(realtimeControlJson);
        if (records.isEmpty()) {
            throw new BusinessException(400, "未找到实时调控记录，请检查 JSON 是否包含 timestamp/control/forecast");
        }

        AlgorithmTask task = new AlgorithmTask();
        task.setTaskType(TaskType.REALTIME_MPC);
        task.setStatus(TaskStatus.SUCCESS);
        task.setProgress(100);
        task.setRetryCount(0);
        task.setAlgorithmName("MPC_REALTIME_CONTROL");
        task.setResultFileName(sourceFileName != null && !sourceFileName.trim().isEmpty()
                ? sourceFileName.trim()
                : "realtime_control.json");
        task.setAlgorithmResponseJson(JSON.toJSONString(realtimeControlJson));
        task.setStartTime(LocalDateTime.now());
        task.setFinishTime(LocalDateTime.now());
        algorithmTaskMapper.insert(task);

        int insertedCount = 0;
        int updatedCount = 0;
        Long latestControlId = null;
        for (Map<String, Object> record : records) {
            MpcRealtimeControl control = toEntity(record, defaultControlDate, task.getId(), task.getResultFileName());
            MpcRealtimeControl existing = mpcRealtimeControlMapper.selectOne(
                    new LambdaQueryWrapper<MpcRealtimeControl>()
                            .eq(MpcRealtimeControl::getControlDate, control.getControlDate())
                            .eq(MpcRealtimeControl::getControlTime, control.getControlTime())
                            .last("LIMIT 1")
            );
            if (existing == null) {
                mpcRealtimeControlMapper.insert(control);
                insertedCount++;
            } else {
                control.setId(existing.getId());
                mpcRealtimeControlMapper.updateById(control);
                updatedCount++;
            }
            latestControlId = control.getId();
        }

        task.setResultId(latestControlId);
        algorithmTaskMapper.updateById(task);

        RealtimeControlImportResultVO vo = RealtimeControlImportResultVO.builder()
                .taskId(task.getId())
                .success(true)
                .latestControlId(latestControlId)
                .controlId(latestControlId)
                .insertedCount(insertedCount)
                .updatedCount(updatedCount)
                .totalCount(records.size())
                .build();
        return Result.ok("导入成功", vo);
    }

    private String toDate(String value) {
        if (value == null || value.trim().length() < 10) {
            return null;
        }
        return value.trim().substring(0, 10);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractRecords(Object source) {
        if (source instanceof List<?> list) {
            return toRecordList(list);
        }
        if (!(source instanceof Map<?, ?> map)) {
            throw new BusinessException(400, "JSON 根节点必须是对象或数组");
        }

        Map<String, Object> root = (Map<String, Object>) map;
        Object nested = firstNonNull(root, "data", "records", "items", "controls", "result");
        if (nested instanceof List<?> list) {
            return toRecordList(list);
        }
        if (nested instanceof Map<?, ?> nestedMap && !root.containsKey("timestamp")) {
            return extractRecords(nestedMap);
        }
        if (root.containsKey("timestamp")) {
            return List.of(root);
        }
        throw new BusinessException(400, "未找到 timestamp 字段，或 data/records/items 中没有记录");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> toRecordList(List<?> list) {
        List<Map<String, Object>> records = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                throw new BusinessException(400, "实时调控数组中的每一项都必须是 JSON 对象");
            }
            records.add((Map<String, Object>) map);
        }
        return records;
    }

    @SuppressWarnings("unchecked")
    private MpcRealtimeControl toEntity(Map<String, Object> record,
                                        LocalDate defaultControlDate,
                                        Long taskId,
                                        String sourceFileName) {
        Object timestampValue = record.get("timestamp");
        if (timestampValue == null || timestampValue.toString().trim().isEmpty()) {
            throw new BusinessException(400, "缺少字段: timestamp");
        }

        String rawTimestamp = timestampValue.toString().trim();
        LocalDate controlDate = parseControlDate(toText(firstNonNull(record, "controlDate", "control_date", "date")), rawTimestamp);
        if (controlDate == null) {
            controlDate = defaultControlDate != null ? defaultControlDate : LocalDate.now();
        }
        String controlTime = parseControlTime(rawTimestamp);

        Map<String, Object> control = record.get("control") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : record;
        Map<String, Object> forecast = record.get("forecast") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : record;

        MpcRealtimeControl entity = new MpcRealtimeControl();
        entity.setTaskId(taskId);
        entity.setControlDate(controlDate);
        entity.setControlTime(controlTime);
        entity.setRawTimestamp(rawTimestamp);
        entity.setBoilerLoadMw(getRequiredDecimal(control, "boiler_load", "boilerLoad", "boilerLoadMw"));
        entity.setTurbineOutputMw(getRequiredDecimal(control, "turbine_output", "turbineOutput", "turbineOutputMw"));
        entity.setGridPurchaseKwh(getRequiredDecimal(control, "grid_purchase", "gridPurchase", "gridPurchaseKwh"));
        entity.setPowerFactorTarget(getRequiredDecimal(control, "power_factor_target", "powerFactorTarget"));
        entity.setElecNext5minKwh(getRequiredDecimal(forecast, "elec_next_5min", "elecNext5min", "elecNext5minKwh"));
        entity.setSteamNext5minT(getRequiredDecimal(forecast, "steam_next_5min", "steamNext5min", "steamNext5minT"));
        entity.setSourceFileName(sourceFileName);
        entity.setRawJson(JSON.toJSONString(record));
        validateControlBounds(entity);
        return entity;
    }

    private void validateControlBounds(MpcRealtimeControl entity) {
        if (entity.getBoilerLoadMw().compareTo(BOILER_MIN_LOAD_MW) < 0
                || entity.getBoilerLoadMw().compareTo(BOILER_MAX_LOAD_MW) > 0) {
            throw new BusinessException(400, "锅炉负荷必须在 20-80 MW 范围内");
        }
        if (entity.getTurbineOutputMw().compareTo(TURBINE_MIN_OUTPUT_MW) < 0
                || entity.getTurbineOutputMw().compareTo(TURBINE_MAX_OUTPUT_MW) > 0) {
            throw new BusinessException(400, "汽机出力必须在 5-30 MW 范围内");
        }
    }

    private LocalDate parseControlDate(String controlDate, String timestamp) {
        String value = controlDate;
        String timestampText = timestamp != null ? timestamp.trim() : "";
        if ((value == null || value.trim().isEmpty()) && timestampText.matches("\\d{4}-\\d{2}-\\d{2}.*")) {
            value = timestampText.substring(0, 10);
        }
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            String text = value.trim();
            if (text.matches("\\d{4}-\\d{2}-\\d{2}.*") && text.length() > 10) {
                text = text.substring(0, 10);
            }
            return LocalDate.parse(text);
        } catch (DateTimeParseException | IndexOutOfBoundsException e) {
            throw new BusinessException(400, "controlDate 格式错误，应为 yyyy-MM-dd，例如 2026-07-17");
        }
    }

    private String parseControlTime(String rawTimestamp) {
        String value = rawTimestamp != null ? rawTimestamp.trim() : "";
        if (value.matches("\\d{4}-\\d{2}-\\d{2}[ T].*") && value.length() >= 19) {
            value = value.substring(11, 19);
        }
        try {
            return LocalTime.parse(value, TIME_FORMATTER).format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        } catch (DateTimeParseException e) {
            throw new BusinessException(400, "timestamp 格式错误，应为 HH:mm:ss 或 yyyy-MM-dd HH:mm:ss");
        }
    }

    private BigDecimal getRequiredDecimal(Map<String, Object> source, String... keys) {
        Object value = firstNonNull(source, keys);
        if (value == null || value.toString().trim().isEmpty()) {
            throw new BusinessException(400, "缺少字段: " + keys[0]);
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "字段格式错误，必须是数字: " + keys[0]);
        }
    }

    private Object firstNonNull(Map<String, Object> source, String... keys) {
        if (source == null) {
            return null;
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private String toText(Object value) {
        return value == null ? null : value.toString();
    }
}
