package com.momentliving.api.interceptor;

import com.momentliving.context.AdminHolder;
import com.momentliving.context.MerchantHolder;
import com.momentliving.context.UserHolder;
import com.momentliving.properties.InternalAuthProperties;
import com.momentliving.security.InternalRequestSigner;
import com.momentliving.vo.AdminVO;
import com.momentliving.vo.MerchantVO;
import com.momentliving.vo.UserVO;
import feign.RequestInterceptor;
import feign.RequestTemplate;

import java.time.Instant;
import java.util.UUID;

/** Feign 内部调用透传身份时同时附加 HMAC，避免下游盲目信任身份头。 */
public class FeignIdentityInterceptor implements RequestInterceptor {
    private final InternalAuthProperties properties;

    public FeignIdentityInterceptor(InternalAuthProperties properties) {
        this.properties = properties;
    }

    /** 把 ThreadLocal 中的用户/商家/管理员身份写入内部请求头,并附时间戳、nonce 与 HMAC 签名;无身份则直接放行 */
    @Override
    public void apply(RequestTemplate template) {
        UserVO user = UserHolder.getUser();
        MerchantVO merchant = MerchantHolder.getMerchant();
        AdminVO admin = AdminHolder.getAdmin();

        String userId = user == null ? null : user.getId().toString();
        String merchantId = merchant == null ? null : merchant.getId().toString();
        String merchantShopId = merchant == null || merchant.getShopId() == null
                ? null : merchant.getShopId().toString();
        String adminId = admin == null ? null : admin.getId().toString();
        if (userId == null && merchantId == null && adminId == null) {
            return;
        }

        set(template, InternalRequestSigner.USER_ID, userId);
        set(template, InternalRequestSigner.MERCHANT_ID, merchantId);
        set(template, InternalRequestSigner.MERCHANT_SHOP_ID, merchantShopId);
        set(template, InternalRequestSigner.ADMIN_ID, adminId);

        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        String signature = InternalRequestSigner.sign(properties.requiredSecret(), template.method(),
                template.path(), timestamp, nonce, userId, adminId, merchantId, merchantShopId);
        set(template, InternalRequestSigner.TIMESTAMP, timestamp);
        set(template, InternalRequestSigner.NONCE, nonce);
        set(template, InternalRequestSigner.SIGNATURE, signature);
    }

    private void set(RequestTemplate template, String name, String value) {
        template.removeHeader(name);
        if (value != null) {
            template.header(name, value);
        }
    }
}
