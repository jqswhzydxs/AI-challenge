package com.xq.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xq.common.exception.BusinessException;
import com.xq.common.result.PageResult;
import com.xq.common.result.Result;
import com.xq.mapper.ProductionOrderMapper;
import com.xq.model.dto.PageQueryDTO;
import com.xq.model.entity.ProductionOrder;
import com.xq.model.vo.ImportOrderResultVO;
import com.xq.model.vo.ProductionOrderVO;
import com.xq.service.ProductionOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductionOrderServiceImpl implements ProductionOrderService {

    private final ProductionOrderMapper productionOrderMapper;

    @Override
    public Result<PageResult<ProductionOrderVO>> listOrders(PageQueryDTO query) {
        LambdaQueryWrapper<ProductionOrder> wrapper = new LambdaQueryWrapper<>();
        if (query.getDate() != null) {
            wrapper.apply("DATE_FORMAT(due_time, '%Y-%m-%d') = {0}", query.getDate());
        }
        if (query.getStatus() != null) {
            wrapper.eq(ProductionOrder::getStatus, query.getStatus());
        }
        wrapper.orderByAsc(ProductionOrder::getPriority)
               .orderByAsc(ProductionOrder::getDueTime);

        Page<ProductionOrder> page = new Page<>(query.getPageNum(), query.getPageSize());
        Page<ProductionOrder> result = productionOrderMapper.selectPage(page, wrapper);

        List<ProductionOrderVO> records = result.getRecords().stream().map(o -> ProductionOrderVO.builder()
                .orderId(o.getId())
                .orderNo(o.getOrderNo())
                .productName(o.getProductName())
                .plannedQuantity(o.getPlannedQuantity())
                .unit(o.getUnit())
                .dueTime(o.getDueTime())
                .priority(o.getPriority())
                .status(o.getStatus())
                .build()).collect(Collectors.toList());

        return Result.ok(PageResult.of(result.getTotal(), query.getPageNum(), query.getPageSize(), records));
    }

    @Override
    public Result<ImportOrderResultVO> importOrders(byte[] fileBytes, String originalFilename) {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new BusinessException(400, "上传文件不能为空");
        }
        String filename = originalFilename != null ? originalFilename.trim() : "";
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new BusinessException(400, "当前仅支持 CSV 订单文件");
        }

        List<List<String>> rows = parseCsv(new String(fileBytes, StandardCharsets.UTF_8));
        if (rows.isEmpty()) {
            throw new BusinessException(400, "订单 CSV 不能为空");
        }

        Map<String, Integer> header = headerIndex(rows.get(0));
        requireAny(header, "orderNo", "order_no", "orderId", "order_id");
        requireAny(header, "productName", "product_name", "product");
        requireAny(header, "plannedQuantity", "planned_quantity", "quantity", "demandTon", "demand_ton", "weight");
        requireAny(header, "dueTime", "due_time", "deadline", "deliveryTime", "delivery_time");

        int inserted = 0;
        int updated = 0;
        int skipped = 0;

        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (row.stream().allMatch(value -> value == null || value.trim().isEmpty())) {
                skipped++;
                continue;
            }

            ProductionOrder order = toOrder(row, header, i + 1);
            ProductionOrder existing = productionOrderMapper.selectOne(
                    new LambdaQueryWrapper<ProductionOrder>()
                            .eq(ProductionOrder::getOrderNo, order.getOrderNo())
                            .last("LIMIT 1")
            );
            if (existing == null) {
                productionOrderMapper.insert(order);
                inserted++;
            } else {
                order.setId(existing.getId());
                productionOrderMapper.updateById(order);
                updated++;
            }
        }

        ImportOrderResultVO vo = ImportOrderResultVO.builder()
                .totalCount(Math.max(rows.size() - 1, 0))
                .insertedCount(inserted)
                .updatedCount(updated)
                .skippedCount(skipped)
                .build();
        return Result.ok("订单导入成功", vo);
    }

    private ProductionOrder toOrder(List<String> row, Map<String, Integer> header, int rowNumber) {
        String orderNo = required(row, header, rowNumber, "orderNo", "order_no", "orderId", "order_id");
        String productName = required(row, header, rowNumber, "productName", "product_name", "product");
        BigDecimal quantity = decimal(required(row, header, rowNumber,
                "plannedQuantity", "planned_quantity", "quantity", "demandTon", "demand_ton", "weight"), rowNumber);
        LocalDateTime dueTime = dateTime(required(row, header, rowNumber,
                "dueTime", "due_time", "deadline", "deliveryTime", "delivery_time"), rowNumber);

        ProductionOrder order = new ProductionOrder();
        order.setOrderNo(orderNo);
        order.setProductName(productName);
        order.setProductSpec(optional(row, header, "productSpec", "product_spec", "spec"));
        order.setPlannedQuantity(quantity);
        order.setUnit(defaultValue(optional(row, header, "unit"), "t"));
        order.setDueTime(dueTime);
        order.setPriority(integer(defaultValue(optional(row, header, "priority"), "1"), rowNumber));
        order.setStatus(defaultValue(optional(row, header, "status"), "PENDING"));
        order.setRemark(optional(row, header, "remark", "备注"));
        order.setDeleted(0);
        return order;
    }

    private List<List<String>> parseCsv(String content) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < content.length() && content.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                row.add(cell.toString().trim());
                cell.setLength(0);
            } else if ((ch == '\n' || ch == '\r') && !quoted) {
                if (ch == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(cell.toString().trim());
                cell.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                cell.append(ch);
            }
        }
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString().trim());
            rows.add(row);
        }
        return rows;
    }

    private Map<String, Integer> headerIndex(List<String> headerRow) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < headerRow.size(); i++) {
            String key = normalizeHeader(headerRow.get(i));
            if (!key.isEmpty()) {
                index.put(key, i);
            }
        }
        return index;
    }

    private String normalizeHeader(String value) {
        return value == null ? "" : value.replace("\uFEFF", "")
                .trim()
                .replace("-", "")
                .replace("_", "")
                .toLowerCase(Locale.ROOT);
    }

    private void requireAny(Map<String, Integer> header, String... names) {
        for (String name : names) {
            if (header.containsKey(normalizeHeader(name))) {
                return;
            }
        }
        throw new BusinessException(415, "订单 CSV 缺少字段: " + names[0]);
    }

    private String required(List<String> row, Map<String, Integer> header, int rowNumber, String... names) {
        String value = optional(row, header, names);
        if (value == null || value.trim().isEmpty()) {
            throw new BusinessException(400, "订单 CSV 第 " + rowNumber + " 行缺少字段: " + names[0]);
        }
        return value.trim();
    }

    private String optional(List<String> row, Map<String, Integer> header, String... names) {
        for (String name : names) {
            Integer index = header.get(normalizeHeader(name));
            if (index != null && index < row.size()) {
                String value = row.get(index);
                return value != null ? value.trim() : null;
            }
        }
        return null;
    }

    private String defaultValue(String value, String defaultValue) {
        return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
    }

    private BigDecimal decimal(String value, int rowNumber) {
        try {
            BigDecimal number = new BigDecimal(value.trim());
            if (number.compareTo(BigDecimal.ZERO) <= 0) {
                throw new NumberFormatException("quantity must be positive");
            }
            return number;
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "订单 CSV 第 " + rowNumber + " 行 plannedQuantity 必须是正数");
        }
    }

    private Integer integer(String value, int rowNumber) {
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "订单 CSV 第 " + rowNumber + " 行 priority 必须是整数");
        }
    }

    private LocalDateTime dateTime(String value, int rowNumber) {
        String text = value.trim();
        DateTimeFormatter[] dateTimeFormatters = new DateTimeFormatter[]{
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
        };
        for (DateTimeFormatter formatter : dateTimeFormatters) {
            try {
                return LocalDateTime.parse(text, formatter);
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        try {
            return LocalDate.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd")).atTime(23, 59, 59);
        } catch (DateTimeParseException ignored) {
            // try ISO parser
        }
        try {
            return LocalDateTime.parse(text);
        } catch (DateTimeParseException e) {
            throw new BusinessException(400, "订单 CSV 第 " + rowNumber + " 行 dueTime 格式错误，应为 yyyy-MM-dd HH:mm:ss");
        }
    }
}
