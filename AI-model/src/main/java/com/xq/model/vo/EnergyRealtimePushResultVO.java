package com.xq.model.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 能源实时采集点位入库结果.
 */
@Data
@Builder
public class EnergyRealtimePushResultVO {

    private Long dataId;

    private String timestamp;

    private String source;

    private Boolean inserted;
}
