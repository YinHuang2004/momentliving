package com.momentliving.constant;

public class RedisConstants {
    /** 验证码发送间隔锁 key（60 秒内只能发一次） */
    public static final String LOGIN_CODE_INTERVAL_KEY = "login:code:interval:";
    /** 发送间隔：60 秒 */
    public static final Long LOGIN_CODE_INTERVAL_TTL = 60L;

    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final Long CACHE_SHOP_TTL = 30L;
    public static final String CACHE_SHOP_KEY = "cache:shop:";
    public static final String SECKILL_COUNT_KEY = "seckill:count:";

    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;
    public static final String LOCK_ORDER_KEY="lock:order:";

    // ========== 商户类型缓存 ==========
    public static final String CACHE_SHOP_TYPE_KEY = "cache:shop:type";

    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final Integer SECKILL_LIMIT=3;
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    /** 我点赞的博客反向索引（ZSet member=blogId score=点赞时间戳）：blog:my:liked:{userId}，"我的喜欢"列表用 */
    public static final String BLOG_MY_LIKED_KEY = "blog:my:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";
    public static final String LOGIN_USER_KEY="login:refresh:";
    // 管理端登录态 Key（/admin/** 用，与用户端隔离）
    public static final String LOGIN_ADMIN_KEY = "login:admin:";
    // 商家端登录态 Key（/merchant/** 用，商家账号已从 admin 表拆到独立 merchant 表）
    public static final String LOGIN_MERCHANT_KEY = "login:merchant:";
    // 店铺ID布隆过滤器Key（双布隆方案：0/1 两个桶交替重建 + active 指针原子切换）
    public static final String BLOOM_FILTER_SHOP_KEY_PREFIX = "bloom:shop:id:";
    /** 当前对外服务的布隆下标（值 "0" 或 "1"，切换时单次 set，原子） */
    public static final String BLOOM_FILTER_SHOP_ACTIVE_KEY = "bloom:shop:id:active";
    // 关注用户Key
    public static final String FOLLOW_USER_KEY = "follow:";

    // ========== 支付/核销（交易闭环） ==========
    /** 核销码 → 订单ID 映射，商家扫码先查 Redis 再查 DB */
    public static final String VERIFY_CODE_KEY = "verify:code:";
    /** 核销码分布式锁（防并发重复核销同一张券） */
    public static final String LOCK_VERIFY_CODE_KEY = "lock:verify:code:";
    /** 店铺评分聚合缓存（Hash：avg 评分均值 / cnt 评价总数） */
    public static final String SHOP_SCORE_KEY = "cache:shop:score:";
    /** 店铺评分缓存 TTL：30 分钟 */
    public static final Long SHOP_SCORE_TTL = 30L;
    /** 核销码 Redis 映射 TTL：90 天（券基本都会在此期间使用或过期） */
    public static final Long VERIFY_CODE_TTL = 90L * 24 * 60 * 60;

    // ========== 个人主页足迹（footprint 数据仅 user-service 读写，voucher-service 经 UserClient Feign 查询） ==========
    /** 足迹可见性开关（"1"可见/"0"隐藏，缺省可见）：footprint:visible:{userId} */
    public static final String FOOTPRINT_VISIBLE_KEY = "footprint:visible:";
    /** 足迹清空时间戳（毫秒，早于该时间的购物记录对他人隐藏，缺省 0=未清空）：footprint:cleared:{userId} */
    public static final String FOOTPRINT_CLEARED_KEY = "footprint:cleared:";

    // ========== 每日积分（user-service） ==========
    /** 每日积分领取标记 key：credits:claim:{userId}:{yyyyMMdd}，存在即当天已领取 */
    public static final String CREDITS_CLAIM_KEY = "credits:claim:";
    /** 每日可领取积分数 */
    public static final Long DAILY_CREDITS = 10L;
}
