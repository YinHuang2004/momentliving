package com.momentliving.task;

import com.momentliving.service.ShopSearchService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * ES 索引初始化任务（服务启动后异步执行）
 *
 * <p>策略：索引不存在 → 创建 + 全量导入；已存在 → 跳过（增量靠 add/update/delete 钩子维护）。
 * 出现数据漂移（ES 宕机期间发生过写库）时手动 POST /shop/es/reindex 重建。
 *
 * <p>失败约定：ES 不可用时只记 error，不抛异常、不阻塞 shop-service 启动 ——
 * ES 是搜索的"加速器"，搜索侧有 MySQL like 降级，绝不能让它影响主流程。
 */
@Slf4j
@Component
public class ShopEsInitTask implements ApplicationRunner {

    @Resource
    private ShopSearchService shopSearchService;

    @Override
    public void run(ApplicationArguments args) {
        // 异步执行：ES 冷启动较慢（几十秒），不能让 shop-service 的上线等待它
        Thread worker = new Thread(() -> {
            try {
                boolean created = shopSearchService.ensureIndex();
                if (created) {
                    shopSearchService.importAll();
                } else {
                    log.info("ES 索引 [shop] 已存在，跳过全量导入（需要重建请调 POST /shop/es/reindex）");
                }
            } catch (Exception e) {
                log.error("ES 初始化失败，店铺搜索将降级为 MySQL like：{}", e.getMessage());
            }
        }, "shop-es-init");
        worker.setDaemon(true);
        worker.start();
    }
}
