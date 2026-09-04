package com.momentliving.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.momentliving.constant.RedisConstants;
import com.momentliving.dto.VoucherDTO;
import com.momentliving.entity.SeckillVoucher;
import com.momentliving.entity.Voucher;
import com.momentliving.entity.VoucherShop;
import com.momentliving.mapper.SeckillVoucherMapper;
import com.momentliving.mapper.VoucherMapper;
import com.momentliving.mapper.VoucherShopMapper;
import com.momentliving.result.Result;
import com.momentliving.service.SeckillVoucherService;
import com.momentliving.service.VoucherService;
import com.momentliving.vo.VoucherVO;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class VoucherServiceImpl implements VoucherService {

    @Resource
    private VoucherMapper voucherMapper;
    @Resource
    private VoucherShopMapper voucherShopMapper;
    @Resource
    private SeckillVoucherMapper seckillVoucherMapper;
    @Resource
    private SeckillVoucherService seckillVoucherService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Long save(VoucherDTO dto) {
        Voucher voucher = BeanUtil.copyProperties(dto, Voucher.class);
        voucherMapper.insert(voucher);
        saveScopeRows(voucher.getId(), dto);
        return voucher.getId();
    }

    private boolean isAllShop(VoucherDTO dto) {
        return Boolean.TRUE.equals(dto.getAllShop());
    }

    /** 适用店铺是否有效（至少一家） */
    private boolean hasShopIds(VoucherDTO dto) {
        return dto.getShopIds() != null && dto.getShopIds().stream().anyMatch(id -> id != null && id > 0);
    }

    /**
     * 🆕 适用范围落库（唯一事实源 = voucher_shop）：
     * 全场通用 → 清空关系记录；指定店铺 → 整表重写（1 条=单店券，N 条=多店券/批量发放）。
     */
    private void saveScopeRows(Long voucherId, VoucherDTO dto) {
        voucherShopMapper.delete(new LambdaQueryWrapper<VoucherShop>()
                .eq(VoucherShop::getVoucherId, voucherId));
        if (isAllShop(dto)) {
            return;
        }
        List<Long> shopIds = dto.getShopIds() == null ? List.of()
                : dto.getShopIds().stream().filter(id -> id != null && id > 0).distinct().toList();
        if (shopIds.isEmpty()) {
            throw new com.momentliving.exception.BadRequestException("请至少选择一家适用店铺，或选择全场通用");
        }
        shopIds.forEach(shopId -> voucherShopMapper.insert(
                VoucherShop.builder().voucherId(voucherId).shopId(shopId).build()));
    }

    @Override
    public Voucher getById(Long id) {
        return voucherMapper.selectById(id);
    }

    @Override
    public Result<List<VoucherVO>> queryVoucherOfShop(Long shopId) {
        // 范围 = 单店(shop_id=本店) + 多店(关系表含本店) + 全场通用(shop_id=0 且无关系记录)
        List<VoucherVO> vouchers = voucherMapper.selectVouchersOfShop(shopId).stream()
                .map(voucher -> BeanUtil.copyProperties(voucher, VoucherVO.class))
                .collect(Collectors.toList());
        fillSeckillInfo(vouchers);
        return Result.success(vouchers);
    }

    @Override
    public Result<List<VoucherVO>> listSeckillVouchers() {
        List<VoucherVO> vouchers = voucherMapper.selectList(new LambdaQueryWrapper<Voucher>()
                        .eq(Voucher::getType, 1)
                        .orderByDesc(Voucher::getId)).stream()
                .map(voucher -> BeanUtil.copyProperties(voucher, VoucherVO.class))
                .collect(Collectors.toList());
        fillSeckillInfo(vouchers);
        return Result.success(vouchers);
    }

    /**
     * 批量联查 seckill_voucher，为秒杀券回填库存与活动时间（避免 N+1）。
     */
    private void fillSeckillInfo(List<VoucherVO> vouchers) {
        List<Long> seckillIds = vouchers.stream()
                .filter(v -> v.getType() != null && v.getType() == 1)
                .map(VoucherVO::getId)
                .collect(Collectors.toList());
        if (seckillIds.isEmpty()) {
            return;
        }
        Map<Long, SeckillVoucher> seckillMap = seckillVoucherMapper.selectBatchIds(seckillIds).stream()
                .collect(Collectors.toMap(SeckillVoucher::getVoucherId, sv -> sv, (a, b) -> a));
        vouchers.forEach(v -> {
            SeckillVoucher sv = seckillMap.get(v.getId());
            if (sv != null) {
                v.setStock(sv.getStock());
                v.setBeginTime(sv.getBeginTime());
                v.setEndTime(sv.getEndTime());
            }
        });
    }

    @Override
    @Transactional
    public Long addSeckillVoucher(VoucherDTO dto) {
        // 保存优惠券
        Voucher voucher = BeanUtil.copyProperties(dto, Voucher.class);
        voucherMapper.insert(voucher);
        saveScopeRows(voucher.getId(), dto);
        // 保存秒杀信息
        SeckillVoucher seckillVoucher =SeckillVoucher.builder()
                .voucherId(voucher.getId())
                .stock(dto.getStock())
                .beginTime(dto.getBeginTime())
                .endTime(dto.getEndTime())
                .build();
        seckillVoucherService.save(seckillVoucher);
        // 保存秒杀库存到 Redis 中
        stringRedisTemplate.opsForValue().set(
                RedisConstants.SECKILL_STOCK_KEY + voucher.getId(),
                dto.getStock().toString()
        );
        return voucher.getId();
    }

    @Override
    public VoucherVO queryByIdWithStock(Long id) {
        Voucher voucher = voucherMapper.selectById(id);
        if (voucher == null) {
            return null;
        }
        VoucherVO vo = BeanUtil.copyProperties(voucher, VoucherVO.class);
        // 秒杀券：联查 seckill_voucher 补库存与活动时间
        if (voucher.getType() != null && voucher.getType() == 1) {
            SeckillVoucher seckillVoucher = seckillVoucherService.getById(id);
            if (seckillVoucher != null) {
                vo.setStock(seckillVoucher.getStock());
                vo.setBeginTime(seckillVoucher.getBeginTime());
                vo.setEndTime(seckillVoucher.getEndTime());
            }
        }
        // 🆕 适用范围：shopIds（空=全场通用）+ 适用店铺精简信息（券详情页展示）
        vo.setShopIds(voucherShopMapper.selectList(new LambdaQueryWrapper<VoucherShop>()
                        .eq(VoucherShop::getVoucherId, id)).stream()
                .map(VoucherShop::getShopId).collect(Collectors.toList()));
        vo.setScopeShops(voucherMapper.selectScopeShops(id));
        return vo;
    }

    @Override
    @Transactional
    public void update(VoucherDTO dto) {
        Voucher voucher = BeanUtil.copyProperties(dto, Voucher.class);
        voucherMapper.updateById(voucher);
        // 范围可能被编辑（单店↔全场↔多店），关系表整表重写
        saveScopeRows(dto.getId(), dto);
        // 秒杀券：同步更新秒杀信息 + Redis 库存
        if (dto.getType() != null && dto.getType() == 1) {
            SeckillVoucher seckillVoucher = SeckillVoucher.builder()
                    .voucherId(dto.getId())
                    .stock(dto.getStock())
                    .beginTime(dto.getBeginTime())
                    .endTime(dto.getEndTime())
                    .build();
            seckillVoucherService.update(seckillVoucher);
            if (dto.getStock() != null) {
                stringRedisTemplate.opsForValue().set(
                        RedisConstants.SECKILL_STOCK_KEY + dto.getId(),
                        dto.getStock().toString()
                );
            }
        }
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        Voucher voucher = voucherMapper.selectById(id);
        if (voucher == null) {
            return;
        }
        voucherMapper.deleteById(id);
        // 清理多店券适用店铺关系
        voucherShopMapper.delete(new LambdaQueryWrapper<VoucherShop>()
                .eq(VoucherShop::getVoucherId, id));
        // 秒杀券：删除秒杀信息与 Redis 库存
        if (voucher.getType() != null && voucher.getType() == 1) {
            seckillVoucherService.deleteByVoucherId(id);
            stringRedisTemplate.delete(RedisConstants.SECKILL_STOCK_KEY + id);
        }
    }

    /**
     * 管理端券分页（全量，按 id 倒序）：回填秒杀信息 + 多店券的适用店铺ID列表。
     */
    public Result<List<VoucherVO>> pageForAdmin(Integer current, Integer pageSize) {
        Page<Voucher> page = voucherMapper.selectPage(
                new Page<>(current == null ? 1 : current, pageSize == null ? 10 : pageSize),
                new LambdaQueryWrapper<Voucher>().orderByDesc(Voucher::getId));
        List<VoucherVO> vouchers = page.getRecords().stream()
                .map(v -> BeanUtil.copyProperties(v, VoucherVO.class))
                .collect(Collectors.toList());
        fillSeckillInfo(vouchers);
        fillScopeShopIds(vouchers);
        return Result.success(vouchers);
    }

    /** 🆕 批量回填适用店铺ID列表（全场券置空列表，单店/多店为各自 ID；一次查询分组避免 N+1） */
    private void fillScopeShopIds(List<VoucherVO> vouchers) {
        if (vouchers.isEmpty()) {
            return;
        }
        List<Long> ids = vouchers.stream().map(VoucherVO::getId).collect(Collectors.toList());
        Map<Long, List<VoucherShop>> groups = voucherShopMapper.selectList(
                        new LambdaQueryWrapper<VoucherShop>().in(VoucherShop::getVoucherId, ids))
                .stream().collect(Collectors.groupingBy(VoucherShop::getVoucherId));
        vouchers.forEach(v -> v.setShopIds(
                groups.getOrDefault(v.getId(), List.of()).stream()
                        .map(VoucherShop::getShopId).collect(Collectors.toList())));
    }

    @Override
    public Result<List<VoucherVO>> listByType(Integer type) {
        List<VoucherVO> vouchers = voucherMapper.selectList(new LambdaQueryWrapper<Voucher>()
                        .eq(type != null, Voucher::getType, type)
                        .orderByDesc(Voucher::getId)).stream()
                .map(v -> BeanUtil.copyProperties(v, VoucherVO.class))
                .collect(Collectors.toList());
        fillSeckillInfo(vouchers);
        fillScopeShopIds(vouchers);
        return Result.success(vouchers);
    }
}
