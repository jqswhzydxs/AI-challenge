package com.xq.model.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 登录响应 VO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@Builder
public class LoginVO {

    /** JWT Token */
    private String token;

    /** 用户 ID */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 角色 */
    private String role;
}
