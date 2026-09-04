package com.momentliving.dto;

import lombok.Data;

/**
 * 开店申请审核入参（仅平台管理员，POST /admin/shop/apply/audit）
 */
@Data
public class ShopApplyAuditDTO {

    /** 申请ID */
    private Long id;

    /** true=通过（Seata 全局事务内建店并上线） false=拒绝（须填 reason） */
    private Boolean approved;

    /** 拒绝原因（拒绝时必填） */
    private String reason;

    /**
     * 回滚演练开关（仅联调/教学用，生产勿传）：
     * true = 在店铺创建成功、申请状态落库之后主动抛异常，验证 Seata 全局回滚
     */
    private Boolean drillFail;
}
