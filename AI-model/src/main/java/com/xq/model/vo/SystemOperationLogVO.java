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
public class SystemOperationLogVO {

    private Long id;

    private Long userId;

    private String module;

    private String operation;

    private String requestUri;

    private String requestMethod;

    private Integer resultCode;

    private String errorMessage;

    private LocalDateTime operationTime;
}
