package com.xq.service;

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

import java.util.List;

public interface SystemService {

    Result<PageResult<SystemUserVO>> listUsers(PageQueryDTO query);

    Result<SystemUserVO> createUser(SystemUserCreateDTO dto);

    Result<SystemUserVO> updateUser(Long id, SystemUserUpdateDTO dto);

    Result<Void> deleteUser(Long id);

    Result<List<SystemRoleVO>> listRoles(String status);

    Result<PageResult<SystemOperationLogVO>> listLogs(PageQueryDTO query);

    Result<List<SystemConfigVO>> listConfig(String group);

    Result<List<SystemConfigVO>> updateConfig(SystemConfigUpdateDTO dto);

    Result<List<SystemDataPointVO>> listDataPoints(String type);
}
