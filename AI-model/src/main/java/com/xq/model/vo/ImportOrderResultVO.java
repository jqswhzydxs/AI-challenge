package com.xq.model.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ImportOrderResultVO {

    private Integer totalCount;

    private Integer insertedCount;

    private Integer updatedCount;

    private Integer skippedCount;
}
