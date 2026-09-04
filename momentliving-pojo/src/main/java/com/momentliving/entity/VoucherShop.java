package com.momentliving.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 多店优惠券的适用店铺（对应表：voucher_shop）
 *
 * <p>范围语义（由 voucher.shop_id + 本表共同决定）：
 * voucher.shop_id > 0 = 单店券（不写本表）；shop_id = 0 且本表有记录 = 指定多店；
 * shop_id = 0 且本表无记录 = 全场通用。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoucherShop implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 券ID（voucher.id） */
    private Long voucherId;

    /** 适用店铺ID（shop.id） */
    private Long shopId;
}
