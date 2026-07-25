package com.xq.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xq.common.exception.BusinessException;
import com.xq.mapper.EnergyEquipmentMapper;
import com.xq.mapper.ProductionEquipmentMapper;
import com.xq.mapper.ProductionLineMapper;
import com.xq.mapper.SysOperationLogMapper;
import com.xq.mapper.SysRoleMapper;
import com.xq.mapper.SysUserMapper;
import com.xq.mapper.SysUserRoleMapper;
import com.xq.mapper.SystemConfigMapper;
import com.xq.model.dto.SystemConfigUpdateDTO;
import com.xq.model.dto.SystemUserCreateDTO;
import com.xq.model.entity.SysRole;
import com.xq.model.entity.SysUser;
import com.xq.model.entity.SysUserRole;
import com.xq.model.entity.SystemConfig;
import com.xq.model.vo.SystemUserVO;
import com.xq.service.support.PasswordUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

class SystemServiceImplTest {

    @Test
    void createUserHashesPasswordAndSyncsDistinctRoles() {
        SysUserMapper sysUserMapper = mock(SysUserMapper.class);
        SysRoleMapper sysRoleMapper = mock(SysRoleMapper.class);
        SysUserRoleMapper sysUserRoleMapper = mock(SysUserRoleMapper.class);
        SystemServiceImpl service = service(sysUserMapper, sysRoleMapper, sysUserRoleMapper, mock(SystemConfigMapper.class));
        SysUser[] insertedUser = new SysUser[1];

        when(sysUserMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        doAnswer(invocation -> {
            SysUser user = invocation.getArgument(0);
            user.setId(100L);
            insertedUser[0] = user;
            return 1;
        }).when(sysUserMapper).insert(any(SysUser.class));
        when(sysUserMapper.selectById(100L)).thenAnswer(invocation -> insertedUser[0]);
        when(sysRoleMapper.selectById(2L)).thenReturn(role(2L, "ENERGY_MANAGER"));
        when(sysRoleMapper.selectById(3L)).thenReturn(role(3L, "PRODUCTION_MANAGER"));
        when(sysUserRoleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(userRole(100L, 2L), userRole(100L, 3L)));
        when(sysRoleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                role(2L, "ENERGY_MANAGER"),
                role(3L, "PRODUCTION_MANAGER")
        ));

        SystemUserCreateDTO dto = new SystemUserCreateDTO();
        dto.setUsername("  zhangsan  ");
        dto.setPassword("plain123");
        dto.setRealName("Zhang San");
        dto.setRoleIds(Arrays.asList(2L, 2L, null, 3L));

        SystemUserVO data = service.createUser(dto).getData();

        assertEquals(100L, data.getId());
        assertEquals("zhangsan", data.getUsername());
        assertEquals(2, data.getRoles().size());
        assertTrue(PasswordUtils.matches("plain123", insertedUser[0].getPassword()));
        assertTrue(insertedUser[0].getPassword().startsWith("$2"));

        ArgumentCaptor<SysUserRole> roleCaptor = ArgumentCaptor.forClass(SysUserRole.class);
        verify(sysUserRoleMapper).delete(any(LambdaQueryWrapper.class));
        verify(sysUserRoleMapper, times(2)).insert(roleCaptor.capture());
        assertEquals(List.of(2L, 3L), roleCaptor.getAllValues().stream().map(SysUserRole::getRoleId).toList());
    }

    @Test
    void deleteUserClearsRoleRelationsBeforeDeletingUser() {
        SysUserMapper sysUserMapper = mock(SysUserMapper.class);
        SysUserRoleMapper sysUserRoleMapper = mock(SysUserRoleMapper.class);
        SystemServiceImpl service = service(sysUserMapper, mock(SysRoleMapper.class), sysUserRoleMapper, mock(SystemConfigMapper.class));

        SysUser user = new SysUser();
        user.setId(5L);
        when(sysUserMapper.selectById(5L)).thenReturn(user);

        service.deleteUser(5L);

        verify(sysUserRoleMapper).delete(any(LambdaQueryWrapper.class));
        verify(sysUserMapper).deleteById(5L);
    }

    @Test
    void updateConfigRejectsReadonlyConfig() {
        SystemConfigMapper systemConfigMapper = mock(SystemConfigMapper.class);
        SystemServiceImpl service = service(mock(SysUserMapper.class), mock(SysRoleMapper.class), mock(SysUserRoleMapper.class), systemConfigMapper);
        SystemConfig readonly = new SystemConfig();
        readonly.setId(1L);
        readonly.setConfigKey("jwt.secret");
        readonly.setConfigValue("old");
        readonly.setEditable(0);
        when(systemConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(readonly);

        SystemConfigUpdateDTO dto = new SystemConfigUpdateDTO();
        dto.setConfigKey("jwt.secret");
        dto.setConfigValue("new");

        assertThrows(BusinessException.class, () -> service.updateConfig(dto));
        verify(systemConfigMapper, never()).updateById(any(SystemConfig.class));
    }

    private SystemServiceImpl service(SysUserMapper sysUserMapper,
                                      SysRoleMapper sysRoleMapper,
                                      SysUserRoleMapper sysUserRoleMapper,
                                      SystemConfigMapper systemConfigMapper) {
        return new SystemServiceImpl(
                sysUserMapper,
                sysRoleMapper,
                sysUserRoleMapper,
                mock(SysOperationLogMapper.class),
                systemConfigMapper,
                mock(ProductionLineMapper.class),
                mock(ProductionEquipmentMapper.class),
                mock(EnergyEquipmentMapper.class)
        );
    }

    private SysRole role(Long id, String code) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setRoleCode(code);
        role.setRoleName(code);
        role.setStatus("ENABLE");
        return role;
    }

    private SysUserRole userRole(Long userId, Long roleId) {
        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(roleId);
        return userRole;
    }
}
