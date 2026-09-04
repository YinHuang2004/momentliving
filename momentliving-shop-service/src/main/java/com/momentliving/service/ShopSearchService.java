package com.momentliving.service;

import com.momentliving.entity.Shop;
import com.momentliving.vo.ShopVO;

import java.util.List;

/**
 * 店铺搜索服务（Elasticsearch 侧）
 *
 * <p>通道分工：ES 只存检索字段（id/name/address/typeId），搜到 id 后回 MySQL 取完整数据（回表），
 * 避免两份数据全量双写导致的漂移面扩大。
 *
 * <p>异常约定：
 * <ul>
 *   <li>{@link #search} 查询失败（ES 宕机/索引不存在等）直接抛异常 —— 由
 *       {@code ShopServiceImpl.queryShopByName} 捕获后降级回 MySQL like；</li>
 *   <li>{@link #syncUpsert}/{@link #syncDelete} 写同步失败只记 warn 不抛 —— 同步是"尽力而为"，
 *       兜底手段是启动自动建索引 + 手动 POST /shop/es/reindex 全量重建。</li>
 * </ul>
 */
public interface ShopSearchService {

    /** 索引名 */
    String INDEX_SHOP = "shop";

    /**
     * 确保 shop 索引存在（不存在则按 mapping 创建）
     *
     * @return true=本次新建了索引（调用方可据此决定是否需要全量导入），false=索引已存在
     */
    boolean ensureIndex() throws Exception;

    /**
     * 全量导入：MySQL shop 表 → ES bulk 批量写入（幂等，按文档 id upsert）
     *
     * @return 成功写入的文档数
     */
    int importAll() throws Exception;

    /**
     * 全量重建：importAll + 清理 ES 中已不在 DB 的脏文档（手动运维入口 POST /shop/es/reindex 用）
     */
    int reindex() throws Exception;

    /** 单条同步：店铺新增/修改时 upsert 一篇文档 */
    void syncUpsert(Shop shop);

    /** 单条同步：店铺删除时删除对应文档 */
    void syncDelete(Long id);

    /**
     * 关键词搜索：multi_match(name^3, address) + typeId 过滤 + 分页，按相关性排序
     *
     * @param keyword 关键词（必填）
     * @param typeId  店铺类型过滤（可空）
     * @param current 页码（从 1 开始）
     */
    List<ShopVO> search(String keyword, Integer typeId, int current) throws Exception;
}
