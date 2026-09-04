package com.momentliving.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.momentliving.constant.SystemConstants;
import com.momentliving.entity.Shop;
import com.momentliving.mapper.ShopMapper;
import com.momentliving.service.ShopSearchService;
import com.momentliving.vo.ShopVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 店铺搜索服务实现（RestHighLevelClient = 在 Java 里组装 ES 的 DSL JSON）
 */
@Slf4j
@Service
public class ShopSearchServiceImpl implements ShopSearchService {

    /** 分词器约定：索引用 ik_max_word（最细切分，词条越多召回越好），搜索用 ik_smart（粗切分，避免过度拆词误召回） */
    private static final String INDEX_MAPPING = """
            {
              "settings": {
                "number_of_shards": 1,
                "number_of_replicas": 0
              },
              "mappings": {
                "properties": {
                  "id":      { "type": "keyword" },
                  "name":    {
                    "type": "text",
                    "analyzer": "ik_max_word",
                    "search_analyzer": "ik_smart",
                    "fields": { "keyword": { "type": "keyword", "ignore_above": 256 } }
                  },
                  "address": { "type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart" },
                  "typeId":  { "type": "keyword" }
                }
              }
            }""";

    @Resource
    private RestHighLevelClient restHighLevelClient;
    @Resource
    private ShopMapper shopMapper;

    // ==================== 索引与数据管理 ====================

    @Override
    public boolean ensureIndex() throws Exception {
        // 高层客户端专用的索引请求类在 org.elasticsearch.client.indices 下
        // （org.elasticsearch.action.admin.indices.* 是传输层内部类，构造器不对 REST 客户端开放）
        var existsReq = new org.elasticsearch.client.indices.GetIndexRequest(INDEX_SHOP);
        if (restHighLevelClient.indices().exists(existsReq, RequestOptions.DEFAULT)) {
            return false;
        }
        var createReq = new org.elasticsearch.client.indices.CreateIndexRequest(INDEX_SHOP);
        createReq.source(INDEX_MAPPING, XContentType.JSON);
        restHighLevelClient.indices().create(createReq, RequestOptions.DEFAULT);
        log.info("ES 索引 [{}] 已创建", INDEX_SHOP);
        return true;
    }

    @Override
    public int importAll() throws Exception {
        ensureIndex();
        int page = 1;
        int total = 0;
        while (true) {
            Page<Shop> result = shopMapper.selectPage(new Page<>(page, 500), null);
            List<Shop> shops = result.getRecords();
            if (shops.isEmpty()) {
                break;
            }
            total += bulkUpsert(shops);
            if (shops.size() < 500) {
                break;
            }
            page++;
        }
        log.info("ES 全量导入完成，共 {} 篇文档", total);
        return total;
    }

    @Override
    public int reindex() throws Exception {
        int imported = importAll();
        purgeStale();
        return imported;
    }

    /** 清理 ES 中已不在 DB 的脏文档（DB 删除时 ES 不可用导致漏同步的场景，由全量重建修复） */
    private void purgeStale() throws Exception {
        // 1. 拉取 ES 里全部文档 id（店铺量级远小于 10000 上限）
        SearchRequest req = new SearchRequest(INDEX_SHOP);
        SearchSourceBuilder ss = new SearchSourceBuilder()
                .query(QueryBuilders.matchAllQuery())
                .fetchSource("id", null)
                .size(10000);
        req.source(ss);
        SearchResponse resp = restHighLevelClient.search(req, RequestOptions.DEFAULT);
        Set<Long> esIds = new HashSet<>();
        for (SearchHit hit : resp.getHits().getHits()) {
            Object id = hit.getSourceAsMap().get("id");
            if (id != null) {
                esIds.add(Long.valueOf(id.toString()));
            }
        }
        // 2. DB 全量 id，差集 = 脏文档
        Set<Long> dbIds = shopMapper.selectList(null).stream().map(Shop::getId).collect(Collectors.toSet());
        List<Long> stale = esIds.stream().filter(id -> !dbIds.contains(id)).toList();
        if (stale.isEmpty()) {
            return;
        }
        BulkRequest bulk = new BulkRequest();
        for (Long id : stale) {
            bulk.add(new DeleteRequest(INDEX_SHOP, String.valueOf(id)));
        }
        restHighLevelClient.bulk(bulk, RequestOptions.DEFAULT);
        log.info("ES 清理脏文档 {} 条：{}", stale.size(), stale);
    }

