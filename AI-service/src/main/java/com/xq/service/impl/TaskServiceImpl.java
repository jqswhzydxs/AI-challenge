package com.xq.service.impl;

import com.xq.common.exception.BusinessException;
import com.xq.common.result.Result;
import com.xq.mapper.AlgorithmTaskMapper;
import com.xq.model.entity.AlgorithmTask;
import com.xq.model.vo.TaskVO;
import com.xq.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 任务查询服务实现.
 *
 * @author XQ
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private final AlgorithmTaskMapper algorithmTaskMapper;

    @Override
    public Result<TaskVO> getTask(Long taskId) {
        AlgorithmTask task = algorithmTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "任务不存在");
        }

        TaskVO vo = TaskVO.builder()
                .taskId(task.getId())
                .taskType(task.getTaskType())
                .status(task.getStatus())
                .progress(task.getProgress())
                .message(task.getMessage())
                .resultId(task.getResultId())
                .errorMessage(task.getErrorMessage())
                .createTime(task.getCreateTime())
                .updateTime(task.getUpdateTime())
                .build();
        return Result.ok(vo);
    }
}
