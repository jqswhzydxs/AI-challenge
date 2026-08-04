package com.xq.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xq.common.constant.UserRole;
import com.xq.common.exception.BusinessException;
import com.xq.common.result.Result;
import com.xq.common.utils.JwtUtils;
import com.xq.mapper.SysRoleMapper;
import com.xq.mapper.SysUserMapper;
import com.xq.mapper.SysUserRoleMapper;
import com.xq.model.dto.LoginDTO;
import com.xq.model.entity.SysRole;
import com.xq.model.entity.SysUser;
import com.xq.model.entity.SysUserRole;
import com.xq.model.vo.LoginVO;
import com.xq.service.AuthService;
import com.xq.service.support.PasswordUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMapper sysRoleMapper;

    @Override
    public Result<LoginVO> login(LoginDTO loginDTO) {
        if (loginDTO == null || StrUtil.isBlank(loginDTO.getUsername()) || StrUtil.isBlank(loginDTO.getPassword())) {
            throw new BusinessException(400, "用户名和密码不能为空");
        }

        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, loginDTO.getUsername())
        );
        if (user == null) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        if ("DISABLE".equals(user.getStatus())) {
            throw new BusinessException(403, "账号已被禁用");
        }
        if (!PasswordUtils.matches(loginDTO.getPassword(), user.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        List<String> roles = resolveRoleCodes(user.getId());
        String primaryRole = roles.get(0);
        String token = JwtUtils.createToken(user.getId(), user.getUsername(), primaryRole);
        LoginVO vo = LoginVO.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .role(primaryRole)
                .roles(roles)
                .build();
        return Result.ok("登录成功", vo);
    }

    @Override
    public Result<LoginVO> currentUser(String authorization) {
        String token = normalizeBearerToken(authorization);
        if (StrUtil.isBlank(token) || JwtUtils.isExpired(token)) {
            throw new BusinessException(401, "登录已过期，请重新登录");
        }

        Long userId = JwtUtils.getUserId(token);
        SysUser user = userId == null ? null : sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(401, "登录用户不存在");
        }
        if ("DISABLE".equals(user.getStatus())) {
            throw new BusinessException(403, "账号已被禁用");
        }

        List<String> roles = resolveRoleCodes(user.getId());
        LoginVO vo = LoginVO.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .role(roles.get(0))
                .roles(roles)
                .build();
        return Result.ok(vo);
    }

    @Override
    public Result<Void> logout() {
        return Result.ok();
    }

    private String normalizeBearerToken(String authorization) {
        if (StrUtil.isBlank(authorization)) {
            return null;
        }
        String bearerPrefix = "Bearer ";
        if (authorization.startsWith(bearerPrefix)) {
            return authorization.substring(bearerPrefix.length());
        }
        return authorization;
    }

    private List<String> resolveRoleCodes(Long userId) {
        List<Long> roleIds = sysUserRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId))
                .stream()
                .map(SysUserRole::getRoleId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (roleIds.isEmpty()) {
            return List.of(UserRole.USER);
        }

        List<String> roleCodes = sysRoleMapper.selectBatchIds(roleIds)
                .stream()
                .filter(role -> "ENABLE".equals(role.getStatus()))
                .map(SysRole::getRoleCode)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return normalizeRoleCodes(roleCodes);
    }

    private List<String> normalizeRoleCodes(List<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return List.of(UserRole.USER);
        }
        boolean admin = roleCodes.stream().anyMatch(UserRole::isAdmin);
        return List.of(admin ? UserRole.ADMIN : UserRole.USER);
    }
}
