package com.momentliving.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 用户脱敏视图对象:仅 id/昵称/头像,不含密码等敏感字段 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserVO {
    private Long id;
    private String nickName;
    private String images;
}
