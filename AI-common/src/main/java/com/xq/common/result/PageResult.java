package com.xq.common.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 分页响应封装.
 * <p>
 * 配合前端分页列表使用，统一放在 {@link Result#data} 内.
 * </p>
 *
 * @param <T> 记录类型
 * @author XQ
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> implements Serializable {

    /** 总记录数 */
    private long total;

    /** 当前页码 */
    private int pageNum;

    /** 每页条数 */
    private int pageSize;

    /** 当前页记录列表 */
    private List<T> records;

    /** 快捷构造分页结果. */
    public static <T> PageResult<T> of(long total, int pageNum, int pageSize, List<T> records) {
        return new PageResult<>(total, pageNum, pageSize, records);
    }
}
