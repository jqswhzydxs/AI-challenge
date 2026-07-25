package com.xq.model.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 电价政策 VO.
 *
 * @author XQ
 * @since 1.0.0
 */
@Data
@Builder
public class PricePolicyVO {

    /** 电价模式 */
    private String mode;

    /** 时段列表 */
    private List<PricePolicyPeriodVO> periods;
}
