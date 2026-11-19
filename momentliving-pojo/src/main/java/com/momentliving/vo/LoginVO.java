package com.momentliving.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 登录成功返回 VO:双 Token(Access/Refresh) + 用户脱敏信息 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LoginVO {
    private String token;         // AccessToken
    private String refreshToken;  // RefreshToken
    private UserVO userInfo;     // 用户脱敏信息
}