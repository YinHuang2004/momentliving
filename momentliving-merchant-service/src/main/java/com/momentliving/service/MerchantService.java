package com.momentliving.service;

import com.momentliving.dto.MerchantLoginDTO;
import com.momentliving.dto.MerchantUpdateDTO;
import com.momentliving.result.Result;
import com.momentliving.vo.MerchantVO;

public interface MerchantService {

    /** 商家登录：用户名+密码（BCrypt），登录态存 login:merchant:{id} */
    Result<MerchantVO> login(MerchantLoginDTO dto);

    /** 商家退出：失效 login:merchant:{id} */
    void logout();

    /** 当前登录商家（拦截器只塞 id，回查 DB 补全） */
    Result<MerchantVO> me();

    /** 修改当前商家个人信息（姓名/手机号/头像，可选改密），返回更新后的信息 */
    Result<MerchantVO> updateMe(MerchantUpdateDTO dto);
}
