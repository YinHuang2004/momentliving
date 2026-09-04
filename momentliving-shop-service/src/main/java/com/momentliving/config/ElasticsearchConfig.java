package com.momentliving.config;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/**
 * Elasticsearch 客户端配置
 *
 * <p>RestHighLevelClient 是 ES 官方 7.x 标准 Java 客户端，
 * 8.x 起改用 elasticsearch-java 新客户端，企业存量系统仍以 7.x 居多，故本项目锁定 7.17.18。
 */
@Configuration
public class ElasticsearchConfig {

    @Resource
    private EsProperties esProperties;

    /**
     * 单例客户端：内部自带连接池（每个 HttpHost 维护异步 HTTP 连接），全局复用，容器关闭时释放
     */
    @Bean(destroyMethod = "close")
    public RestHighLevelClient restHighLevelClient() {
        HttpHost[] hosts = Arrays.stream(esProperties.getUris().split(","))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .map(HttpHost::create)
                .toArray(HttpHost[]::new);

        RestClientBuilder builder = RestClient.builder(hosts)
                // 请求级超时：ES 挂掉时快速失败，让搜索降级逻辑尽快接管
                .setRequestConfigCallback(config -> config
                        .setConnectTimeout(esProperties.getConnectTimeout())
                        .setSocketTimeout(esProperties.getSocketTimeout()));
        return new RestHighLevelClient(builder);
    }
}
