package com.momentliving.controller;

import com.momentliving.api.client.UserClient;
import com.momentliving.api.client.VoucherClient;
import com.momentliving.context.MerchantHolder;
import com.momentliving.entity.Merchant;
import com.momentliving.entity.VoucherOrder;
import com.momentliving.exception.BadRequestException;
import com.momentliving.mapper.MerchantMapper;
import com.momentliving.result.Result;
import com.momentliving.vo.MerchantStatsVO;
import com.momentliving.vo.MerchantVO;
import com.momentliving.vo.RecentVerifyVO;
import com.momentliving.vo.UserVO;
import com.momentliving.vo.VerifyOrderPreviewVO;
import com.momentliving.vo.VerifyRecordsVO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 商家核销接口（/merchant/verify/**，从原 VerifyController /admin/verify/** 平移）：
 * 用户到店出示核销码 → 商家（merchant 登录态）输入核销码完成验券。
 * 业务逻辑在 voucher-service（订单状态机 + 核销记录同库同事务），
 * 本服务只做身份代理：FeignIdentityInterceptor 把 MerchantHolder 里的 merchantId
 * 以 X-Merchant-Id 头透传给下游，核销记录 verify_by 登记的就是实际操作的商家账号（merchant.id）。
 */
@RestController
@RequestMapping("/merchant/verify")
public class MerchantVerifyController {

    @Resource
    private VoucherClient voucherClient;
    @Resource
    private UserClient userClient;
    @Resource
    private MerchantMapper merchantMapper;

    /**
     * 商家核销券码。
     *
     * @param verifyCode 用户出示的 16 位核销码
     */
    @PostMapping
    public Result<Void> verify(@RequestParam("code") String verifyCode) {
        return voucherClient.verifyByCode(verifyCode);
    }

    /**
     * 商家查询订单详情（按订单 ID，内部核对用）。
     */
    @GetMapping("/order/{orderId}")
    public Result<VoucherOrder> queryOrder(@PathVariable("orderId") Long orderId) {
        return voucherClient.getOrder(orderId);
    }

    /**
     * 按核销码查订单预览（核销第一步"核对"：券名/买家/金额/状态，只读不改状态）。
     */
    @GetMapping("/preview/{code}")
    public Result<VerifyOrderPreviewVO> previewByCode(@PathVariable("code") String code) {
        Result<VerifyOrderPreviewVO> res = voucherClient.previewByCode(code);
        VerifyOrderPreviewVO preview = res == null ? null : res.getData();
        if (preview != null && preview.getUserId() != null) {
            // 买家昵称回填（查不到时留空，前端兜底展示 ID）
            Result<List<UserVO>> usersRes = userClient.selectUsersByIdsOrdered(List.of(preview.getUserId()));
            if (usersRes != null && usersRes.getData() != null && !usersRes.getData().isEmpty()) {
                preview.setNickName(usersRes.getData().get(0).getNickName());
            }
        }
        return res;
    }

    /**
     * 商家端工作台统计：今日待核销/已核销/营收 + 最近核销列表。
     * 店铺维度：不传 shopId 时取当前登录商家账号绑定的店铺（merchant.shop_id）；
     * 买家昵称由本服务编排 user-service 批量回填（voucher-service 与 user 表不同库）。
     */
    @GetMapping("/stats")
    public Result<MerchantStatsVO> stats(@RequestParam(value = "shopId", required = false) Long shopId) {
        MerchantStatsVO stats = voucherClient.statsByShop(resolveShopId(shopId)).getData();
        if (stats == null) {
            return Result.success(stats);
        }
        fillNickNames(stats.getRecent());
        return Result.success(stats);
    }

    /**
     * 商家端核销记录分页（按店铺 + 状态筛选，核销时间倒序）。
     *
     * @param status 核销状态：0未核销 1已核销 2已作废，null 表示全部
     */
    @GetMapping("/records")
    public Result<VerifyRecordsVO> records(@RequestParam(value = "shopId", required = false) Long shopId,
                                           @RequestParam(value = "status", required = false) Integer status,
                                           @RequestParam(value = "current", defaultValue = "1") Integer current,
                                           @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        VerifyRecordsVO records = voucherClient
                .recordsByShop(resolveShopId(shopId), status, current, pageSize).getData();
        if (records != null) {
            fillNickNames(records.getList());
        }
        return Result.success(records);
    }

    /** 核销店铺归属：显式传参优先，否则取当前登录商家账号绑定的店铺 */
    private Long resolveShopId(Long shopId) {
        if (shopId != null) {
            return shopId;
        }
        MerchantVO merchant = MerchantHolder.getMerchant();
        if (merchant == null || merchant.getId() == null) {
            throw new BadRequestException("未登录");
        }
        Merchant merchantEntity = merchantMapper.selectById(merchant.getId());
        Long boundShopId = merchantEntity == null ? null : merchantEntity.getShopId();
        if (boundShopId == null) {
            throw new BadRequestException("当前账号未绑定店铺，无法查看工作台");
        }
        return boundShopId;
    }

    /** 批量回填核销记录的买家昵称（user-service 按用户 id 批查） */
    private void fillNickNames(List<RecentVerifyVO> recent) {
        if (recent == null || recent.isEmpty()) {
            return;
        }
        List<Long> userIds = recent.stream()
                .map(RecentVerifyVO::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (userIds.isEmpty()) {
            return;
        }
        Result<List<UserVO>> usersRes = userClient.selectUsersByIdsOrdered(userIds);
        if (usersRes != null && usersRes.getData() != null) {
            Map<Long, String> nickMap = usersRes.getData().stream()
                    .filter(u -> u != null && u.getId() != null)
                    .collect(Collectors.toMap(UserVO::getId, UserVO::getNickName, (a, b) -> a));
            recent.forEach(r -> r.setNickName(nickMap.get(r.getUserId())));
        }
    }
}
