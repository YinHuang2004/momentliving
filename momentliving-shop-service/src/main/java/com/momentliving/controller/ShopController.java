package com.momentliving.controller;

import com.momentliving.dto.ShopDTO;
import com.momentliving.dto.ShopQueryDTO;
import com.momentliving.result.Result;
import com.momentliving.service.ShopSearchService;
import com.momentliving.service.ShopService;
import com.momentliving.vo.ShopVO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/shop")
public class ShopController {

    @Resource
    private ShopService shopService;

    @Resource
    private ShopSearchService shopSearchService;

    /**
     * 根据 id 查询商户详情
     */
    @GetMapping("/{id}")
    public Result<ShopVO> queryById(@PathVariable Long id) {
        ShopVO shop = shopService.queryById(id);
        return Result.success(shop);
    }

    /**
     * 更新商户信息
     */
    @PutMapping
    public Result<Void> update(@RequestBody ShopDTO shop) {
        shopService.update(shop);
        return Result.success();
    }

    /**
     * ★ 新增店铺（内部 Feign 接口）：店铺上线唯一入口。
     *   业务规则：管理员不能直接新增店铺——店铺只能由商家提交开店申请
     *   （merchant-service POST /merchant/shop/apply），平台管理员审核通过后
     *   由 admin-service 发起 @GlobalTransactional 经本接口建店（审核通过 = 上线）。
     *   公开 POST /shop 已下线，防止绕过申请直接建店。
     *   admin-service 发起全局事务，XID 经 Feign 请求头透传到本服务，
     *   add() 里的写操作注册为分支事务，全局回滚时按 undo_log 逆补偿。
     */
    @PostMapping("/feign/create")
    public Result<Long> feignCreate(@RequestBody ShopDTO shop) {
        return Result.success(shopService.add(shop));
    }

    @DeleteMapping("{id}")
    public Result<Void> deleteById(@PathVariable Long id){
        shopService.delete(id);
        return Result.success();
    }

    /**
     * 根据商铺类型分页查询商铺信息（GET query 绑定 {@link ShopQueryDTO}，URL 参数不变）
     */
    @GetMapping("/of/type")
    public Result<List<ShopVO>> queryShopByType(@ModelAttribute ShopQueryDTO query) {
        return shopService.queryShopByType(query);
    }

    /**
     * 按店铺名称关键词分页搜索（GET query 绑定 {@link ShopQueryDTO}，name 必填）
     *
     * <p>实现：优先 Elasticsearch（IK 分词 + 相关性排序），ES 不可用自动降级 MySQL like
     */
    @GetMapping("/of/name")
    public Result<List<ShopVO>> queryShopByName(@ModelAttribute ShopQueryDTO query) {
        return shopService.queryShopByName(query);
    }

    /**
     * 收藏/取消收藏店铺（toggle：返回 true=操作后已收藏）。
     * 鉴权：/shop/** 查询类接口公开，仅 /shop/favorite/** 需要用户登录——
     * 网关白名单对 /shop/favorite 豁免（强制解析用户态并透传 X-User-Id），
     * 本地 MvcConfig 对 /shop/favorite/** 单独启用 LoginInterceptor（防绕过网关直连）
     */
    @PutMapping("/favorite/{id}")
    public Result<Boolean> favorite(@PathVariable Long id) {
        return Result.success(shopService.favoriteShop(id));
    }

    /**
     * 当前用户是否已收藏该店铺（店铺详情页收藏按钮状态）
     */
    @GetMapping("/favorite/is-favorite/{id}")
    public Result<Boolean> isFavorite(@PathVariable Long id) {
        return Result.success(shopService.isFavoriteShop(id));
    }

    /**
     * 我收藏的店铺列表（收藏时间倒序）
     */
    @GetMapping("/favorite/list")
    public Result<List<ShopVO>> myFavoriteShops() {
        return Result.success(shopService.myFavoriteShops());
    }

    /**
     * 手动重建 ES 索引数据（全量导入 + 清理脏文档）。
     * 运维入口：ES 宕机期间发生过写库导致数据漂移时调用，正常无需使用（启动时索引不存在会自动建+导）
     */
    @PostMapping("/es/reindex")
    public Result<Integer> reindex() throws Exception {
        return Result.success(shopSearchService.reindex());
    }

}