    @Override
    public void syncUpsert(Shop shop) {
        if (shop == null || shop.getId() == null) {
            return;
        }
        try {
            bulkUpsert(List.of(shop));
        } catch (Exception e) {
            // 写同步是"尽力而为"：失败不影响主流程，兜底靠启动建索引 + 手动 reindex（生产建议 MQ/binlog 同步，见 23 号文档）
            log.warn("ES 文档同步失败 shopId={}：{}", shop.getId(), e.getMessage());
        }
    }

    @Override
    public void syncDelete(Long id) {
        if (id == null) {
            return;
        }
        try {
            restHighLevelClient.delete(new DeleteRequest(INDEX_SHOP, String.valueOf(id)), RequestOptions.DEFAULT);
        } catch (Exception e) {
            log.warn("ES 文档删除失败 shopId={}：{}", id, e.getMessage());
        }
    }

    /** bulk 批量写入（单条也复用，统一走 Bulk API），返回成功条数 */
    private int bulkUpsert(List<Shop> shops) throws Exception {
        BulkRequest bulk = new BulkRequest();
        for (Shop shop : shops) {
            bulk.add(new IndexRequest(INDEX_SHOP)
                    .id(String.valueOf(shop.getId()))       // 文档 id = 店铺 id：天然幂等（重复导入是覆盖不是重复）
                    .source(JSONUtil.toJsonStr(docOf(shop)), XContentType.JSON));
        }
        BulkResponse resp = restHighLevelClient.bulk(bulk, RequestOptions.DEFAULT);
        int failed = 0;
        for (var item : resp.getItems()) {
            if (item.isFailed()) {
                failed++;
                log.warn("ES bulk 单条失败 id={}：{}", item.getId(), item.getFailureMessage());
            }
        }
        return shops.size() - failed;
    }

    /** 只存检索字段：ES 瘦身，完整数据回 MySQL 拿（防止双份全量数据的漂移） */
    private Map<String, Object> docOf(Shop shop) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", shop.getId());
        doc.put("name", shop.getName());
        doc.put("address", shop.getAddress());
        doc.put("typeId", shop.getTypeId());
        return doc;
    }

    // ==================== 搜索 ====================

    @Override
    public List<ShopVO> search(String keyword, Integer typeId, int current) throws Exception {
        SearchRequest request = new SearchRequest(INDEX_SHOP);
        SearchSourceBuilder source = new SearchSourceBuilder();

        // bool 组合：must 参与算分（相关性排序），filter 不算分但可被缓存（精确过滤走 filter）
        source.query(QueryBuilders.boolQuery()
                // multi_match：name 权重 ^3（店名命中比地址命中更相关）；搜索词默认用索引的 search_analyzer(ik_smart) 分词
                .must(QueryBuilders.multiMatchQuery(keyword, "name^3", "address"))
                .filter(typeId != null ? QueryBuilders.termQuery("typeId", typeId) : QueryBuilders.matchAllQuery()))
                // from+size 浅分页（深分页问题见 23 号文档，本项目店铺量级无压力）
                .from((current - 1) * SystemConstants.MAX_PAGE_SIZE)
                .size(SystemConstants.MAX_PAGE_SIZE);
        request.source(source);

        SearchResponse response = restHighLevelClient.search(request, RequestOptions.DEFAULT);
        // 按相关性顺序取出 id（ES 只存了检索字段，完整数据回表）
        List<Long> ids = new ArrayList<>();
        for (SearchHit hit : response.getHits().getHits()) {
            Object id = hit.getSourceAsMap().get("id");
            if (id != null) {
                ids.add(Long.valueOf(id.toString()));
            }
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        // 回表：selectBatchIds 一次取齐，再按 ES 的相关性顺序组装
        Map<Long, Shop> shopMap = shopMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Shop::getId, s -> s));
        return ids.stream()
                .map(shopMap::get)
                .filter(Objects::nonNull)
                .map(shop -> BeanUtil.copyProperties(shop, ShopVO.class))
                .collect(Collectors.toList());
    }
}
