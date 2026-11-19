package com.momentliving.dto;

import lombok.Data;

import java.util.List;

/** 滚动分页结果 DTO:数据列表 + 本批最小时间戳与偏移量,供下次续查(Feed 流滚动分页用) */
@Data
public class ScrollResult {
    private List<?> list;
    private Long minTime;
    private Integer offset;
}
