package com.xq.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemConfigVO {

    private Long id;

    private String configKey;

    private String configValue;

    private String configName;

    private String configGroup;

    private Integer editable;

    private LocalDateTime updateTime;

    private String remark;
}
