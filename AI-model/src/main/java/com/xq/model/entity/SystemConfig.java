package com.xq.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统配置实体.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@TableName("system_config")
public class SystemConfig {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 配置键 */
    private String configKey;
    /** 配置值 */
    private String configValue;
    /** 配置名称 */
    private String configName;
    /** 配置分组 */
    private String configGroup;
    /** 是否可编辑：0 否，1 是 */
    private Integer editable;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    private Long createBy;
    private Long updateBy;
    @TableLogic
    private Integer deleted;
    private String remark;
}
