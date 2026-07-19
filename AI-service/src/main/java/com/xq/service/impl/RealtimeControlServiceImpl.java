package com.xq.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xq.common.constant.TaskStatus;
import com.xq.common.constant.TaskType;
import com.xq.common.exception.BusinessException;
import com.xq.common.result.PageResult;
import com.xq.common.result.Result;
import com.xq.mapper.AlgorithmTaskMapper;
import com.xq.mapper.MpcRealtimeControlMapper;
import com.xq.model.dto.PageQueryDTO;
import com.xq.model.entity.AlgorithmTask;
import com.xq.model.entity.MpcRealtimeControl;
import com.xq.model.vo.RealtimeControlImportResultVO;
import com.xq.model.vo.RealtimeControlVO;
import com.xq.service.RealtimeControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
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

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("H:mm:ss");

    private final AlgorithmTaskMapper algorithmTaskMapper;
    private final MpcRealtimeControlMapper mpcRealtimeControlMapper;

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
                .latestControlId(latestControlId)
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
        return entity;
    }

    private LocalDate parseControlDate(String controlDate, String timestamp) {
        String value = controlDate;
        if ((value == null || value.trim().isEmpty()) && timestamp != null && timestamp.trim().length() >= 10) {
            value = timestamp.trim().substring(0, 10);
        }
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            String text = value.trim();
            if (text.length() > 10) {
                text = text.substring(0, 10);
            }
            return LocalDate.parse(text);
        } catch (DateTimeParseException | IndexOutOfBoundsException e) {
            throw new BusinessException(400, "controlDate 格式错误，应为 yyyy-MM-dd，例如 2026-07-17");
        }
    }

    private String parseControlTime(String rawTimestamp) {
        String value = rawTimestamp;
        if (value.length() >= 19) {
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
