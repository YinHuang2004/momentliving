package com.momentliving.api.interceptor;

import com.momentliving.context.AdminHolder;
import com.momentliving.context.MerchantHolder;
import com.momentliving.context.UserHolder;
import com.momentliving.vo.AdminVO;
import com.momentliving.vo.MerchantVO;
import com.momentliving.vo.UserVO;
import feign.RequestInterceptor;
import feign.RequestTemplate;

/**
 * 内部调用身份透传：把 ThreadLocal 里的用户/商家/管理员 ID 写进请求头。
 * 下游服务通过 @RequestHeader 或拦截器还原身份（内部网络信任模型，与网关透传 X-User-Id 一致）。
 */
public class FeignIdentityInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate requestTemplate) {
        UserVO user = UserHolder.getUser();
        if (user != null) {
            requestTemplate.header("X-User-Id", user.getId().toString());
        }
        MerchantVO merchant = MerchantHolder.getMerchant();
        if (merchant != null) {
            // 商家端服务发起的内部调用（商家核销），下游用 X-Merchant-Id 还原操作人，
            // 核销记录 voucher_verify.verify_by 记 merchant.id
            requestTemplate.header("X-Merchant-Id", merchant.getId().toString());
            // 🆕 商家绑定的店铺 ID：核销时做"券适用范围"校验 + 核销归属回填（全场/多店券记实际核销店铺）
            if (merchant.getShopId() != null) {
                requestTemplate.header("X-Merchant-Shop-Id", merchant.getShopId().toString());
            }
        }
        AdminVO admin = AdminHolder.getAdmin();
        if (admin != null) {
            // 管理员服务发起的内部调用（如审核类），下游用 X-Admin-Id 还原操作人
            requestTemplate.header("X-Admin-Id", admin.getId().toString());
        }
    }
}
