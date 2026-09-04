package com.momentliving.service;

import com.momentliving.dto.ShopDTO;
import com.momentliving.dto.ShopQueryDTO;
import com.momentliving.result.Result;
import com.momentliving.vo.ShopVO;

import java.util.List;

public interface ShopService {

    /**
     * 根据 id 查询商户（带缓存）
     */
    ShopVO queryById(Long id);

    /**
     * 更新商户信息（保证双写一致性）
     */
   void update(ShopDTO shop);

    /**
     * 新增店铺（返回自增 ID；商家入驻审核链路经 Feign 调用时参与 Seata 全局事务）
     * @param shop
     */
    Long add(ShopDTO shop);

    /**
     * 新增店铺
     * @param id
     */
    void delete(Long id);

    /**
     * 根据商户类型分页查询商户列表（入参见 {@link ShopQueryDTO}）
     */
    Result<List<ShopVO>> queryShopByType(ShopQueryDTO query);

    /**
     * 按店铺名称关键词分页查询（模糊匹配，入参见 {@link ShopQueryDTO}）
     */
    Result<List<ShopVO>> queryShopByName(ShopQueryDTO query);

    /**
     * 收藏/取消收藏店铺（toggle：未收藏则收藏、已收藏则取消，返回 true=操作后已收藏）
     */
    Boolean favoriteShop(Long shopId);

    /**
     * 当前登录用户是否已收藏该店铺（未登录返回 false）
     */
    Boolean isFavoriteShop(Long shopId);

    /**
     * 我收藏的店铺列表（按收藏时间倒序）
     */
    List<ShopVO> myFavoriteShops();

    void saveShop2Redis(Long id,Long logicExpireSeconds);
}
