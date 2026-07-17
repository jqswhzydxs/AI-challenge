package com.xq.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统用户实体.
 * <p>
 * 用于登录、权限和操作记录.
 * </p>
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@TableName("sys_user")
public class SysUser {

    /** 用户 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 登录账号 */
    private String username;
    /** 加密密码 */
    private String password;
    /** 姓名 */
    private String realName;
    /** 手机号 */
    private String phone;
    /** 邮箱 */
    private String email;
    /** 状态：ENABLE / DISABLE */
    private String status;
    /** 最近登录时间 */
    private LocalDateTime lastLoginTime;

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
