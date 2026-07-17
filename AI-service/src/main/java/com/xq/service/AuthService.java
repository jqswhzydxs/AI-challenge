package com.xq.service;

import com.xq.common.result.Result;
import com.xq.model.dto.LoginDTO;
import com.xq.model.vo.LoginVO;

public interface AuthService {

    Result<LoginVO> login(LoginDTO loginDTO);
}
