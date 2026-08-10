package com.xq.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xq.common.exception.BusinessException;
import com.xq.common.result.Result;
import com.xq.mapper.EnergyRealtimeDataMapper;
import com.xq.model.dto.EnergyRealtimePushDTO;
import com.xq.model.dto.PageQueryDTO;
import com.xq.model.entity.EnergyRealtimeData;
import com.xq.model.vo.EnergyRealtimePushResultVO;
import com.xq.model.vo.RealtimeDataPointVO;
import com.xq.model.vo.RealtimeDataVO;
import com.xq.service.EnergyDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EnergyDataServiceImpl implements EnergyDataService {

    private static final String MOCK_SOURCE = "MOCK_DEVICE";
    private static final String REPLAY_MODE = "replay";
    private static final DateTimeFormatter NORMAL_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final EnergyRealtimeDataMapper energyRealtimeDataMapper;
    private volatile List<ReplayPoint> replayPoints;

    @Value("${mock-device.mode:replay}")
    private String mockDeviceMode;

    @Value("${mock-device.replay-file:data/algorithm/steel_data_cleaned.csv}")
    private String mockDeviceReplayFile;

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

    @Override
    public Result<EnergyRealtimePushResultVO> pushRealtime(EnergyRealtimePushDTO dto) {
        if (dto == null) {
            throw new BusinessException(400, "采集数据不能为空");
        }
        EnergyRealtimeData entity = toEntity(dto);
        EnergyRealtimeData existing = energyRealtimeDataMapper.selectOne(
                new LambdaQueryWrapper<EnergyRealtimeData>()
                        .eq(EnergyRealtimeData::getTimestamp, entity.getTimestamp())
                        .eq(EnergyRealtimeData::getSource, entity.getSource())
                        .last("LIMIT 1")
        );
        boolean inserted = existing == null;
        if (inserted) {
            energyRealtimeDataMapper.insert(entity);
        } else {
            entity.setId(existing.getId());
            energyRealtimeDataMapper.updateById(entity);
        }
        return Result.ok("采集数据已入库", EnergyRealtimePushResultVO.builder()
                .dataId(entity.getId())
                .timestamp(entity.getTimestamp().format(NORMAL_TIME_FORMATTER))
                .source(entity.getSource())
                .inserted(inserted)
                .build());
    }

    @Override
    public Result<EnergyRealtimePushResultVO> pushMockRealtime() {
        LocalDateTime timestamp = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        return pushRealtime(mockPoint(timestamp));
    }

    @Override
    public int backfillMockRealtimeIfNeeded(int days) {
        int safeDays = Math.max(days, 1);
        LocalDateTime end = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime start = end.minusDays(safeDays).plusMinutes(1);
        Long count = energyRealtimeDataMapper.selectCount(
                new LambdaQueryWrapper<EnergyRealtimeData>()
                        .eq(EnergyRealtimeData::getSource, MOCK_SOURCE)
                        .ge(EnergyRealtimeData::getTimestamp, start)
                        .le(EnergyRealtimeData::getTimestamp, end)
        );
        int requiredRows = safeDays * 24 * 60;
        if (count != null && count >= requiredRows) {
            return 0;
        }
        int inserted = 0;
        for (LocalDateTime cursor = start; !cursor.isAfter(end); cursor = cursor.plusMinutes(1)) {
            EnergyRealtimePushResultVO result = pushRealtime(mockPoint(cursor)).getData();
            if (Boolean.TRUE.equals(result.getInserted())) {
                inserted++;
            }
        }
        return inserted;
    }

    private EnergyRealtimeData toEntity(EnergyRealtimePushDTO dto) {
        LocalDateTime timestamp = parseTimestamp(dto.getTimestamp());
        BigDecimal electricity = dto.getElectricityConsumption();
        if (electricity == null || electricity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "electricityConsumption/elec 必须是正数");
        }
        BigDecimal steam = dto.getSteamConsumption() != null
                ? dto.getSteamConsumption()
                : electricity.multiply(new BigDecimal("0.005")).add(new BigDecimal("0.5"));
        BigDecimal carbon = dto.getCarbonEmissionTco2() != null
                ? dto.getCarbonEmissionTco2()
                : electricity.multiply(new BigDecimal("0.00057"));

        EnergyRealtimeData entity = new EnergyRealtimeData();
        entity.setTimestamp(timestamp);
        entity.setRawTimestamp(timestamp.format(NORMAL_TIME_FORMATTER));
        entity.setElectricityConsumption(electricity.setScale(4, RoundingMode.HALF_UP));
        entity.setSteamConsumption(steam.setScale(4, RoundingMode.HALF_UP));
        entity.setCarbonEmissionTco2(carbon.setScale(6, RoundingMode.HALF_UP));
        entity.setLaggingReactivePowerKvarh(defaultDecimal(dto.getLaggingReactivePowerKvarh(), electricity.multiply(new BigDecimal("0.18"))));
        entity.setLeadingReactivePowerKvarh(defaultDecimal(dto.getLeadingReactivePowerKvarh(), electricity.multiply(new BigDecimal("0.04"))));
        entity.setLaggingPowerFactor(defaultDecimal(dto.getLaggingPowerFactor(), new BigDecimal("0.9600")));
        entity.setLeadingPowerFactor(defaultDecimal(dto.getLeadingPowerFactor(), new BigDecimal("0.9800")));
        entity.setNsm(timestamp.toLocalTime().toSecondOfDay());
        entity.setWeekStatus(isWeekend(timestamp.getDayOfWeek()) ? "WEEKEND" : "WORKDAY");
        entity.setDayOfWeek(timestamp.getDayOfWeek().name());
        entity.setLoadType(hasText(dto.getLoadType()) ? dto.getLoadType().trim() : loadType(timestamp));
        entity.setDataQuality(hasText(dto.getDataQuality()) ? dto.getDataQuality().trim() : "NORMAL");
        entity.setSource(hasText(dto.getSource()) ? dto.getSource().trim() : MOCK_SOURCE);
        return entity;
    }

    private EnergyRealtimePushDTO mockPoint(LocalDateTime timestamp) {
        EnergyRealtimePushDTO replay = replayPoint(timestamp);
        if (replay != null) {
            return replay;
        }

        int minuteOfDay = timestamp.getHour() * 60 + timestamp.getMinute();
        double dayWave = Math.sin((minuteOfDay / 1440.0) * Math.PI * 2 - Math.PI / 2);
        double hourWave = Math.sin((minuteOfDay / 60.0) * Math.PI * 2);
        double shiftFactor = timestamp.getHour() >= 8 && timestamp.getHour() <= 22 ? 1.0 : 0.72;
        double weekdayFactor = isWeekend(timestamp.getDayOfWeek()) ? 0.88 : 1.0;
        BigDecimal electricity = BigDecimal.valueOf((720 + dayWave * 160 + hourWave * 28) * shiftFactor * weekdayFactor)
                .setScale(4, RoundingMode.HALF_UP);

        EnergyRealtimePushDTO dto = new EnergyRealtimePushDTO();
        dto.setTimestamp(timestamp.format(NORMAL_TIME_FORMATTER));
        dto.setElectricityConsumption(electricity);
        dto.setSteamConsumption(electricity.multiply(new BigDecimal("0.0052")).add(new BigDecimal("0.8")).setScale(4, RoundingMode.HALF_UP));
        dto.setCarbonEmissionTco2(electricity.multiply(new BigDecimal("0.00057")).setScale(6, RoundingMode.HALF_UP));
        dto.setLaggingPowerFactor(new BigDecimal("0.9600"));
        dto.setLeadingPowerFactor(new BigDecimal("0.9800"));
        dto.setLoadType(loadType(timestamp));
        dto.setDataQuality("SYNTHETIC_FALLBACK");
        dto.setSource(MOCK_SOURCE);
        return dto;
    }

    private EnergyRealtimePushDTO replayPoint(LocalDateTime timestamp) {
        if (!REPLAY_MODE.equalsIgnoreCase(mockDeviceMode != null ? mockDeviceMode.trim() : "")) {
            return null;
        }
        List<ReplayPoint> points = loadReplayPoints();
        if (points.isEmpty()) {
            return null;
        }
        long minuteIndex = timestamp.toEpochSecond(ZoneOffset.ofHours(8)) / 60;
        ReplayPoint point = points.get(Math.floorMod(minuteIndex, points.size()));

        EnergyRealtimePushDTO dto = new EnergyRealtimePushDTO();
        dto.setTimestamp(timestamp.format(NORMAL_TIME_FORMATTER));
        dto.setElectricityConsumption(point.electricity);
        dto.setSteamConsumption(point.steam);
        dto.setCarbonEmissionTco2(point.carbon);
        dto.setLaggingReactivePowerKvarh(point.laggingReactivePower);
        dto.setLeadingReactivePowerKvarh(point.leadingReactivePower);
        dto.setLaggingPowerFactor(point.laggingPowerFactor);
        dto.setLeadingPowerFactor(point.leadingPowerFactor);
        dto.setLoadType(hasText(point.loadType) ? point.loadType : loadType(timestamp));
        dto.setDataQuality("REPLAY_SAMPLE");
        dto.setSource(MOCK_SOURCE);
        return dto;
    }

    private List<ReplayPoint> loadReplayPoints() {
        List<ReplayPoint> cached = replayPoints;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (replayPoints != null) {
                return replayPoints;
            }
            replayPoints = readReplayPoints();
            return replayPoints;
        }
    }

    private List<ReplayPoint> readReplayPoints() {
        Path path = Paths.get(mockDeviceReplayFile).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            return List.of();
        }
        List<ReplayPoint> points = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (!hasText(headerLine)) {
                return List.of();
            }
            Map<String, Integer> header = replayHeader(headerLine);
            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = line.split(",", -1);
                BigDecimal electricity = csvDecimal(values, header, "elec", "usage_kwh", "power", "power_kw");
                if (electricity == null || electricity.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                ReplayPoint point = new ReplayPoint();
                point.electricity = electricity;
                point.steam = defaultDecimal(csvDecimal(values, header, "steam"),
                        electricity.multiply(new BigDecimal("0.005")).add(new BigDecimal("0.5")));
                point.carbon = defaultDecimal(csvDecimal(values, header, "co2_tco2_", "co2", "carbon"),
                        electricity.multiply(new BigDecimal("0.00057")));
                point.laggingReactivePower = csvDecimal(values, header, "lagging_current_reactive_power_kvarh", "lagging_reactive_power_kvarh");
                point.leadingReactivePower = csvDecimal(values, header, "leading_current_reactive_power_kvarh", "leading_reactive_power_kvarh");
                point.laggingPowerFactor = csvDecimal(values, header, "lagging_current_power_factor", "lagging_power_factor");
                point.leadingPowerFactor = csvDecimal(values, header, "leading_current_power_factor", "leading_power_factor");
                point.loadType = csvText(values, header, "load_type", "loadtype");
                points.add(point);
            }
        } catch (IOException | NumberFormatException e) {
            return List.of();
        }
        return points;
    }

    private Map<String, Integer> replayHeader(String headerLine) {
        String[] names = headerLine.split(",", -1);
        Map<String, Integer> header = new HashMap<>();
        for (int i = 0; i < names.length; i++) {
            header.put(normalizeHeader(names[i]), i);
        }
        return header;
    }

    private BigDecimal csvDecimal(String[] values, Map<String, Integer> header, String... names) {
        String text = csvText(values, header, names);
        return hasText(text) ? new BigDecimal(text.trim()) : null;
    }

    private String csvText(String[] values, Map<String, Integer> header, String... names) {
        for (String name : names) {
            Integer index = header.get(normalizeHeader(name));
            if (index != null && index >= 0 && index < values.length) {
                return values[index];
            }
        }
        return null;
    }

    private String normalizeHeader(String value) {
        return value != null ? value.trim().replace("-", "_").replace("(", "_").replace(")", "_").toLowerCase() : "";
    }

    private static class ReplayPoint {
        private BigDecimal electricity;
        private BigDecimal steam;
        private BigDecimal carbon;
        private BigDecimal laggingReactivePower;
        private BigDecimal leadingReactivePower;
        private BigDecimal laggingPowerFactor;
        private BigDecimal leadingPowerFactor;
        private String loadType;
    }

    private LocalDateTime parseTimestamp(String value) {
        if (!hasText(value)) {
            return LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        }
        String text = value.trim().replace('T', ' ');
        if (text.endsWith("Z")) {
            text = text.substring(0, text.length() - 1).trim();
        }
        if (text.length() > 19) {
            text = text.substring(0, 19);
        }
        DateTimeFormatter[] formatters = new DateTimeFormatter[]{
                NORMAL_TIME_FORMATTER,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
        };
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDateTime.parse(text, formatter);
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        try {
            return LocalDateTime.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new BusinessException(400, "timestamp 格式错误，应为 yyyy-MM-dd HH:mm:ss");
        }
    }

    private BigDecimal defaultDecimal(BigDecimal value, BigDecimal defaultValue) {
        return (value != null ? value : defaultValue).setScale(4, RoundingMode.HALF_UP);
    }

    private boolean isWeekend(DayOfWeek dayOfWeek) {
        return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
    }

    private String loadType(LocalDateTime timestamp) {
        int hour = timestamp.getHour();
        if (hour >= 8 && hour <= 11 || hour >= 18 && hour <= 21) {
            return "PEAK";
        }
        if (hour >= 0 && hour <= 6) {
            return "VALLEY";
        }
        return "FLAT";
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
