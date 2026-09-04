package com.momentliving.service;

import com.momentliving.dto.VoucherDTO;
import com.momentliving.entity.Voucher;
import com.momentliving.result.Result;
import com.momentliving.vo.VoucherVO;

import java.util.List;

public interface VoucherService {

    Long save(VoucherDTO voucher);

    Voucher getById(Long id);

    Result<List<VoucherVO>> queryVoucherOfShop(Long shopId);

    /**
     * 新增秒杀券（stock/beginTime/endTime 在 VoucherDTO 中），返回新券 ID
     */
    Long addSeckillVoucher(VoucherDTO voucher);

    /**
     * 查询优惠券详情，秒杀券附带库存与活动时间
     */
    VoucherVO queryByIdWithStock(Long id);

    /**
     * 全量秒杀券列表（type=1，附带库存与活动时间，新建时间倒序），供秒杀 Tab 展示
     */
    Result<List<VoucherVO>> listSeckillVouchers();

    /**
     * 更新优惠券，秒杀券同步更新库存与 Redis
     */
    void update(VoucherDTO voucher);

    /**
     * 删除优惠券，秒杀券同步删除秒杀信息与 Redis 库存
     */
    void deleteById(Long id);

    /**
     * 🆕 管理端券分页（全量）：回填秒杀信息 + 多店券适用店铺ID列表
     */
    Result<List<VoucherVO>> pageForAdmin(Integer current, Integer pageSize);

    /**
     * 🆕 平台券列表（用户端"优惠券中心"）：type=0 普通券 / 1 秒杀券，
     * 附带秒杀信息 + 适用范围 shopIds（空=全场通用）
     */
    Result<List<VoucherVO>> listByType(Integer type);
}
