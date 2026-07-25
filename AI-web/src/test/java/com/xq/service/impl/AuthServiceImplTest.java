package com.xq.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.xq.service.support.PasswordUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceImplTest {

    @Test
    void loginUsesRolesFromUserRoleTables() {
        SysUserMapper sysUserMapper = mock(SysUserMapper.class);
        SysUserRoleMapper sysUserRoleMapper = mock(SysUserRoleMapper.class);
        SysRoleMapper sysRoleMapper = mock(SysRoleMapper.class);
        AuthServiceImpl service = new AuthServiceImpl(sysUserMapper, sysUserRoleMapper, sysRoleMapper);

        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("energy");
        user.setPassword("123456");
        user.setStatus("ENABLE");
        when(sysUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(1L);
        userRole.setRoleId(3L);
        when(sysUserRoleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(userRole));

        SysRole role = new SysRole();
        role.setId(3L);
        role.setRoleCode("ENERGY_MANAGER");
        role.setStatus("ENABLE");
        when(sysRoleMapper.selectBatchIds(any())).thenReturn(List.of(role));

        LoginDTO dto = new LoginDTO();
        dto.setUsername("energy");
        dto.setPassword("123456");
        Result<LoginVO> result = service.login(dto);

        assertEquals("ENERGY_MANAGER", result.getData().getRole());
        assertEquals(List.of("ENERGY_MANAGER"), result.getData().getRoles());
        assertEquals("ENERGY_MANAGER", JwtUtils.getRole(result.getData().getToken()));
    }

    @Test
    void loginAcceptsBcryptPassword() {
        SysUserMapper sysUserMapper = mock(SysUserMapper.class);
        SysUserRoleMapper sysUserRoleMapper = mock(SysUserRoleMapper.class);
        SysRoleMapper sysRoleMapper = mock(SysRoleMapper.class);
        AuthServiceImpl service = new AuthServiceImpl(sysUserMapper, sysUserRoleMapper, sysRoleMapper);

        SysUser user = new SysUser();
        user.setId(2L);
        user.setUsername("planner");
        user.setPassword(PasswordUtils.encode("123456"));
        user.setStatus("ENABLE");
        when(sysUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(sysUserRoleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        LoginDTO dto = new LoginDTO();
        dto.setUsername("planner");
        dto.setPassword("123456");
        Result<LoginVO> result = service.login(dto);

        assertEquals("SYSTEM_ADMIN", result.getData().getRole());
    }

    @Test
    void loginRejectsWrongPasswordAndDisabledAccount() {
        SysUserMapper sysUserMapper = mock(SysUserMapper.class);
        AuthServiceImpl service = new AuthServiceImpl(sysUserMapper, mock(SysUserRoleMapper.class), mock(SysRoleMapper.class));

        SysUser user = new SysUser();
        user.setId(3L);
        user.setUsername("operator");
        user.setPassword(PasswordUtils.encode("right-password"));
        user.setStatus("ENABLE");
        when(sysUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        LoginDTO wrongPassword = new LoginDTO();
        wrongPassword.setUsername("operator");
        wrongPassword.setPassword("wrong-password");
        BusinessException passwordError = assertThrows(BusinessException.class, () -> service.login(wrongPassword));
        assertEquals(401, passwordError.getCode());

        user.setStatus("DISABLE");
        LoginDTO disabled = new LoginDTO();
        disabled.setUsername("operator");
        disabled.setPassword("right-password");
        BusinessException disabledError = assertThrows(BusinessException.class, () -> service.login(disabled));
        assertEquals(403, disabledError.getCode());
    }

    @Test
    void currentUserUsesBearerTokenAndFallsBackWhenRolesDisabled() {
        SysUserMapper sysUserMapper = mock(SysUserMapper.class);
        SysUserRoleMapper sysUserRoleMapper = mock(SysUserRoleMapper.class);
        SysRoleMapper sysRoleMapper = mock(SysRoleMapper.class);
        AuthServiceImpl service = new AuthServiceImpl(sysUserMapper, sysUserRoleMapper, sysRoleMapper);

        SysUser user = new SysUser();
        user.setId(4L);
        user.setUsername("viewer");
        user.setStatus("ENABLE");
        when(sysUserMapper.selectById(4L)).thenReturn(user);

        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(4L);
        userRole.setRoleId(8L);
        when(sysUserRoleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(userRole));

        SysRole disabledRole = new SysRole();
        disabledRole.setId(8L);
        disabledRole.setRoleCode("ENERGY_MANAGER");
        disabledRole.setStatus("DISABLE");
        when(sysRoleMapper.selectBatchIds(any())).thenReturn(List.of(disabledRole));

        String token = JwtUtils.createToken(4L, "viewer", "ENERGY_MANAGER");
        LoginVO data = service.currentUser("Bearer " + token).getData();

        assertEquals(4L, data.getUserId());
        assertEquals("viewer", data.getUsername());
        assertEquals("SYSTEM_ADMIN", data.getRole());
        assertEquals(List.of("SYSTEM_ADMIN"), data.getRoles());
    }
}
