package com.xq.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemUserVO {

    private Long id;

    private String username;

    private String realName;

    private String phone;

    private String email;

    private String status;

    private List<SystemRoleVO> roles;

    private LocalDateTime lastLoginTime;

    private LocalDateTime createTime;

    private String remark;
}
