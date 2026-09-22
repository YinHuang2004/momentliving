package com.momentliving.filter;

import cn.hutool.core.util.StrUtil;
import com.momentliving.constant.RedisConstants;
import com.momentliving.properties.JwtProperties;
import com.momentliving.utils.JwtUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * ★ 网关统一鉴权过滤器（GlobalFilter：对所有路由生效）
 *
 * 📌 核心职责（按 filter() 方法的执行顺序）：
 * 1. 白名单放行（无需登录的接口：精确 → 前缀 → 方法感知 三级匹配）
 * 2. 解析 JWT AccessToken → 获取 userId（自包含身份，无需查库）
 * 3. 校验 Redis 登录态（login:refresh:{userId} 是否存活）
 *    —— 用户态缺失时降级尝试管理端 / 商家端登录态（一套过滤器服务三种账号）
 * 4. 滑动续期 RefreshToken（活跃用户 7 天不过期，休眠 7 天后真正失效）
 * 5. 把 userId 写入请求头（X-User-Id / X-Admin-Id / X-Merchant-Id），透传给下游微服务
 *
 * 📌 登录态吊销模型（改密码 / 找回密码 / 退出登录都依赖这一点）：
 * JWT 本身是无状态的，签发后在有效期内"无法作废"；本项目通过
 * "网关每请求回查 Redis 键是否存在"来获得服务端吊销能力——
 * 删除 login:refresh:{userId} 即可让该用户所有已签发的 AccessToken 立即 401，
 * 不需要等 JWT 自然过期。user-service 的改密码/找回密码正是利用这一点实现全端强制下线。
 *
 * ⚠️ 基于 WebFlux（响应式），不能用传统的 HttpServletRequest/Response！
 *
 * ⚠️ 已知设计取舍（评审记录，暂不改动）：
 * 1. /shop/ 整段前缀白名单只应该覆盖"查询"，但当前 DELETE/PUT /shop/** 也会被匿名放行，
 *    依赖下游 shop-service 自己兜底——建议后续改成方法感知（同 /voucher/ 的 GET-only 写法）；
 * 2. /admin/ 整段放行是刻意为之（管理后台自己有 AdminAuthInterceptor 二次鉴权），
 *    但新增管理端接口时必须记得在下游配拦截器，属于"约定式安全"；
 * 3. 401 响应体是 {success,message}，与业务统一的 Result{code,msg,data} 不一致，
 *    前端已做双格式兼容；如要统一需网关和前端同步改，影响面大。
 */
