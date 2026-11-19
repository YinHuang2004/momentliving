package com.momentliving.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.momentliving.ai.config.AiProperties;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具调用支撑：上游 HTTP GET + 响应解析 + 身份头构造。
 *
 * <p>⚠️ 身份传播机制：网关解析 JWT 后写入 X-User-Id/X-Merchant-Id，控制器装入
 * Spring AI toolContext，@Tool 方法从 ToolContext 读取后转发给上游——
 * 工具只能查「当前请求人」的数据，AI 无法指定别的用户（防越权，与 Python 版
 * ContextVar 方案语义一致，但用显式传参实现，Reactor 线程切换安全）。
 */
@Component
public class ToolSupport {

    private final RestClient rest;
    private final AiProperties props;
    private final ObjectMapper mapper = new ObjectMapper();

    public ToolSupport(RestClient upstreamRestClient, AiProperties props) {
        this.rest = upstreamRestClient;
        this.props = props;
    }

    /** 从 toolContext 构造上游身份头（userId/merchantId 谁有放谁）。 */
    public static Map<String, String> identityHeaders(ToolContext ctx) {
        Map<String, String> headers = new HashMap<>();
        if (ctx == null) {
            return headers;
        }
        Object uid = ctx.getContext().get("userId");
        Object mid = ctx.getContext().get("merchantId");
        if (uid instanceof Long l && l > 0) {
            headers.put("X-User-Id", String.valueOf(l));
        }
        if (mid instanceof Long l && l > 0) {
            headers.put("X-Merchant-Id", String.valueOf(l));
        }
        return headers;
    }

    /** 从 toolContext 取商家 id（无则抛错，商家工具的身份守卫）。 */
    public static long merchantId(ToolContext ctx) {
        Object mid = ctx == null ? null : ctx.getContext().get("merchantId");
        if (mid instanceof Long l && l > 0) {
            return l;
        }
        throw new IllegalStateException("未获取到商家身份，请重新登录商家端");
    }

    /**
     * GET 上游接口并解析 JSON。
     *
     * @param service 上游服务名（映射到配置的 base url）
     * @param path    请求路径（可含 {id} 占位，用 pathVars 填充）
     */
    public JsonNode get(String service, String path, Map<String, ?> query, Map<String, String> headers) {
        String baseUrl = switch (service) {
            case "shop" -> props.upstream().shopServiceUrl();
            case "blog" -> props.upstream().blogServiceUrl();
            case "voucher" -> props.upstream().voucherServiceUrl();
            case "merchant" -> props.upstream().merchantServiceUrl();
            default -> throw new IllegalArgumentException("未知上游服务：" + service);
        };
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + path);
        if (query != null) {
            query.forEach((k, v) -> {
                if (v != null) {
                    builder.queryParam(k, v);
                }
            });
        }
        RestClient.RequestHeadersSpec<?> spec = rest.get().uri(URI(builder.toUriString()));
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                spec = spec.header(e.getKey(), e.getValue());
            }
        }
        ResponseEntity<String> resp = spec.retrieve().toEntity(String.class);
        try {
            return mapper.readTree(resp.getBody());
        } catch (Exception e) {
            throw new IllegalStateException("上游响应解析失败", e);
        }
    }

    private static java.net.URI URI(String s) {
        return java.net.URI.create(s);
    }

    /**
     * 从上游 Result 响应中提取业务数据列表：
     * 兼容 data 为数组、IPage（records）等形态。
     */
    public JsonNode extractList(JsonNode root) {
        JsonNode data = root == null ? null : root.get("data");
        if (data == null || data.isNull()) {
            return mapper.createArrayNode();
        }
        if (data.isArray()) {
            return data;
        }
        JsonNode records = data.get("records");
        if (records != null && records.isArray()) {
            return records;
        }
        JsonNode list = data.get("list");
        if (list != null && list.isArray()) {
            return list;
        }
        return mapper.createArrayNode();
    }

    /** 提取单个业务对象（data 字段）。 */
    public JsonNode extractData(JsonNode root) {
        return root == null ? mapper.createObjectNode() : (root.get("data") == null ? mapper.createObjectNode() : root.get("data"));
    }

    /** 截断文本（加省略号），与 Python 版一致。 */
    public static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    /** 空安全取字符串。 */
    public static String str(JsonNode node, String field, String def) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? def : v.asText();
    }

    /** 空安全取数值。 */
    public static Object num(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.numberValue();
    }

    /** 构造错误返回（工具内不抛异常，错误 JSON 交给模型兜底）。 */
    public static List<Map<String, Object>> errorList(String message) {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(Map.of("error", message));
        return list;
    }
}
