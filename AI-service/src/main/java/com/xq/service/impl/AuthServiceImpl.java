package com.xq.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xq.common.exception.BusinessException;
import com.xq.common.result.Result;
import com.xq.common.utils.JwtUtils;
import com.xq.mapper.SysUserMapper;
import com.xq.model.dto.LoginDTO;
import com.xq.model.entity.SysUser;
import com.xq.model.vo.LoginVO;
import com.xq.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper sysUserMapper;

    @Override
    public Result<LoginVO> login(LoginDTO loginDTO) {
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
        // Mock: 实际应使用 BCrypt 校验
        if (!loginDTO.getPassword().equals(user.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        String token = JwtUtils.createToken(user.getId(), user.getUsername(), "SYSTEM_ADMIN");
        LoginVO vo = LoginVO.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .role("SYSTEM_ADMIN")
                .build();
        return Result.ok("登录成功", vo);
    }
}