@Component
@Slf4j
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    @Resource
    private JwtProperties jwtProperties;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * ★ 精确白名单：路径完全相等才放行。
     * 用于"前缀匹配会误伤"的路径（如 /pay/ 下的用户接口都需要登录，
     * 只有回调这一个无用户态端点由渠道验签保护）。
     */
    private static final Set<String> EXACT_WHITE_LIST = Set.of(
            "/pay/alipay/notify",  // 支付宝异步回调：无登录态，安全靠 RSA2 验签 + 金额一致性校验
            "/blog/hot",           // 热门博客
            "/doc.html",           // Knife4j 文档
            "/favicon.ico"         // 浏览器图标
    );

    /**
     * ★ 前缀白名单：startsWith 匹配。
     * 注意带斜杠的写法：/shop/ 匹配 /shop/1 但不匹配 /shop-xxx；
     * /shop-type 无斜杠（其下还有 /shop-type/list 一类子路径）。
     */
    private static final List<String> PREFIX_WHITE_LIST = List.of(
            "/user/code",           // 发送验证码
            "/user/login",          // 登录
            "/user/refresh",        // 刷新 Token（RefreshToken 自证身份，不需要登录态）
            "/user/password/reset", // 🆕 找回密码（邮箱验证码自证身份；注意 /user/password 改密码不在白名单，需登录）
            "/shop/",               // 商铺查询
            "/shop-type",
            "/admin/",              // 管理端：网关放行，由 admin-service 内部 AdminAuthInterceptor 独立 JWT 鉴权
            "/merchant/login",      // 商家登录（拆分后商家接口归 /merchant/**，公开端点在网关放行）
            "/merchant/apply",      // 商家入驻申请：未登录提交，审核通过后才生成账号
            "/swagger-ui",          // Swagger UI
            "/v3/api-docs",         // OpenAPI 规范
            // 🆕 WS 握手放行：WebSocket 握手是 HTTP Upgrade 请求，GlobalFilter 对它同样生效，
            // 但握手无法携带 Authorization 头（token 在 query），故在 chat-service 的
            // ChatHandshakeInterceptor 做 JWT + Redis 登录态校验，安全性不打折
            "/ws/"
    );

    /**
     * ★ 管理端 token 可访问的前缀（管理后台 Web 用）：
     * 用户态校验失败时回查 login:admin:{id}，命中才放行——
     * 店铺/分类/券的写操作与文件上传，此前仅用户 token 可通过网关。
     */
    private static final List<String> ADMIN_MANAGE_PREFIX = List.of(
            "/shop",        // 店铺 CRUD（含 /shop-type 分类，前缀覆盖）
            "/voucher",     // 券上架/编辑/下架（GET 查询本就在方法白名单）
            "/file",        // 店铺图/券图上传删除
            "/ai"           // 🆕 AI 知识库管理（ai-service 内部再校验 AdminHolder，非管理员 403）
    );

    /** ★ 商家端模式仅放行 /merchant/**：商家登录态只对商家端接口有效 */
    private static final String MERCHANT_PREFIX = "/merchant/";

    // 🆕 AI 商家助手（/ai/merchant/**）也走商家态：商家 token 换 X-Merchant-Id 透传给 ai-service
    private static final String MERCHANT_AI_PREFIX = "/ai/merchant/";

    /**
     * ★ 方法感知白名单：/voucher/ 前缀仅放行 GET（券查询）。
     * 修复历史问题：此前 POST /voucher（上架券）、PUT/DELETE 均被前缀匹配匿名放行。
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // ========== 1. 获取请求路径与方法 ==========
        // 注意：这里拿的是路由匹配前的原始路径（Path=/user/** 一类），与白名单同口径
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        boolean isGet = HttpMethod.GET.equals(request.getMethod());

        // ========== 2. 白名单判断（三级：精确 → 前缀 → 方法感知） ==========
        // 🆕 /shop/favorite/** 例外：店铺收藏是用户态写操作，虽在 /shop/ 前缀下，
        //    仍强制走登录解析并透传 X-User-Id（否则下游拿不到收藏人是谁）
        boolean shopFavoritePath = path.startsWith("/shop/favorite");
        if (!shopFavoritePath && (EXACT_WHITE_LIST.contains(path)
                || PREFIX_WHITE_LIST.stream().anyMatch(path::startsWith)
                || (isGet && path.startsWith("/voucher/")))) {
            log.debug("白名单放行: {}", path);
            // ★ 防身份头伪造：白名单路径不做任何鉴权，但绝不能把客户端自带的
            //   X-User-Id / X-Admin-Id / X-Merchant-Id 原样放过去——
            //   下游服务的 UserContextInterceptor 等会直接信任这些头，
            //   不剥离的话，攻击者打白名单接口（如 GET /shop/**）时可以伪造任意用户身份。
            //   这里统一剥离后再放行，身份头只允许由本过滤器在鉴权通过后注入。
            ServerHttpRequest cleanRequest = request.mutate()
                    .headers(h -> {
                        h.remove("X-User-Id");
                        h.remove("X-Admin-Id");
                        h.remove("X-Merchant-Id");
                    })
                    .build();
            return chain.filter(exchange.mutate().request(cleanRequest).build());
        }

        // ========== 3. 校验 Authorization 头是否存在 ==========
        // 白名单之外的路径必须带 token（前端放 Authorization: <JWT>，无 Bearer 前缀）
        String tokenHeader = request.getHeaders().getFirst(
                jwtProperties.getTokenName()  // "authorization"
        );
        if (StrUtil.isBlank(tokenHeader)) {
            log.warn("未携带 Token: {}", path);
            return unauthorized(exchange, "未登录，请先登录");
        }

        // ========== 4. 解析 JWT 拿 userId ==========
        // 签名校验 + 过期校验都在 JwtUtils 内完成，伪造/过期的 token 会抛异常
        // 注意：用户/管理员/商家三种账号的 JWT payload 都是 userId，无 role claim，
        //       三种身份靠第 5 步的 Redis key（login:refresh / login:admin / login:merchant）分流
        Long userId;
        try {
            userId = JwtUtils.getUserId(tokenHeader, jwtProperties.getSecret());
        } catch (Exception e) {
            log.warn("Token 解析失败: {} -> {}", path, e.getMessage());
            return unauthorized(exchange, "Token 无效或已过期");
        }

        // ========== 5. 查 Redis 校验登录态（吊销能力的关键一步） ==========
        // JWT 是无状态的，单看签名无法"作废"；这一步让网关获得服务端吊销能力：
        // 改密码 / 找回密码 / 退出登录会删除 login:refresh:{userId}，
        // 从此所有已签发的该用户 AccessToken 在网关直接 401，无需等 JWT 自然过期。
        String refreshToken = stringRedisTemplate.opsForValue()
                .get(RedisConstants.LOGIN_USER_KEY + userId);
        boolean adminMode = false;
        boolean merchantMode = false;
        //如果用户态消失了也就是用户主动退出登录了,判断是否是管理员或者商家
        if (StrUtil.isBlank(refreshToken)) {
            // ★ 管理端模式：用户态缺失时回查 login:admin:{id}，命中需同时满足：
            //   ① Redis 存的 token 与请求头完全一致（防串号）② 路径属于管理类前缀——
            //   三重条件缺一不可，避免管理员 token 被拿去访问普通用户接口
            String adminToken = stringRedisTemplate.opsForValue()
                    .get(RedisConstants.LOGIN_ADMIN_KEY + userId);
            if (StrUtil.isNotBlank(adminToken) && adminToken.equals(tokenHeader)
                    && ADMIN_MANAGE_PREFIX.stream().anyMatch(path::startsWith)) {
                adminMode = true;
                log.debug("管理端模式放行: adminId={}, path={}", userId, path);
            } else {
                // ★ 商家端模式：用户态缺失时回查 login:merchant:{id}（仅限 /merchant/** 与 /ai/merchant/**），
                //   登录态由 merchant-service 登录时写入，网关只做同 key 校验并透传 X-Merchant-Id。
                //   商家 token 只对商家端接口有效，拿去访问用户接口会 401
                String merchantToken = stringRedisTemplate.opsForValue()
                        .get(RedisConstants.LOGIN_MERCHANT_KEY + userId);
                if (StrUtil.isNotBlank(merchantToken) && merchantToken.equals(tokenHeader)
                        && (path.startsWith(MERCHANT_PREFIX) || path.startsWith(MERCHANT_AI_PREFIX))) {
                    merchantMode = true;
                    log.debug("商家端模式放行: merchantId={}, path={}", userId, path);
                } else {
                    // 三种登录态都不命中：token 是伪造的，或登录态已被吊销（改密码/退出）
                    log.warn("登录态已过期: userId={}", userId);
                    return unauthorized(exchange, "登录已过期，请重新登录");
                }
            }
        }

        // ========== 6. 滑动续期 RefreshToken（活跃用户永不过期，休眠 7 天才真失效） ==========
        // 每次成功鉴权都把 TTL 重置为 7 天：只要用户持续活跃就不需要重新登录；
        // 管理端 token 固定 TTL 不续期（admin/merchant 登录态本身 TTL 就短，由登录服务控制）
        if (!adminMode) {
            stringRedisTemplate.expire(
                    RedisConstants.LOGIN_USER_KEY + userId,
                    Duration.ofDays(7)
            );
        }

        // ========== 7. 把身份写入请求头，透传给下游微服务 ==========
        // mutate().header() 的语义是"替换"（headers.put），所以客户端伪造的同名头在这里被覆盖；
        // 管理端模式用 X-Admin-Id、商家端模式用 X-Merchant-Id，与 Feign 内部透传头保持一致，
        // 下游服务统一从这些头取身份，绝不信任客户端原始头
        ServerHttpRequest mutatedRequest;
        if (adminMode) {
            mutatedRequest = request.mutate().header("X-Admin-Id", userId.toString()).build();
        } else if (merchantMode) {
            mutatedRequest = request.mutate().header("X-Merchant-Id", userId.toString()).build();
        } else {
            mutatedRequest = request.mutate().header("X-User-Id", userId.toString()).build();
        }

        // ========== 8. 把修改后的请求传给过滤器链 ==========
        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

        log.debug("鉴权通过: userId={}, path={}", userId, path);
        return chain.filter(mutatedExchange);
    }

    /**
     * 返回 401 未授权响应
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
        // ★ Gateway 响应式写 JSON
        String body = "{\"success\":false,\"message\":\"" + message + "\"}";
        return response.writeWith(Mono.just(
                response.bufferFactory().wrap(body.getBytes())
        ));
    }

    /**
     * ★ 过滤器执行顺序：值越小越先执行
     * 鉴权过滤器应在最前面，给后续过滤器（如 Sentinel）提供干净的请求
     */
    @Override
    public int getOrder() {
        return 0;
    }
}