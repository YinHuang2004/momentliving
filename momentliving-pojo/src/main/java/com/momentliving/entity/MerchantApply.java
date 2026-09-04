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
 * 商家入驻申请（对应表：merchant_apply）
 * 流程：商家提交申请(status=0) → 平台管理员审核 → 通过(1)：自动创建 shop + admin 商家账号(role=2)；
 *       拒绝(2)：记录原因，商家可修改后重新提交（同一 username 复用行）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantApply implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_APPROVED = 1;
    public static final int STATUS_REJECTED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 申请入驻的店铺名称 */
    private String shopName;

    /** 店铺地址 */
    private String address;

    /** 联系电话 */
    private String contactPhone;

    /** 拟用商家账号（唯一） */
    private String username;

    /** 密码（提交时已 BCrypt 加密；对外接口一律脱敏为 null） */
    private String password;

    /** 0待审核 1已通过 2已拒绝 */
    private Integer status;

    /** 拒绝原因 */
    private String rejectReason;

    /** 审核管理员ID */
    private Long auditBy;

    private LocalDateTime auditTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
