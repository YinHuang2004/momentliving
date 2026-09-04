package com.momentliving.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Elasticsearch 连接配置（momentliving.elasticsearch.*，见 application.yml）
 */
@Data
@Component
@ConfigurationProperties(prefix = "momentliving.elasticsearch")
public class EsProperties {

    /** ES 地址，集群多节点用逗号分隔，如 http://192.168.19.131:9200,http://192.168.19.132:9200 */
    private String uris = "http://localhost:9200";

    /** 建立连接超时（毫秒） */
    private int connectTimeout = 3000;

    /** 读超时（毫秒） */
    private int socketTimeout = 5000;
}
