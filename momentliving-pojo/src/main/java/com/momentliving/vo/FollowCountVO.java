package com.momentliving.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户关注数据统计：关注数 / 粉丝数（个人主页展示用）
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FollowCountVO {
    /** 关注数（我关注的人） */
    private Long followee;
    /** 粉丝数（关注我的人） */
    private Long follower;
}
