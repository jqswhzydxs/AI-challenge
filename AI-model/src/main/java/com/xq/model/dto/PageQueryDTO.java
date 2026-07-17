package com.xq.model.dto;

import lombok.Data;

/**
 * 通用分页查询 DTO.
 * <p>
 * 各接口的分页查询参数可复用此类或继承扩展.
 * </p>
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
public class PageQueryDTO {

    /** 页码，默认 1 */
    private Integer pageNum = 1;

    /** 每页条数，默认 10 */
    private Integer pageSize = 10;

    /** 日期筛选，格式 yyyy-MM-dd */
    private String date;

    /** 状态筛选 */
    private String status;

    /** 开始时间 */
    private String startTime;

    /** 结束时间 */
    private String endTime;

    /** 开始日期 */
    private String startDate;

    /** 结束日期 */
    private String endDate;

    /** 返回粒度，单位分钟 */
    private Integer interval;
}
