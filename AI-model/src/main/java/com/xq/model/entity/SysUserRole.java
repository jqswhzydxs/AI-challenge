package com.xq.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * 用户角色关联实体.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@TableName("sys_user_role")
public class SysUserRole {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 用户 ID */
    private Long userId;
    /** 角色 ID */
    private Long roleId;
}
