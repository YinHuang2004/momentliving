package com.momentliving.service;

import com.momentliving.entity.VoucherOrder;
import com.momentliving.vo.MerchantStatsVO;
import com.momentliving.vo.VerifyOrderPreviewVO;
import com.momentliving.vo.VerifyRecordsVO;

/**
 * 券核销服务：
 * - 支付成功后生成核销码并写 DB + Redis 预热
 * - 商家端出示码核销（分布式锁 + 订单/核销记录双 CAS）
 * - 商家端工作台统计（按店铺维度）
 */
public interface VerifyService {

    /**
     * 支付成功回调里调用：为订单生成确定性核销码。
     * 幂等：同一订单重复生成得到同一个码；DB 唯一索引兜底，重复插入静默跳过。
     *
     * @param order 已处于「已支付」状态的订单（含 userId/voucherId）
     */
    void createVerifyCode(VoucherOrder order);

    /**
     * 商家扫码核销。
     *
     * @param verifyCode 用户出示的核销码
     * @param merchantId 操作商家账号（merchant.id，账号体系已与 admin 拆分）
     * @throws com.momentliving.exception.BadRequestException 码无效/券状态不允许核销时抛出
     */
    /**
     * 商家核销（含券适用范围校验）。
     * @param shopId 核销商家绑定店铺：单店券须一致、多店券须在名单内、全场通用不限；
     *               全场/多店券核销成功后 voucher_verify.shop_id 回填为该店铺（核销归属）
     */
    void verifyByCode(String verifyCode, Long merchantId, Long shopId);

    /**
     * 按核销码查订单预览（商家"先核对再确认"第一步：只读，不改变任何状态）
     *
     * @param verifyCode 用户出示的核销码
     * @return 订单 + 券名 + 核销状态；码无效时抛 BadRequestException
     */
    VerifyOrderPreviewVO previewByCode(String verifyCode);

    /**
     * 商家端工作台统计：今日待核销/今日已核销/今日营收 + 最近核销列表。
     * 数据全部在 voucher-service 同库（voucher_order/voucher/voucher_verify），
     * 买家昵称由 admin-service 调 user-service 回填。
     *
     * @param shopId 核销店铺ID（商家账号绑定的店铺）
     */
    MerchantStatsVO stats(Long shopId);

    /**
     * 商家端核销记录分页（按店铺 + 状态筛选，核销时间倒序）。
     *
     * @param shopId   店铺ID
     * @param status   核销状态：0未核销 1已核销 2已作废，null 表示全部
     * @param current  页码（1 起）
     * @param pageSize 页大小
     */
    VerifyRecordsVO pageRecords(Long shopId, Integer status, Integer current, Integer pageSize);
}
