package com.momentliving.filter;

import cn.hutool.core.util.StrUtil;
import com.momentliving.constant.RedisConstants;
import com.momentliving.properties.InternalAuthProperties;
import com.momentliving.properties.JwtProperties;
import com.momentliving.security.InternalRequestSigner;
import com.momentliving.utils.JwtUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * ★ 网关统一鉴权过滤器（GlobalFilter：对所有路由生效）
 *
 * 📌 核心职责（按 filter() 方法的执行顺序）：
 * 1. 进门先剥离客户端伪造的内部身份头（X-User-Id 等只允许由本过滤器签名写入）
 * 2. 白名单放行（精确 → 前缀 → GET 方法感知 三级匹配）
 * 3. 解析 JWT AccessToken → 获取 userId（自包含身份，无需查库）
 * 4. Redis 三级登录态回查（login:refresh → login:admin → login:merchant，获得服务端吊销能力）
 * 5. 滑动续期（仅用户态，活跃用户 7 天不过期；管理端/商家端不续期）
 * 6. HMAC 签名身份头后放行（下游 InternalRequestVerifier 验签后才信任 X-User-Id）
 *
 * 📌 为什么进出都要动身份头：
 * 进门剥离——白名单路径不做鉴权，若不剥离，攻击者打白名单接口时可以自带
 * X-User-Id 伪造任意身份（下游拦截器会信任这些头）；
 * 出门签名——方法+路径+时间戳+nonce+身份 拼成 canonical 串做 HmacSHA256，
 * 下游用同一 secret 重算并常量时间比对，60 秒时钟窗防重放，
 * 即使内网被攻破也不能裸信 header。
 */
