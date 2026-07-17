package com.xq.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色实体.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@TableName("sys_role")
public class SysRole {

    /** 角色 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 角色编码，如 SYSTEM_ADMIN */
    private String roleCode;
    /** 角色名称 */
    private String roleName;
    /** 启用状态 */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    private Long createBy;
    private Long updateBy;
    @TableLogic
    private Integer deleted;
    private String remark;
}
