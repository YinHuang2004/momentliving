package com.momentliving.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.momentliving.constant.MessageConstant;
import com.momentliving.constant.RedisConstants;
import com.momentliving.constant.SystemConstants;
import com.momentliving.dto.ShopDTO;
import com.momentliving.dto.ShopQueryDTO;
import com.momentliving.context.UserHolder;
import com.momentliving.entity.Shop;
import com.momentliving.entity.ShopFavorite;
import com.momentliving.exception.BadRequestException;
import com.momentliving.mapper.ShopFavoriteMapper;
import com.momentliving.mapper.ShopMapper;
import com.momentliving.result.Result;
import com.momentliving.service.ShopBloomFilterService;
import com.momentliving.service.ShopSearchService;
import com.momentliving.service.ShopService;
import com.momentliving.utils.RedisData;
import com.momentliving.vo.ShopVO;
import com.momentliving.vo.UserVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.baomidou.mybatisplus.core.toolkit.Wrappers.query;

@Slf4j
@Service
public class ShopServiceImpl implements ShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ShopMapper shopMapper;

    @Resource
    private ShopFavoriteMapper shopFavoriteMapper;

    @Resource
    private ShopBloomFilterService shopBloomFilterService;

    @Resource
    private ShopSearchService shopSearchService;
    //线程池创建线程
    private static final ExecutorService executorService= Executors.newFixedThreadPool(10);

    @Override
    public ShopVO queryById(Long id) {
        //1.布隆过滤器拦截（双布隆：读 active 桶）
        //返回 false 说明 id 一定不存在，直接抛出异常，不走 redis 和 db
        if (!shopBloomFilterService.contains(id)) {
            throw new BadRequestException(MessageConstant.SHOP_NOT_EXIST);
        }
        //查询缓存是否存在该店铺信息
        String key=RedisConstants.CACHE_SHOP_KEY+id;
        String redisDataJson = stringRedisTemplate.opsForValue().get(key);
        // 2.缓存完全未命中（被物理删除/新增未预热/冷数据）：同步查DB重建
        if (StrUtil.isBlank(redisDataJson)) {
            return buildCacheAndReturn(id, key);
        }
        //缓存命中
        //需要判断缓存是否过期
        RedisData<Shop> redisData = JSONUtil.toBean(redisDataJson, new TypeReference<RedisData<Shop>>() {},false);
        Shop shop = redisData.getData();
        LocalDateTime expireTime = redisData.getExpireTime();
        if(expireTime.isAfter(LocalDateTime.now())){
            //缓存没有过期直接返回缓存
            return BeanUtil.copyProperties(shop, ShopVO.class);
        }

        //过期了就需要获取锁重建缓存
        String lockKey= RedisConstants.LOCK_SHOP_KEY+id;
        boolean isLock=false;
        try{
            isLock = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
            //如果获取锁失败直接返回旧数据
            if(isLock){
                //获取锁成功后先双检(为什么要双检，因为假设线程a刚释放锁时碰巧有锁尝试获取锁，此时获取成功后就不会递归回去了，此时双检能减少数据库压力
                // //双肩：检查缓存是否命中,如果命中直接返回，而不会多余查询数据库(认为自己还没命中）
                String doubleCheckJson=stringRedisTemplate.opsForValue().get(key);
                if(StrUtil.isNotBlank(doubleCheckJson)){
                    RedisData<Shop> doubleCheckData = JSONUtil.toBean(doubleCheckJson, new TypeReference<RedisData<Shop>>() {},false);
                    if(doubleCheckData.getExpireTime().isAfter(LocalDateTime.now())){
                        //双检发现缓存已经被其他线程重建且未过期：直接释放锁返回
                        return BeanUtil.copyProperties(doubleCheckData.getData(), ShopVO.class);
                    }
                }
                //开启独立线程去查询数据库
                executorService.submit(() ->{
                    try{
                        //重建缓存，实际中缓存过期时期应该是30minutes+0-5分钟
                        Long logicalExpireSeconds=RedisConstants.CACHE_SHOP_TTL*60+ThreadLocalRandom.current().nextLong(300);
                        saveShop2Redis(id,logicalExpireSeconds);
                    }catch (Exception e){
                        throw new RuntimeException(e);
                    }finally {
                        //释放互斥锁
                        stringRedisTemplate.delete(lockKey);
                    }
                });
            }
        }catch (Exception e){
            throw new RuntimeException(e);
        }
        //无论是获取锁成功与否都需要返回数据（只是失败返回旧数据，成功返回新数据）
        return BeanUtil.copyProperties(redisData.getData(), ShopVO.class);
    }
    /**
     * 缓存未命中时：同步查DB重建缓存，只需要一个线程去重建缓存（兜底物理删除/新增/冷数据场景）
     */
    private ShopVO buildCacheAndReturn(Long id, String key) {
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = false;
        try {
            isLock = stringRedisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "sync_build", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
            if (!isLock) {
                Thread.sleep(50);
                return queryById(id);
            }
            // 查DB
            Shop shop = shopMapper.selectById(id);
            if (Objects.isNull(shop)) {
                // DB不存在：缓存空值5分钟防穿透
                stringRedisTemplate.opsForValue().set(key, "", 5, TimeUnit.MINUTES);
                throw new BadRequestException(MessageConstant.SHOP_NOT_EXIST);
            }
            // 存在则写入缓存
            saveShop2Redis(id, RedisConstants.CACHE_SHOP_TTL * 60L);
            return BeanUtil.copyProperties(shop, ShopVO.class);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            if (isLock) {
                stringRedisTemplate.delete(lockKey);
            }
        }
    }

    /**
     * 写入带逻辑过期的缓存，同时设置物理ttl（必须大于逻辑过期时间）
     * @param id
     * @param logicExpireSeconds
     */
    public void saveShop2Redis(Long id,Long logicExpireSeconds){
        //1.查询店铺数据
        Shop shop = shopMapper.selectById(id);
        //封装逻辑过期时间
        RedisData<Shop> redisData=RedisData.<Shop>builder().data(shop).expireTime(LocalDateTime.now().plusSeconds(logicExpireSeconds)).build();
        // 物理TTL = 逻辑过期时间 + 10~30分钟随机，保证逻辑过期时key还在
        long physicalTtl = logicExpireSeconds + (600 + ThreadLocalRandom.current().nextInt(1200));
        //写入redis
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData),physicalTtl,TimeUnit.SECONDS);
    }

    @Override
    public void update(ShopDTO dto) {
        Shop shop = BeanUtil.copyProperties(dto, Shop.class);
        Long id = shop.getId();
        if(id==null){
            throw new BadRequestException(MessageConstant.SHOPID_IS_NULL);
        }
        shopMapper.updateById(shop);
        //删除店铺缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY+id);
        // 同步 ES 文档（best-effort，失败只记 warn）
        shopSearchService.syncUpsert(shopMapper.selectById(id));
    }

    /**
     * 新增店铺，返回自增 ID。
     * ★ 分布式事务：商家入驻审核经 Feign 调到这里时，XID 已由 Seata 的 Feign 集成透传，
     *   @Transactional 提交前数据源代理会向 TC 注册分支并记录 undo_log，失败时随全局事务回滚。
     * @param shop
     */
    @Override
    @Transactional
    public Long add(ShopDTO dto) {
        Shop shop = BeanUtil.copyProperties(dto, Shop.class);
        shopMapper.insert(shop);
        // 新增店铺：双写两个布隆桶（保证重建期间新增 ID 不丢）
        shopBloomFilterService.add(shop.getId());
        // 同步 ES 文档（best-effort，失败只记 warn；ES 非 XA 资源不参与回滚，脏文档由 reindex 兜底）
        shopSearchService.syncUpsert(shop);
        return shop.getId();
    }

    /**
     * 删除店铺
     * @param id
     */
    @Override
    public void delete(Long id) {
        shopMapper.deleteById(id);
        String key=RedisConstants.CACHE_SHOP_KEY+id;
        stringRedisTemplate.delete(key);
        // 同步删除 ES 文档（best-effort，失败只记 warn，可由 reindex 修复脏文档）
        shopSearchService.syncDelete(id);
    }

    /**
     * 收藏/取消收藏店铺：shop_favorite 表存在记录即已收藏，toggle 为删行/插行。
     * 鉴权说明：/shop/** 查询在网关与服务本地均公开，仅 /shop/favorite/**
     * 强制登录（网关白名单豁免 + 本地 LoginInterceptor 单独拦截），这里 UserHolder 必有值
     */
    @Override
    public Boolean favoriteShop(Long shopId) {
        Long userId = UserHolder.getUser().getId();
        if (shopMapper.selectById(shopId) == null) {
            throw new BadRequestException(MessageConstant.SHOP_NOT_EXIST);
        }
        LambdaQueryWrapper<ShopFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ShopFavorite::getUserId, userId).eq(ShopFavorite::getShopId, shopId);
        if (shopFavoriteMapper.selectCount(wrapper) > 0) {
            shopFavoriteMapper.delete(wrapper);
            return false;
        }
        shopFavoriteMapper.insert(ShopFavorite.builder()
                .userId(userId).shopId(shopId).createTime(LocalDateTime.now()).build());
        return true;
    }

    @Override
    public Boolean isFavoriteShop(Long shopId) {
        UserVO user = UserHolder.getUser();
        if (user == null) {
            return false;
        }
        return shopFavoriteMapper.selectCount(new LambdaQueryWrapper<ShopFavorite>()
                .eq(ShopFavorite::getUserId, user.getId())
                .eq(ShopFavorite::getShopId, shopId)) > 0;
    }

    /**
     * 我收藏的店铺：收藏表查自己的记录（收藏时间倒序），再按 shopId 回填店铺详情；
     * 已被删除的店铺自然从列表消失
     */
    @Override
    public List<ShopVO> myFavoriteShops() {
        Long userId = UserHolder.getUser().getId();
        List<ShopFavorite> favorites = shopFavoriteMapper.selectList(new LambdaQueryWrapper<ShopFavorite>()
                .eq(ShopFavorite::getUserId, userId)
                .orderByDesc(ShopFavorite::getCreateTime));
        if (favorites.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> shopIds = favorites.stream().map(ShopFavorite::getShopId).collect(Collectors.toList());
        Map<Long, Shop> shopMap = shopMapper.selectBatchIds(shopIds).stream()
                .collect(Collectors.toMap(Shop::getId, shop -> shop));
        return favorites.stream()
                .map(favorite -> shopMap.get(favorite.getShopId()))
                .filter(Objects::nonNull)
                .map(shop -> BeanUtil.copyProperties(shop, ShopVO.class))
                .collect(Collectors.toList());
    }

    @Override
    public Result<List<ShopVO>> queryShopByType(ShopQueryDTO query) {
        Integer typeId = query.getTypeId();
        Integer current = query.getCurrent() == null ? 1 : query.getCurrent();
        Double x = query.getX();
        Double y = query.getY();
        // 1.判断是否需要根据坐标查询（typeId 为空 = 首页"附近店铺"不筛分类，查全部）
        if (x == null || y == null || typeId == null) {
            // 不需要坐标查询，按数据库查询
            LambdaQueryWrapper<Shop> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(typeId != null, Shop::getTypeId, typeId);
            // 每页 10 条，与前端 hasMore 判断（length >= 10）保持一致
            Page<Shop> result= shopMapper.selectPage(new Page<>(current, SystemConstants.MAX_PAGE_SIZE), wrapper);
            // 返回数据（转 ShopVO）
            List<ShopVO> shopVOs = result.getRecords().stream()
                    .map(shop -> BeanUtil.copyProperties(shop, ShopVO.class))
                    .collect(Collectors.toList());
            return Result.success(shopVOs);
        }

        // 2.计算分页参数（与列表接口一致，每页 10 条）
        int from = (current - 1) * SystemConstants.MAX_PAGE_SIZE;
        int end = current * SystemConstants.MAX_PAGE_SIZE;

        // 3.查询redis、按照距离排序、分页。结果：shopId、distance
        String key = RedisConstants.SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo() // GEOSEARCH key BYLONLAT x y BYRADIUS 10 WITHDISTANCE
                .search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );
        // 4.解析出id
        if (results == null) {
            return Result.success(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size() <= from) {
            // 没有下一页了，结束
            return Result.success(Collections.emptyList());
        }
        // 4.1.截取 from ~ end的部分
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            // 4.2.获取店铺id
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            // 4.3.获取距离
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });
        // 5.根据id查询Shop（转 ShopVO 并填充距离）
        List<ShopVO> shopVOs = shopMapper.selectShopByIds(ids).stream()
                .map(shop -> BeanUtil.copyProperties(shop, ShopVO.class))
                .collect(Collectors.toList());
        for (ShopVO shopVO : shopVOs) {
            shopVO.setDistance(distanceMap.get(shopVO.getId().toString()).getValue());
        }
        // 6.返回
        return Result.success(shopVOs);
    }

    /**
     * 按店铺名称关键词分页查询（模糊匹配，可叠加类型过滤；搜索场景不走 GEO 距离排序）
     */
    @Override
    public Result<List<ShopVO>> queryShopByName(ShopQueryDTO query) {
        String name = StrUtil.trimToNull(query.getName());
        if (name == null) {
            throw new BadRequestException("搜索关键词不能为空");
        }
        int current = query.getCurrent() == null ? 1 : query.getCurrent();

        // 🆕 纯数字关键词：先按店铺 ID 精确匹配（管理端常按 ID 找店）
        if (name.matches("\\d+") && query.getTypeId() == null) {
            Shop byId = shopMapper.selectById(Long.valueOf(name));
            if (byId != null) {
                return Result.success(List.of(BeanUtil.copyProperties(byId, ShopVO.class)));
            }
        }

        // ★ 搜索优先走 ES：中文分词（MySQL like 只能整串包含，"老城火锅" 搜不到 "老城老火锅"）+ 相关性排序
        //   ES 宕机/索引不存在时抛异常 → 捕获降级回 MySQL like，保证搜索永远可用
        try {
            return Result.success(shopSearchService.search(name, query.getTypeId(), current));
        } catch (Exception e) {
            log.warn("ES 搜索不可用，降级 MySQL like，keyword={}：{}", name, e.getMessage());
        }

        LambdaQueryWrapper<Shop> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(Shop::getName, name)
                .eq(query.getTypeId() != null, Shop::getTypeId, query.getTypeId());
        Page<Shop> result = shopMapper.selectPage(new Page<>(current, SystemConstants.MAX_PAGE_SIZE), wrapper);
        List<ShopVO> shopVOs = result.getRecords().stream()
                .map(shop -> BeanUtil.copyProperties(shop, ShopVO.class))
                .collect(Collectors.toList());
        return Result.success(shopVOs);
    }
}
