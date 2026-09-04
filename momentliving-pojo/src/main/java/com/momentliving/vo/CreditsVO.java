package com.momentliving.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户积分信息（当前登录用户）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditsVO {
    /** 当前总积分（user_info.credits） */
    private Integer credits;
    /** 今日是否已领取每日积分 */
    private Boolean claimedToday;
}
