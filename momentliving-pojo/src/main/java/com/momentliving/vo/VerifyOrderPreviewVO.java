package com.momentliving.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 核销前核对订单预览 VO（按核销码查询，商家"先核对再确认"用）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyOrderPreviewVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单 ID */
    private Long orderId;

    /** 券 ID */
    private Long voucherId;

    /** 券名称 */
    private String voucherTitle;

    /** 购买用户 ID（买家昵称由 admin-service 编排 user-service 回填） */
    private Long userId;

    /** 买家昵称（admin-service 代理时回填，失败为 null） */
    private String nickName;

    /** 实付金额（分） */
    private Long payValue;

    /** 订单状态：0待支付 1已支付 2已核销 3已退款 4已关闭 */
    private Integer status;

    /** 核销记录状态：0未核销 1已核销 2已作废 */
    private Integer verifyStatus;

    /** 下单时间 */
    private LocalDateTime createTime;
}
