package com.momentliving.tools;

import com.momentliving.api.client.VoucherClient;
import com.momentliving.context.UserHolder;
import com.momentliving.entity.VoucherOrder;
import com.momentliving.result.Result;
import com.momentliving.vo.UserVO;
import com.momentliving.vo.VoucherVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 优惠券查询工具（Function Calling）。
 *
 * <p>⚠️ 身份安全：userId 一律从 ToolContext（请求开始时显式写入）或 UserHolder 还原，
 * 禁止由 AI 从对话内容决定查谁的数据（防越权）。
 * SSE 流式场景下工具在 reactor 线程执行、ThreadLocal 会丢失，因此 ToolContext 是主通道。
 */
@Slf4j
@Component
public class VoucherTools {

    @Resource
    private VoucherClient voucherClient;

    /** 订单状态文本（与 voucher_order.status 对齐） */
    private static final Map<Integer, String> STATUS_TEXT = Map.of(
            0, "待支付", 1, "已支付未核销", 2, "已核销", 3, "已退款", 4, "已关闭");

    @Tool(description = "查询当前用户的优惠券订单列表（含待支付/未使用/已使用/已过期），回答\"我有哪些券\"类问题必用")
    public List<Map<String, Object>> getUserVouchers(
            @ToolParam(description = "状态筛选：unused未使用 / used已使用 / refunded已退款 / closed已关闭过期 / all全部") String status,
            ToolContext toolContext) {
        long start = System.currentTimeMillis();
        Long userId = resolveUserId(toolContext);
        if (userId == null) {
            log.warn("AI工具调用 getUserVouchers 缺少用户身份，拒绝查询");
            return List.of(Map.of("error", "未获取到用户身份，请重新登录后再试"));
        }
        Result<List<VoucherOrder>> result = queryWithIdentity(userId, 1, 20, mapStatus(status));
        List<Map<String, Object>> data = (result.getData() == null ? List.<VoucherOrder>of() : result.getData())
                .stream()
                .map(o -> Map.<String, Object>of(
                        "orderId", o.getId(),
                        "voucherId", o.getVoucherId(),
                        "status", o.getStatus(),
                        "statusText", STATUS_TEXT.getOrDefault(o.getStatus(), "未知"),
                        "createTime", String.valueOf(o.getCreateTime()),
                        "payTime", String.valueOf(o.getPayTime()),
                        "useTime", String.valueOf(o.getUseTime())))
                .toList();
        logTool(userId, "getUserVouchers", Map.of("status", status), data.size(), start);
        return data;
    }

    @Tool(description = "根据商铺ID查询该店在售的优惠券列表（标题、售价、抵扣价、类型）")
    public List<Map<String, Object>> getShopVouchers(@ToolParam(description = "商铺ID") Long shopId) {
        long start = System.currentTimeMillis();
        Result<List<VoucherVO>> result = voucherClient.getShopVouchers(shopId);
        List<Map<String, Object>> data = (result.getData() == null ? List.<VoucherVO>of() : result.getData())
                .stream()
                .map(v -> Map.<String, Object>of(
                        "voucherId", v.getId(),
                        "title", v.getTitle() == null ? "" : v.getTitle(),
                        "subTitle", v.getSubTitle() == null ? "" : v.getSubTitle(),
                        "payPrice", yuan(v.getPayValue()),
                        "deductPrice", yuan(v.getActualValue()),
                        "type", v.getType() != null && v.getType() == 2 ? "秒杀券" : "普通券"))
                .toList();
        logTool(null, "getShopVouchers", Map.of("shopId", shopId), data.size(), start);
        return data;
    }

    /**
     * 带"临时身份"的 Feign 调用：流式场景下工具在 reactor 线程执行，UserHolder 为空，
     * FeignIdentityInterceptor 无法透传 X-User-Id —— 这里在调用前临时塞入、调用后清理。
     * （同步场景 UserHolder 已有真实用户，不会覆盖）
     */
    private Result<List<VoucherOrder>> queryWithIdentity(Long userId, Integer current,
                                                         Integer pageSize, Integer status) {
        boolean tempSaved = false;
        if (UserHolder.getUser() == null) {
            UserHolder.saveUser(UserVO.builder().id(userId).build());
            tempSaved = true;
        }
        try {
            return voucherClient.queryMyOrders(current, pageSize, status);
        } finally {
            if (tempSaved) {
                UserHolder.removeUser();
            }
        }
    }

    /** 身份还原：优先 ToolContext（流式场景），回退 UserHolder（同步场景） */
    private Long resolveUserId(ToolContext toolContext) {
        if (toolContext != null && toolContext.getContext().get("aiUserId") != null) {
            return Long.valueOf(toolContext.getContext().get("aiUserId").toString());
        }
        return UserHolder.getUser() != null ? UserHolder.getUser().getId() : null;
    }

    private Integer mapStatus(String status) {
        return switch (status == null ? "all" : status) {
            case "unused" -> 1;    // 已支付未核销 = 未使用
            case "used" -> 2;      // 已核销
            case "refunded" -> 3;
            case "closed" -> 4;    // 已关闭（含超时未支付关闭）
            default -> null;       // all
        };
    }

    /** 分 → 元显示 */
    private String yuan(Long cents) {
        return cents == null ? "0" : String.format("%.2f", cents / 100.0);
    }

    private void logTool(Long userId, String name, Object params, int resultSize, long start) {
        log.info("AI工具调用 userId={}, name={}, params={}, resultSize={}, costMs={}",
                userId, name, params, resultSize, System.currentTimeMillis() - start);
    }
}
