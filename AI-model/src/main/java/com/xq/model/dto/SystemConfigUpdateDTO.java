package com.xq.model.dto;

import lombok.Data;

import java.util.Map;

@Data
public class SystemConfigUpdateDTO {

    private String configKey;

    private String configValue;

    private Map<String, String> values;
}
