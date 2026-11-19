package com.momentliving.ai.common;

/**
 * 当前请求身份（从网关透传的身份头解析，一次解析全程传递）。
 *
 * <p>⚠️ 与 Python 版 ContextVar 的区别：工具调用发生在 Reactor 流水线线程上，
 * ThreadLocal 会失效，因此这里用「显式传参 + Spring AI toolContext」做身份传播，
 * 从机制上杜绝了「AI 决定查谁」的越权问题。
 *
 * <p>头优先级与 Python deps.py 一致：X-Admin-Id &gt; X-Merchant-Id &gt; X-User-Id。
 *
 * @param userId     C 端用户 id（网关 AuthGlobalFilter 校验 JWT 后写入）
 * @param merchantId 商家端 id
 * @param adminId    平台管理员 id
 */
public record UserIdentity(Long userId, Long merchantId, Long adminId) {

    public static final String HEADER_USER = "X-User-Id";
    public static final String HEADER_MERCHANT = "X-Merchant-Id";
    public static final String HEADER_ADMIN = "X-Admin-Id";

    /** 解析请求头构造身份（三个头都可以缺省）。 */
    public static UserIdentity of(String userHeader, String merchantHeader, String adminHeader) {
        return new UserIdentity(parse(userHeader), parse(merchantHeader), parse(adminHeader));
    }

    private static Long parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException("身份头格式错误");
        }
    }

    /** 对话身份：商家优先（与 Python get_user_id 一致），都没有 → 未登录。 */
    public long chatUserId() {
        if (merchantId != null) {
            return merchantId;
        }
        if (userId != null) {
            return userId;
        }
        throw new BusinessException("未登录");
    }

    /** 对话用户类型：1=C 端 2=商家（与 ai_conversation.user_type 对齐）。 */
    public int chatUserType() {
        return merchantId != null ? 2 : 1;
    }

    /** C 端/商家通用登录校验（与 Python get_user_id 语义一致）。 */
    public UserIdentity requireChatUser() {
        chatUserId();
        return this;
    }

    /** 管理员校验（知识库管理端）。 */
    public long requireAdmin() {
        if (adminId == null) {
            throw new BusinessException("仅平台管理员可管理知识库");
        }
        return adminId;
    }

    /** 商家校验（B 端经营助手）。 */
    public long requireMerchant() {
        if (merchantId == null) {
            throw new BusinessException("请先登录商家端");
        }
        return merchantId;
    }
}
