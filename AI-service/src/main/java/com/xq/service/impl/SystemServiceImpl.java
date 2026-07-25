package com.xq.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xq.common.exception.BusinessException;
import com.xq.common.result.PageResult;
import com.xq.common.result.Result;
import com.xq.mapper.EnergyEquipmentMapper;
import com.xq.mapper.ProductionEquipmentMapper;
import com.xq.mapper.ProductionLineMapper;
import com.xq.mapper.SysOperationLogMapper;
import com.xq.mapper.SysRoleMapper;
import com.xq.mapper.SysUserMapper;
import com.xq.mapper.SysUserRoleMapper;
import com.xq.mapper.SystemConfigMapper;
import com.xq.model.dto.PageQueryDTO;
import com.xq.model.dto.SystemConfigUpdateDTO;
import com.xq.model.dto.SystemUserCreateDTO;
import com.xq.model.dto.SystemUserUpdateDTO;
import com.xq.model.entity.EnergyEquipment;
import com.xq.model.entity.ProductionEquipment;
import com.xq.model.entity.ProductionLine;
import com.xq.model.entity.SysOperationLog;
import com.xq.model.entity.SysRole;
import com.xq.model.entity.SysUser;
import com.xq.model.entity.SysUserRole;
import com.xq.model.entity.SystemConfig;
import com.xq.model.vo.SystemConfigVO;
import com.xq.model.vo.SystemDataPointVO;
import com.xq.model.vo.SystemOperationLogVO;
import com.xq.model.vo.SystemRoleVO;
import com.xq.model.vo.SystemUserVO;
import com.xq.service.SystemService;
import com.xq.service.support.PasswordUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SystemServiceImpl implements SystemService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysOperationLogMapper sysOperationLogMapper;
    private final SystemConfigMapper systemConfigMapper;
    private final ProductionLineMapper productionLineMapper;
    private final ProductionEquipmentMapper productionEquipmentMapper;
    private final EnergyEquipmentMapper energyEquipmentMapper;

    @Override
    public Result<PageResult<SystemUserVO>> listUsers(PageQueryDTO query) {
        int pageNum = pageNum(query);
        int pageSize = pageSize(query);
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query != null ? query.getStatus() : null)) {
            wrapper.eq(SysUser::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(SysUser::getCreateTime);
        Page<SysUser> page = sysUserMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<SystemUserVO> records = attachRoles(page.getRecords());
        return Result.ok(PageResult.of(page.getTotal(), pageNum, pageSize, records));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<SystemUserVO> createUser(SystemUserCreateDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getUsername()) || !StringUtils.hasText(dto.getPassword())) {
            throw new BusinessException(400, "username and password are required");
        }
        String username = dto.getUsername().trim();
        Long exists = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (exists != null && exists > 0) {
            throw new BusinessException(400, "username already exists: " + username);
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(PasswordUtils.encode(dto.getPassword()));
        user.setRealName(dto.getRealName());
        user.setPhone(dto.getPhone());
        user.setEmail(dto.getEmail());
        user.setStatus(StringUtils.hasText(dto.getStatus()) ? dto.getStatus() : "ENABLE");
        user.setRemark(dto.getRemark());
        sysUserMapper.insert(user);
        syncUserRoles(user.getId(), dto.getRoleIds());
        return Result.ok(findUserVO(user.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<SystemUserVO> updateUser(Long id, SystemUserUpdateDTO dto) {
        SysUser user = requireUser(id);
        if (dto == null) {
            return Result.ok(findUserVO(id));
        }
        if (StringUtils.hasText(dto.getPassword())) {
            user.setPassword(PasswordUtils.encode(dto.getPassword()));
        }
        if (dto.getRealName() != null) {
            user.setRealName(dto.getRealName());
        }
        if (dto.getPhone() != null) {
            user.setPhone(dto.getPhone());
        }
        if (dto.getEmail() != null) {
            user.setEmail(dto.getEmail());
        }
        if (StringUtils.hasText(dto.getStatus())) {
            user.setStatus(dto.getStatus());
        }
        if (dto.getRemark() != null) {
            user.setRemark(dto.getRemark());
        }
        sysUserMapper.updateById(user);
        if (dto.getRoleIds() != null) {
            syncUserRoles(id, dto.getRoleIds());
        }
        return Result.ok(findUserVO(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deleteUser(Long id) {
        requireUser(id);
        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
        sysUserMapper.deleteById(id);
        return Result.ok();
    }

    @Override
    public Result<List<SystemRoleVO>> listRoles(String status) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            wrapper.eq(SysRole::getStatus, status);
        }
        wrapper.orderByAsc(SysRole::getRoleCode);
        return Result.ok(sysRoleMapper.selectList(wrapper).stream().map(this::toRoleVO).toList());
    }

    @Override
    public Result<PageResult<SystemOperationLogVO>> listLogs(PageQueryDTO query) {
        int pageNum = pageNum(query);
        int pageSize = pageSize(query);
        LambdaQueryWrapper<SysOperationLog> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query != null ? query.getStatus() : null)) {
            wrapper.eq(SysOperationLog::getResultCode, parseResultCode(query.getStatus()));
        }
        wrapper.orderByDesc(SysOperationLog::getOperationTime);
        Page<SysOperationLog> page = sysOperationLogMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<SystemOperationLogVO> records = page.getRecords().stream().map(this::toLogVO).toList();
        return Result.ok(PageResult.of(page.getTotal(), pageNum, pageSize, records));
    }

    @Override
    public Result<List<SystemConfigVO>> listConfig(String group) {
        return Result.ok(queryConfig(group).stream().map(this::toConfigVO).toList());
    }

    @Override
    public Result<List<SystemConfigVO>> updateConfig(SystemConfigUpdateDTO dto) {
        Map<String, String> values = normalizeConfigValues(dto);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            SystemConfig config = systemConfigMapper.selectOne(new LambdaQueryWrapper<SystemConfig>()
                    .eq(SystemConfig::getConfigKey, entry.getKey())
                    .last("LIMIT 1"));
            if (config == null) {
                throw new BusinessException(404, "config not found: " + entry.getKey());
            }
            if (!Objects.equals(config.getEditable(), 1)) {
                throw new BusinessException(403, "config is readonly: " + entry.getKey());
            }
            config.setConfigValue(entry.getValue());
            systemConfigMapper.updateById(config);
        }
        return listConfig(null);
    }

    @Override
    public Result<List<SystemDataPointVO>> listDataPoints(String type) {
        String normalized = StringUtils.hasText(type) ? type.trim().toUpperCase() : "";
        List<SystemDataPointVO> points = new ArrayList<>();
        if (!StringUtils.hasText(normalized) || "PRODUCTION".equals(normalized) || "LINE".equals(normalized)) {
            points.addAll(productionLinePoints());
            points.addAll(productionEquipmentPoints());
        }
        if (!StringUtils.hasText(normalized) || "ENERGY".equals(normalized)) {
            points.addAll(energyEquipmentPoints());
        }
        return Result.ok(points);
    }

    private List<SystemUserVO> attachRoles(List<SysUser> users) {
        if (users.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = users.stream().map(SysUser::getId).collect(Collectors.toSet());
        List<SysUserRole> userRoles = sysUserRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().in(SysUserRole::getUserId, userIds));
        Map<Long, SysRole> roleById = sysRoleMapper.selectList(new LambdaQueryWrapper<SysRole>())
                .stream().collect(Collectors.toMap(SysRole::getId, role -> role, (a, b) -> a));
        Map<Long, List<SystemRoleVO>> rolesByUser = userRoles.stream()
                .collect(Collectors.groupingBy(
                        SysUserRole::getUserId,
                        Collectors.mapping(userRole -> toRoleVO(roleById.get(userRole.getRoleId())), Collectors.toList())
                ));

        return users.stream()
                .map(user -> SystemUserVO.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .realName(user.getRealName())
                        .phone(user.getPhone())
                        .email(user.getEmail())
                        .status(user.getStatus())
                        .roles(rolesByUser.getOrDefault(user.getId(), List.of()).stream()
                                .filter(Objects::nonNull)
                                .toList())
                        .lastLoginTime(user.getLastLoginTime())
                        .createTime(user.getCreateTime())
                        .remark(user.getRemark())
                        .build())
                .toList();
    }

    private SystemUserVO findUserVO(Long id) {
        SysUser user = requireUser(id);
        List<SystemUserVO> users = attachRoles(List.of(user));
        return users.isEmpty() ? null : users.get(0);
    }

    private SysUser requireUser(Long id) {
        if (id == null) {
            throw new BusinessException(400, "user id is required");
        }
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(404, "user not found: " + id);
        }
        return user;
    }

    private void syncUserRoles(Long userId, List<Long> roleIds) {
        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        for (Long roleId : new LinkedHashSet<>(roleIds)) {
            if (roleId == null) {
                continue;
            }
            SysRole role = sysRoleMapper.selectById(roleId);
            if (role == null) {
                throw new BusinessException(404, "role not found: " + roleId);
            }
            SysUserRole userRole = new SysUserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(roleId);
            sysUserRoleMapper.insert(userRole);
        }
    }

    private SystemRoleVO toRoleVO(SysRole role) {
        if (role == null) {
            return null;
        }
        return SystemRoleVO.builder()
                .id(role.getId())
                .roleCode(role.getRoleCode())
                .roleName(role.getRoleName())
                .status(role.getStatus())
                .createTime(role.getCreateTime())
                .remark(role.getRemark())
                .build();
    }

    private SystemOperationLogVO toLogVO(SysOperationLog log) {
        return SystemOperationLogVO.builder()
                .id(log.getId())
                .userId(log.getUserId())
                .module(log.getModule())
                .operation(log.getOperation())
                .requestUri(log.getRequestUri())
                .requestMethod(log.getRequestMethod())
                .resultCode(log.getResultCode())
                .errorMessage(log.getErrorMessage())
                .operationTime(log.getOperationTime())
                .build();
    }

    private SystemConfigVO toConfigVO(SystemConfig config) {
        return SystemConfigVO.builder()
                .id(config.getId())
                .configKey(config.getConfigKey())
                .configValue(config.getConfigValue())
                .configName(config.getConfigName())
                .configGroup(config.getConfigGroup())
                .editable(config.getEditable())
                .updateTime(config.getUpdateTime())
                .remark(config.getRemark())
                .build();
    }

    private List<SystemConfig> queryConfig(String group) {
        LambdaQueryWrapper<SystemConfig> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(group)) {
            wrapper.eq(SystemConfig::getConfigGroup, group);
        }
        wrapper.orderByAsc(SystemConfig::getConfigGroup).orderByAsc(SystemConfig::getConfigKey);
        return systemConfigMapper.selectList(wrapper);
    }

    private Map<String, String> normalizeConfigValues(SystemConfigUpdateDTO dto) {
        Map<String, String> values = new LinkedHashMap<>();
        if (dto != null && dto.getValues() != null) {
            dto.getValues().forEach((key, value) -> {
                if (StringUtils.hasText(key)) {
                    values.put(key.trim(), value);
                }
            });
        }
        if (dto != null && StringUtils.hasText(dto.getConfigKey())) {
            values.put(dto.getConfigKey().trim(), dto.getConfigValue());
        }
        if (values.isEmpty()) {
            throw new BusinessException(400, "config update is empty");
        }
        return values;
    }

    private List<SystemDataPointVO> productionLinePoints() {
        return productionLineMapper.selectList(new LambdaQueryWrapper<ProductionLine>().orderByAsc(ProductionLine::getLineCode))
                .stream()
                .map(line -> SystemDataPointVO.builder()
                        .pointId("production-line-" + line.getId())
                        .pointCode(line.getLineCode() + ".capacity")
                        .pointName(line.getLineName() + "产能")
                        .pointType("PRODUCTION_LINE")
                        .sourceTable("production_line")
                        .sourceId(line.getId())
                        .unit("t/h")
                        .minValue(line.getMinCapacity())
                        .maxValue(line.getMaxCapacity())
                        .status(line.getStatus())
                        .description(line.getRemark())
                        .build())
                .toList();
    }

    private List<SystemDataPointVO> productionEquipmentPoints() {
        return productionEquipmentMapper.selectList(new LambdaQueryWrapper<ProductionEquipment>().orderByAsc(ProductionEquipment::getEquipmentCode))
                .stream()
                .map(equipment -> SystemDataPointVO.builder()
                        .pointId("production-equipment-" + equipment.getId())
                        .pointCode(equipment.getEquipmentCode() + ".ratedPower")
                        .pointName(equipment.getEquipmentName() + "额定功率")
                        .pointType("PRODUCTION_EQUIPMENT")
                        .sourceTable("production_equipment")
                        .sourceId(equipment.getId())
                        .unit("kW")
                        .maxValue(equipment.getRatedPower())
                        .status(equipment.getStatus())
                        .description(equipment.getRemark())
                        .build())
                .toList();
    }

    private List<SystemDataPointVO> energyEquipmentPoints() {
        return energyEquipmentMapper.selectList(new LambdaQueryWrapper<EnergyEquipment>().orderByAsc(EnergyEquipment::getEquipmentCode))
                .stream()
                .map(equipment -> SystemDataPointVO.builder()
                        .pointId("energy-equipment-" + equipment.getId())
                        .pointCode(equipment.getEquipmentCode() + ".output")
                        .pointName(equipment.getEquipmentName() + "能源输出")
                        .pointType("ENERGY_EQUIPMENT")
                        .sourceTable("energy_equipment")
                        .sourceId(equipment.getId())
                        .unit("t/h")
                        .minValue(equipment.getMinOutput())
                        .maxValue(equipment.getMaxOutput())
                        .status(equipment.getStatus())
                        .description(equipment.getRemark())
                        .build())
                .toList();
    }

    private int pageNum(PageQueryDTO query) {
        return query != null && query.getPageNum() != null && query.getPageNum() > 0 ? query.getPageNum() : 1;
    }

    private int pageSize(PageQueryDTO query) {
        int size = query != null && query.getPageSize() != null && query.getPageSize() > 0 ? query.getPageSize() : 10;
        return Math.min(size, 100);
    }

    private Integer parseResultCode(String status) {
        try {
            return Integer.valueOf(status);
        } catch (NumberFormatException ex) {
            throw new BusinessException(400, "result code must be a number");
        }
    }
}
