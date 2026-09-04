package com.momentliving.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商家开店申请（对应表：shop_apply）
 *
 * <p>规则：管理员不能直接新增店铺。店铺一律由商家提交开店申请（status=0）→
 * 平台管理员审核：通过(1) 时 admin-service 经 Seata 全局事务调 shop-service 建店
 * （shop_id 回填，店铺即上线）；拒绝(2) 记录原因，商家可重新提交新申请。
 * 待审核/被拒绝的申请不产生任何 shop 记录，对外天然不可见。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopApply implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_APPROVED = 1;
    public static final int STATUS_REJECTED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 申请人（merchant 表 id） */
    private Long merchantId;

    /** 拟开店名称 */
    private String shopName;

    /** 店铺分类（shop_type.id） */
    private Long typeId;

    /** 店铺地址 */
    private String address;

    /** 联系电话 */
    private String contactPhone;

    /** 0待审核 1已通过 2已拒绝 */
    private Integer status;

    /** 拒绝原因 */
    private String rejectReason;

    /** 审核管理员ID（admin.id） */
    private Long auditBy;

    private LocalDateTime auditTime;

    /** 审核通过后生成的店铺ID */
    private Long shopId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
