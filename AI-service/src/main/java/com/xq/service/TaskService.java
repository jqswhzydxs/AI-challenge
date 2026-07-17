package com.xq.service;

import com.xq.common.result.Result;
import com.xq.model.vo.TaskVO;

public interface TaskService {

    Result<TaskVO> getTask(Long taskId);
}
