package com.momentliving.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 个人主页足迹条目（他人的购物记录，脱敏后展示）
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FootprintItemVO {
    private Long orderId;
    private Long voucherId;
    /** 券标题 */
    private String voucherTitle;
    /** 订单状态：1已支付 2已核销 */
    private Integer status;
    private LocalDateTime payTime;
    private LocalDateTime createTime;
}