@Component
@Slf4j
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    /** 管理端 Token 可访问的路径边界（不是白名单：命中仍要求 login:admin:{id} 存在且 Token 一致） */
    private static final List<String> ADMIN_MANAGE_PREFIX = List.of(
            "/admin", "/shop", "/voucher", "/file", "/ai");
    /** 商家端模式仅放行 /merchant/**（商家登录态只对商家端接口有效） */
    private static final String MERCHANT_PREFIX = "/merchant/";
    /** AI 商家助手（/ai/merchant/**）也走商家态：商家 token 换 X-Merchant-Id 透传给 ai-service */
    private static final String MERCHANT_AI_PREFIX = "/ai/merchant/";

    private final JwtProperties jwtProperties;
    private final InternalAuthProperties internalAuthProperties;
    private final StringRedisTemplate stringRedisTemplate;

    public AuthGlobalFilter(JwtProperties jwtProperties,
                            InternalAuthProperties internalAuthProperties,
                            StringRedisTemplate stringRedisTemplate) {
        this.jwtProperties = jwtProperties;
        this.internalAuthProperties = internalAuthProperties;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // ========== 1. 进门先剥离客户端伪造的内部身份头 ==========
        // 注意：剥离后重建 exchange，后续所有判断都基于"干净"的请求
        ServerHttpRequest cleanRequest = stripInternalHeaders(exchange.getRequest());
        ServerWebExchange cleanExchange = exchange.mutate().request(cleanRequest).build();
        String path = cleanRequest.getURI().getPath();
        HttpMethod method = cleanRequest.getMethod();

        // ========== 2. 三级白名单（精确 → 前缀 → GET 方法感知） ==========
        // 白名单路径不做任何鉴权，但身份头已在第 1 步剥离，下游拿到的永远是"干净"请求
        if (isPublic(path, method)) {
            log.debug("白名单放行: {}", path);
            return chain.filter(cleanExchange);
        }

        // ========== 3. 校验 Token 是否携带 + JWT 验签解析 userId ==========
        // 签名校验 + 过期校验都在 JwtUtils 内完成，伪造/过期的 token 会抛异常
        // 三种账号的 JWT payload 都是 userId，无 role claim，身份靠第 4 步的 Redis key 分流
        String token = cleanRequest.getHeaders().getFirst(jwtProperties.getTokenName());
        if (StrUtil.isBlank(token)) {
            log.warn("未携带 Token: {}", path);
            return unauthorized(cleanExchange, "未登录，请先登录");
        }
        Long userId;
        try {
            userId = JwtUtils.getUserId(token, jwtProperties.getSecret());
        } catch (Exception e) {
            log.warn("Token 解析失败: {} -> {}", path, e.getMessage());
            return unauthorized(cleanExchange, "Token 无效或已过期");
        }

        // ========== 4. Redis 三级登录态回查（吊销能力的关键一步） ==========
        // JWT 是无状态的，单看签名无法"作废"；本步让网关获得服务端吊销能力：
        // 改密码 / 找回密码 / 退出登录会删除 login:refresh:{userId}，
        // 该用户所有已签发 AccessToken 随即在网关 401，无需等 JWT 自然过期。
        // 用户态：key 存的值是 RefreshToken，与请求携带的 AccessToken 本就不同值，
        //         所以只判断"存在与否"，不比对值（允许多端登录并存）；
        // 管理端/商家端：key 存的就是 AccessToken 本身，必须与请求头完全相等
        //         （防串号），且请求路径必须在对应前缀边界内——三重条件缺一不可。
        String userLogin = stringRedisTemplate.opsForValue()
                .get(RedisConstants.LOGIN_USER_KEY + userId);
        boolean adminMode = false;
        boolean merchantMode = false;
        if (StrUtil.isBlank(userLogin)) {
            adminMode = hitAdminLogin(userId, token, path);
            if (!adminMode) {
                merchantMode = hitMerchantLogin(userId, token, path);
            }
            if (!adminMode && !merchantMode) {
                return unauthorized(cleanExchange, "登录已过期，请重新登录");
            }
            log.debug("管理端/商家端模式放行: id={}, admin={}, merchant={}, path={}",
                    userId, adminMode, merchantMode, path);
        }

        // ========== 5. 滑动续期（仅用户态） ==========
        // 活跃用户每次过网关把 login:refresh:{userId} 续到 7 天（被动续期），
        // 闲置 7 天后 key 过期、登录态失效；管理端/商家端 Token 短时效不续期（见文档 Q7）
        if (!adminMode && !merchantMode) {
            stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + userId, Duration.ofDays(7));
        }

        // ========== 6. HMAC 签名身份头后放行 ==========
        // 身份头只允许由本过滤器签名写入：下游 InternalRequestVerifier 用同一 secret
        // 重算签名并常量时间比对，60 秒时钟窗外即作废——内网也不能裸信 header
        return chain.filter(withSignedIdentity(cleanExchange, userId, adminMode, merchantMode));
    }

    /** 管理端回查：路径在管理前缀边界内 + login:admin:{id} 存在 + Redis 值与请求 Token 完全相等 */
    private boolean hitAdminLogin(Long userId, String token, String path) {
        if (ADMIN_MANAGE_PREFIX.stream().noneMatch(path::startsWith)) {
            return false;
        }
        String adminToken = stringRedisTemplate.opsForValue()
                .get(RedisConstants.LOGIN_ADMIN_KEY + userId);
        return StrUtil.isNotBlank(adminToken) && adminToken.equals(token);
    }

    /** 商家端回查：路径为 /merchant/** 或 /ai/merchant/** + login:merchant:{id} 存在 + 值完全相等 */
    private boolean hitMerchantLogin(Long userId, String token, String path) {
        if (!path.startsWith(MERCHANT_PREFIX) && !path.startsWith(MERCHANT_AI_PREFIX)) {
            return false;
        }
        String merchantToken = stringRedisTemplate.opsForValue()
                .get(RedisConstants.LOGIN_MERCHANT_KEY + userId);
        return StrUtil.isNotBlank(merchantToken) && merchantToken.equals(token);
    }

    /** 三级白名单：①精确 Set ②前缀 List ③GET 方法感知（读接口放行、写接口拦下） */
    private boolean isPublic(String path, HttpMethod method) {
        // ① 精确白名单：路径完全相等才放行。登录类端点是"一个动作"而非"一段资源"，
        //    /pay/alipay/notify 只有这一个无登录态端点，安全靠 RSA2 验签 + 金额一致性校验
        if (EXACT_WHITE_LIST.contains(path)) {
            return true;
        }
        // ② 前缀白名单：整段工具型资源（Swagger/API 文档/WS 握手）
        if (PREFIX_WHITE_LIST.stream().anyMatch(path::startsWith)) {
            return true;
        }
        // ③ 方法感知：只放行 GET 读接口（/shop/favorite 是用户态写操作，排除在外）
        if (!HttpMethod.GET.equals(method)) {
            return false;
        }
        return (path.startsWith("/shop/") && !path.startsWith("/shop/favorite"))
                || path.startsWith("/shop-type")
                || path.startsWith("/voucher/")
                || path.startsWith("/review/");
    }

    /** 精确白名单：前缀匹配会误伤的"单动作"端点 */
    private static final Set<String> EXACT_WHITE_LIST = Set.of(
            "/user/code",           // 发送验证码
            "/user/login",          // 登录
            "/user/refresh",        // 刷新 Token（RefreshToken 自证身份）
            "/user/password/reset", // 找回密码（邮箱验证码自证身份；/user/password 改密码需登录）
            "/admin/login",         // 管理端登录
            "/merchant/login",      // 商家登录
            "/merchant/apply",      // 商家入驻申请：未登录提交，审核通过后才生成账号
            "/pay/alipay/notify",   // 支付宝异步回调：无登录态，安全靠 RSA2 验签
            "/blog/hot",            // 热门博客
            "/doc.html",            // Knife4j 文档
            "/favicon.ico"          // 浏览器图标
    );

    /** 前缀白名单：整段工具型资源 */
    private static final List<String> PREFIX_WHITE_LIST = List.of(
            "/swagger-ui",          // Swagger UI
            "/v3/api-docs",         // OpenAPI 规范
            "/ws/"                  // WS 握手（token 在 query，由 chat-service 握手拦截器校验）
    );

    /** 进门剥离：内部身份头共 7 个，一个都不许从外部带进来 */
    private ServerHttpRequest stripInternalHeaders(ServerHttpRequest request) {
        return request.mutate().headers(headers -> {
            headers.remove(InternalRequestSigner.USER_ID);
            headers.remove(InternalRequestSigner.ADMIN_ID);
            headers.remove(InternalRequestSigner.MERCHANT_ID);
            headers.remove(InternalRequestSigner.MERCHANT_SHOP_ID);
            headers.remove(InternalRequestSigner.TIMESTAMP);
            headers.remove(InternalRequestSigner.NONCE);
            headers.remove(InternalRequestSigner.SIGNATURE);
        }).build();
    }

    /** 出门签名：按身份类型写入对应身份头 + 时间戳/nonce/HMAC 签名 */
    private ServerWebExchange withSignedIdentity(ServerWebExchange exchange, Long userId,
                                                 boolean adminMode, boolean merchantMode) {
        ServerHttpRequest request = exchange.getRequest();
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        // 三种身份互斥：只有命中的那一端写入自己的身份头，其余传 null（canonical 串中为空串）
        String uid = (!adminMode && !merchantMode) ? userId.toString() : null;
        String adminId = adminMode ? userId.toString() : null;
        String merchantId = merchantMode ? userId.toString() : null;
        String signature = InternalRequestSigner.sign(
                internalAuthProperties.requiredSecret(), request.getMethod().name(),
                request.getURI().getPath(), timestamp, nonce, uid, adminId, merchantId, null);

        ServerHttpRequest signed = request.mutate().headers(headers -> {
            if (uid != null) headers.set(InternalRequestSigner.USER_ID, uid);
            if (adminId != null) headers.set(InternalRequestSigner.ADMIN_ID, adminId);
            if (merchantId != null) headers.set(InternalRequestSigner.MERCHANT_ID, merchantId);
            headers.set(InternalRequestSigner.TIMESTAMP, timestamp);
            headers.set(InternalRequestSigner.NONCE, nonce);
            headers.set(InternalRequestSigner.SIGNATURE, signature);
        }).build();
        return exchange.mutate().request(signed).build();
    }

    /** 401 统一返回：业务服务解析 Result{code,msg,data} */
    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().set("Content-Type", "application/json;charset=UTF-8");
        String body = "{\"code\":401,\"msg\":\"" + message + "\",\"data\":null}";
        return response.writeWith(Mono.just(response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8))));
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
