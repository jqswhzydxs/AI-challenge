package com.xq.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class SystemUserUpdateDTO {

    private String password;

    private String realName;

    private String phone;

    private String email;

    private String status;

    private List<Long> roleIds;

    private String remark;
}
