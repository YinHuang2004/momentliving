package com.momentliving.api.client;

import com.momentliving.api.config.FeignConfig;
import com.momentliving.result.Result;
import com.momentliving.vo.FootprintSettingVO;
import com.momentliving.vo.UserVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "user-service", configuration = FeignConfig.class)
public interface UserClient {
    @GetMapping("/user/feign/{id}")
    Result<UserVO> getUserInfo(@PathVariable("id") Long userId);
    @GetMapping("/user/feign/select")
    Result<List<UserVO>> selectUsersByIdsOrdered(@RequestParam("ids") List<Long> ids);
    @GetMapping("/user/feign/search")
    Result<List<UserVO>> searchUsers(@RequestParam("keyword") String keyword);

    /**
     * 某用户的足迹设置（可见开关 + 清空时间戳），voucher-service 判断他人足迹是否可见c
     */
    @GetMapping("/user/feign/footprint/{userId}")
    Result<FootprintSettingVO> getFootprintSettings(@PathVariable("userId") Long userId);
}
