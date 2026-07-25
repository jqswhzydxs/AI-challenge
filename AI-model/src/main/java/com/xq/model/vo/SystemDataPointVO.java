package com.xq.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemDataPointVO {

    private String pointId;

    private String pointCode;

    private String pointName;

    private String pointType;

    private String sourceTable;

    private Long sourceId;

    private String unit;

    private BigDecimal minValue;

    private BigDecimal maxValue;

    private String status;

    private String description;
}
