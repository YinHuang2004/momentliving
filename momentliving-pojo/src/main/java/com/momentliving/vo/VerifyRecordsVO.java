package com.momentliving.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 商家端核销记录分页结果
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class VerifyRecordsVO {
    /** 总条数（按筛选条件） */
    private Long total;
    /** 当前页记录（买家昵称由 admin-service 编排 user-service 回填） */
    private List<RecentVerifyVO> list;
}
