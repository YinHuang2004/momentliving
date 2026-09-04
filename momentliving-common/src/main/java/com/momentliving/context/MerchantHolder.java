package com.momentliving.context;

import com.momentliving.vo.MerchantVO;

/**
 * ThreadLocal 商家上下文（/merchant/** 专用，与 AdminHolder/UserHolder 隔离）
 * 商家账号体系已从 admin 表拆到 merchant 表，登录态存 login:merchant:{id}
 */
public class MerchantHolder {
    private static final ThreadLocal<MerchantVO> tl = new ThreadLocal<>();

    public static void saveMerchant(MerchantVO merchant) {
        tl.set(merchant);
    }

    public static MerchantVO getMerchant() {
        return tl.get();
    }

    public static void removeMerchant() {
        tl.remove();
    }
}
