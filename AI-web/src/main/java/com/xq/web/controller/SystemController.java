package com.xq.web.controller;

import com.xq.common.result.PageResult;
import com.xq.common.result.Result;
import com.xq.model.dto.PageQueryDTO;
import com.xq.model.dto.SystemConfigUpdateDTO;
import com.xq.model.dto.SystemUserCreateDTO;
import com.xq.model.dto.SystemUserUpdateDTO;
import com.xq.model.vo.SystemConfigVO;
import com.xq.model.vo.SystemDataPointVO;
import com.xq.model.vo.SystemOperationLogVO;
import com.xq.model.vo.SystemRoleVO;
import com.xq.model.vo.SystemUserVO;
import com.xq.service.SystemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "系统管理", description = "用户、角色、日志、配置和数据点")
@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemController {

    private final SystemService systemService;

    @Operation(summary = "用户列表")
    @GetMapping("/users")
    public Result<PageResult<SystemUserVO>> listUsers(@ParameterObject PageQueryDTO query) {
        return systemService.listUsers(query);
    }

    @Operation(summary = "创建用户")
    @PostMapping("/users")
    public Result<SystemUserVO> createUser(@Valid @RequestBody SystemUserCreateDTO dto) {
        return systemService.createUser(dto);
    }

    @Operation(summary = "更新用户")
    @PutMapping("/users/{id}")
    public Result<SystemUserVO> updateUser(
            @PathVariable("id") Long id,
            @RequestBody SystemUserUpdateDTO dto) {
        return systemService.updateUser(id, dto);
    }

    @Operation(summary = "删除用户")
    @DeleteMapping("/users/{id}")
    public Result<Void> deleteUser(@PathVariable("id") Long id) {
        return systemService.deleteUser(id);
    }

    @Operation(summary = "角色列表")
    @GetMapping("/roles")
    public Result<List<SystemRoleVO>> listRoles(@RequestParam(value = "status", required = false) String status) {
        return systemService.listRoles(status);
    }

    @Operation(summary = "操作日志")
    @GetMapping("/logs")
    public Result<PageResult<SystemOperationLogVO>> listLogs(@ParameterObject PageQueryDTO query) {
        return systemService.listLogs(query);
    }

    @Operation(summary = "系统配置")
    @GetMapping("/config")
    public Result<List<SystemConfigVO>> listConfig(@RequestParam(value = "group", required = false) String group) {
        return systemService.listConfig(group);
    }

    @Operation(summary = "更新系统配置")
    @PutMapping("/config")
    public Result<List<SystemConfigVO>> updateConfig(@RequestBody SystemConfigUpdateDTO dto) {
        return systemService.updateConfig(dto);
    }

    @Operation(summary = "数据点列表")
    @GetMapping("/data-points")
    public Result<List<SystemDataPointVO>> listDataPoints(@RequestParam(value = "type", required = false) String type) {
        return systemService.listDataPoints(type);
    }
}
