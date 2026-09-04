package com.momentliving.controller;

import com.momentliving.dto.VoucherDTO;
import com.momentliving.result.Result;
import com.momentliving.service.VoucherService;
import com.momentliving.vo.VoucherVO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/voucher")
public class VoucherController {

    @Resource
    private VoucherService voucherService;

    /**
     * 新增普通券
     */
    @PostMapping
    public Result<Long> addVoucher(@RequestBody VoucherDTO voucher) {
        return Result.success(voucherService.save(voucher));
    }

    /**
     * 新增秒杀券（VoucherDTO 携带 stock/beginTime/endTime）
     */
    @PostMapping("seckill")
    public Result<Long> addSeckillVoucher(@RequestBody VoucherDTO voucher) {
        return Result.success(voucherService.addSeckillVoucher(voucher));
    }

    /**
     * 查询店铺的优惠券列表
     */
    @GetMapping("/list/{shopId}")
    public Result<List<VoucherVO>> queryVoucherOfShop(@PathVariable("shopId") Long shopId) {
        return voucherService.queryVoucherOfShop(shopId);
    }

    /**
     * 全量秒杀券列表（附带库存与活动时间），供秒杀 Tab 展示（兼容保留）
     */
    @GetMapping("seckill/list")
    public Result<List<VoucherVO>> listSeckillVouchers() {
        return voucherService.listSeckillVouchers();
    }

    /**
     * 🆕 平台券列表（用户端"优惠券中心"双子页）：type=0 普通券 / 1 秒杀券，
     * 附带秒杀信息 + 适用范围 shopIds（空列表 = 全场通用）
     */
    @GetMapping("all")
    public Result<List<VoucherVO>> listByType(@RequestParam(value = "type", required = false) Integer type) {
        return voucherService.listByType(type);
    }

    /**
     * 🆕 管理端券分页（全量，按 id 倒序）：附带秒杀信息 + 适用范围（多店券回填 shopIds）
     * 使用范围：shop_id>0=单店；shop_id=0 且 shopIds 非空=指定多店；shop_id=0 且 shopIds 空=全场通用
     */
    @GetMapping("/page")
    public Result<List<VoucherVO>> pageForAdmin(@RequestParam(value = "current", defaultValue = "1") Integer current,
                                                @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return voucherService.pageForAdmin(current, pageSize);
    }

    /**
     * 查询优惠券详情（秒杀券附带库存与活动时间）
     */
    @GetMapping("/{id}")
    public Result<VoucherVO> queryVoucherById(@PathVariable("id") Long id) {
        VoucherVO voucher = voucherService.queryByIdWithStock(id);
        return voucher == null ? Result.error("优惠券不存在") : Result.success(voucher);
    }

    /**
     * 更新优惠券
     */
    @PutMapping
    public Result<Void> updateVoucher(@RequestBody VoucherDTO voucher) {
        voucherService.update(voucher);
        return Result.success();
    }

    /**
     * 删除优惠券
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteVoucher(@PathVariable("id") Long id) {
        voucherService.deleteById(id);
        return Result.success();
    }
}
