package com.momentliving.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * C 端优惠券工具（上游 voucher-service）。
 */
@Component
public class VoucherTools {

    private final ToolSupport support;

    public VoucherTools(ToolSupport support) {
        this.support = support;
    }

    private static final Map<String, Integer> STATUS_MAP = Map.of(
            "unused", 1, "used", 2, "refunded", 3, "closed", 4);
    private static final Map<Integer, String> STATUS_TEXT = Map.of(
            0, "待支付", 1, "已支付未核销", 2, "已核销", 3, "已退款", 4, "已关闭");

    @Tool(name = "get_user_vouchers",
            description = "查询当前登录用户的优惠券订单列表（待支付/未使用/已使用/已退款），回答\"我有哪些券/我的订单\"类问题必用")
    public List<Map<String, Object>> getUserVouchers(
            @ToolParam(required = false, description = "订单状态过滤：unused=未使用，used=已核销，refunded=已退款，closed=已关闭，all=全部（默认）") String status,
            ToolContext toolContext) {
        Map<String, String> headers = ToolSupport.identityHeaders(toolContext);
        if (headers.isEmpty()) {
            return ToolSupport.errorList("未获取到用户身份，请重新登录后再试");
        }
        try {
            Map<String, Object> query = new LinkedHashMap<>();
            query.put("current", 1);
            query.put("pageSize", 20);
            Integer statusInt = STATUS_MAP.get(status);
            query.put("status", statusInt);   // null = 全部
            JsonNode items = support.extractList(support.get("voucher", "/voucher-order/my", query, headers));
            List<Map<String, Object>> out = new ArrayList<>();
            for (JsonNode item : items) {
                Map<String, Object> order = new LinkedHashMap<>();
                int st = item.path("status").asInt(0);
                order.put("orderId", ToolSupport.num(item, "id"));
                order.put("voucherId", ToolSupport.num(item, "voucherId"));
                order.put("status", st);
                order.put("statusText", STATUS_TEXT.getOrDefault(st, "未知"));
                order.put("createTime", ToolSupport.str(item, "createTime", null));
                order.put("payTime", ToolSupport.str(item, "payTime", null));
                order.put("useTime", ToolSupport.str(item, "useTime", null));
                out.add(order);
            }
            return out;
        } catch (Exception e) {
            return ToolSupport.errorList("订单查询失败：" + e.getMessage());
        }
    }

    @Tool(name = "get_shop_vouchers",
            description = "根据商铺ID查询该店在售的优惠券列表（标题、售价、抵扣价、类型），回答\"这家店有什么优惠\"类问题必用")
    public List<Map<String, Object>> getShopVouchers(
            @ToolParam(description = "商铺ID") long shopId) {
        try {
            JsonNode items = support.extractList(support.get("voucher", "/voucher/list/" + shopId, null, null));
            List<Map<String, Object>> out = new ArrayList<>();
            for (JsonNode item : items) {
                Map<String, Object> voucher = new LinkedHashMap<>();
                voucher.put("voucherId", ToolSupport.num(item, "id"));
                voucher.put("title", ToolSupport.str(item, "title", ""));
                voucher.put("subTitle", ToolSupport.str(item, "subTitle", ""));
                voucher.put("payPrice", cents(item, "payValue"));
                voucher.put("deductPrice", cents(item, "discountValue"));
                int type = item.path("type").asInt(1);
                voucher.put("type", type == 2 ? "秒杀券" : "普通券");
                out.add(voucher);
            }
            return out;
        } catch (Exception e) {
            return ToolSupport.errorList("优惠券查询失败：" + e.getMessage());
        }
    }

    /** 分 → 元字符串（与 Python 版 cents/100 对齐，空值为 "0"）。 */
    private static String cents(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return "0";
        }
        return String.format("%.2f", v.asDouble() / 100);
    }
}
