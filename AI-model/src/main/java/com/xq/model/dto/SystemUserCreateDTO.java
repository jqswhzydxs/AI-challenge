package com.xq.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class SystemUserCreateDTO {

    @NotBlank(message = "username cannot be blank")
    private String username;

    @NotBlank(message = "password cannot be blank")
    private String password;

    private String realName;

    private String phone;

    private String email;

    private String status;

    private List<Long> roleIds;

    private String remark;
}
