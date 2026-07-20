package com.xq.web.controller;

import com.xq.common.result.Result;
import com.xq.model.dto.LoginDTO;
import com.xq.model.vo.LoginVO;
import com.xq.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器.
 * <p>
 * 对应接口文档 4.1 登录接口.
 * </p>
 *
 * @author XQ
 * @since 1.0.0
 */
@Tag(name = "认证", description = "用户登录与身份认证")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "用户登录", description = "使用用户名和密码登录系统，返回 JWT token 和用户信息")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO loginDTO) {
        return authService.login(loginDTO);
    }

    @Operation(summary = "当前用户信息", description = "根据 Authorization 中的 Bearer token 返回当前登录用户信息")
    @GetMapping("/userinfo")
    public Result<LoginVO> userInfo(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return authService.currentUser(authorization);
    }

    @Operation(summary = "退出登录", description = "JWT 无状态退出，前端清理本地 token 即可")
    @PostMapping("/logout")
    public Result<Void> logout() {
        return authService.logout();
    }
}
