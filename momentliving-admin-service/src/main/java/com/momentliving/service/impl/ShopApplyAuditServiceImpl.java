package com.momentliving.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.momentliving.api.client.ShopClient;
import com.momentliving.context.AdminHolder;
import com.momentliving.dto.ShopApplyAuditDTO;
import com.momentliving.dto.ShopDTO;
import com.momentliving.entity.Admin;
import com.momentliving.entity.ShopApply;
import com.momentliving.exception.BadRequestException;
import com.momentliving.mapper.AdminMapper;
import com.momentliving.mapper.ShopApplyMapper;
import com.momentliving.result.Result;
import com.momentliving.service.ShopApplyAuditService;
import io.seata.spring.annotation.GlobalTransactional;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 开店申请审核实现。
 *
 * <p>规则：管理员不能直接新增店铺，店铺上线只有一条路 —— 商家在 merchant-service 提交
 * 开店申请（shop_apply），这里审核通过后经 Seata 全局事务调 shop-service 建店：
 * <ul>
 *   <li>① Feign 调 shop-service /shop/feign/create 创建 shop 记录（跨服务分支事务）</li>
 *   <li>② 本服务回填申请状态/审核人/shopId（本地分支事务）</li>
 * </ul>
 * 任何一步失败（下游异常、演练开关），已建的店与申请状态一起按 undo_log 回滚；
 * 待审核/被拒绝的申请不产生 shop 记录，店铺对外天然不可见 —— 这就是"审核后才上线"。
 */
@Slf4j
@Service
public class ShopApplyAuditServiceImpl implements ShopApplyAuditService {

    @Resource
    private ShopApplyMapper shopApplyMapper;
    @Resource
    private AdminMapper adminMapper;
    @Resource
    private ShopClient shopClient;

    @Override
    public Result<List<ShopApply>> list(Integer status, Integer current, Integer pageSize) {
        checkAdmin();
        LambdaQueryWrapper<ShopApply> wrapper = new LambdaQueryWrapper<ShopApply>()
                .eq(status != null, ShopApply::getStatus, status)
                .orderByDesc(ShopApply::getCreateTime);
        Page<ShopApply> page = shopApplyMapper.selectPage(
                new Page<>(current == null ? 1 : current, pageSize == null ? 10 : pageSize), wrapper);
        return Result.success(page.getRecords());
    }

    @Override
    @GlobalTransactional(rollbackFor = Exception.class, name = "momentliving-shop-apply-audit")
    public Result<Void> audit(ShopApplyAuditDTO dto) {
        // 1. 仅平台管理员可审核
        Admin actor = checkAdmin();
        if (dto.getId() == null || dto.getApproved() == null) {
            throw new BadRequestException("缺少申请ID或审核结论");
        }
        if (!dto.getApproved() && StrUtil.isBlank(dto.getReason())) {
            throw new BadRequestException("拒绝时必须填写原因");
        }
        // 2. 申请必须存在且处于待审核
        ShopApply apply = shopApplyMapper.selectById(dto.getId());
        if (apply == null) {
            throw new BadRequestException("申请不存在");
        }
        if (apply.getStatus() != ShopApply.STATUS_PENDING) {
            throw new BadRequestException("该申请已处理过，请勿重复审核");
        }

        if (dto.getApproved()) {
            // 3a. 通过：Seata 全局事务里跨服务建店（审核通过 = 店铺上线）
            ShopDTO shop = new ShopDTO();
            shop.setName(apply.getShopName());
            shop.setAddress(StrUtil.blankToDefault(apply.getAddress(), ""));
            shop.setTypeId(apply.getTypeId() == null ? 1L : apply.getTypeId());
            shop.setImages("");         // shop 表 NOT NULL 占位，商家开店后在工作台补全图片
            shop.setX(116.397428);      // 默认坐标（后续 GEO 搜索可用），商家可编辑
            shop.setY(39.90923);
            shop.setSold(0);
            shop.setComments(0);
            shop.setScore(0);
            // ① 跨服务分支：店铺创建下沉到 shop-service（XID 经 Feign 头透传，下游注册分支事务）
            Result<Long> created = shopClient.create(shop);
            if (created == null || created.getCode() != 1 || created.getData() == null) {
                // 调不通/下游拒绝 → 全局回滚（本地已写的申请状态也会被 undo_log 逆补偿）
                throw new IllegalStateException("店铺创建失败：" + (created == null ? "下游无响应" : created.getMsg()));
            }
            apply.setShopId(created.getData());
            apply.setStatus(ShopApply.STATUS_APPROVED);
            log.info("开店申请审核通过 applyId={}, Seata 建店 shopId={}, 申请人 merchantId={}",
                    apply.getId(), apply.getShopId(), apply.getMerchantId());
        } else {
            // 3b. 拒绝：记录原因，商家可再提交新申请
            apply.setStatus(ShopApply.STATUS_REJECTED);
            apply.setRejectReason(dto.getReason());
            log.info("开店申请审核拒绝 applyId={}, 原因: {}", apply.getId(), dto.getReason());
        }
        apply.setAuditBy(actor.getId());
        apply.setAuditTime(LocalDateTime.now());
        shopApplyMapper.updateById(apply);

        // 🆕 回滚演练（教学用）：此时店铺与申请状态都已写入，抛异常验证 Seata 把两边一起回滚
        if (Boolean.TRUE.equals(dto.getDrillFail())) {
            throw new IllegalStateException("【演练】模拟审核后半段失败 → 触发 Seata 全局回滚（店铺/申请状态均应还原）");
        }
        return Result.success();
    }

    /** 校验当前登录者是平台管理员并返回其账号信息（admin 表只存平台管理员） */
    private Admin checkAdmin() {
        com.momentliving.vo.AdminVO holder = AdminHolder.getAdmin();
        if (holder == null) {
            throw new BadRequestException("未登录");
        }
        // 拦截器只塞了 id，账号以 DB 为准（防止 token 未过期但账号已被删除/禁用）
        Admin admin = adminMapper.selectById(holder.getId());
        if (admin == null) {
            throw new BadRequestException("仅平台管理员可执行该操作");
        }
        return admin;
    }
}
