package com.momentliving.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 商家端"最近核销"单条记录
 * voucher-service 只返回同库字段（userId 由 admin-service 调 user-service 回填昵称）
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RecentVerifyVO {
    /** 券订单ID */
    private Long orderId;
    /** 券名（JOIN voucher.title） */
    private String voucherTitle;
    /** 买家用户ID（admin-service 回填 nickName） */
    private Long userId;
    /** 买家昵称（admin-service 回填，voucher-service 返回 null） */
    private String nickName;
    /** 核销时间 */
    private LocalDateTime verifyTime;
    /** 核销状态：1已核销（最近核销列表只含已核销） */
    private Integer status;
    /** 核销码后 4 位（扫码场景快速对账） */
    private String verifyCodeTail;
}
