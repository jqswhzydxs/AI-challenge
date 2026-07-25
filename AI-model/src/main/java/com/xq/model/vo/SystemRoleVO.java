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
public class SystemRoleVO {

    private Long id;

    private String roleCode;

    private String roleName;

    private String status;

    private LocalDateTime createTime;

    private String remark;
}
