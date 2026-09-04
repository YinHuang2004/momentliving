package com.momentliving.constant;

/**
 * 券订单状态机（voucher_order.status）
 * 对应表注释：0未支付 1已支付 2已核销 3已退款 4已关闭（超时取消）
 *
 * <p>合法流转：
 * <pre>
 *   待支付(0) --支付成功--> 已支付(1) --商家核销--> 已核销(2)
 *      |                        |
 *      +--超时未付--> 已关闭(4)   +--申请退款--> 已退款(3)（仅限未核销）
 * </pre>
 *
 * <p>所有流转必须走 Mapper 的条件 UPDATE（where status = 期望值），
 * 用数据库行级条件保证并发下的原子性与重复回调的幂等。
 */
public class OrderStatus {

    /** 待支付：秒杀下单成功后的初始状态 */
    public static final int PENDING_PAY = 0;
    /** 已支付/待使用：第三方支付回调确认到账 */
    public static final int PAID = 1;
    /** 已核销：商家端出示核销码核销成功，券已使用 */
    public static final int USED = 2;
    /** 已退款：用户对未核销订单发起退款并到账 */
    public static final int REFUNDED = 3;
    /** 已关闭：超时未支付自动取消（库存已回补） */
    public static final int CLOSED = 4;
}
