package com.momentliving.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.momentliving.entity.Voucher;
import com.momentliving.vo.ShopSimpleVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface VoucherMapper extends BaseMapper<Voucher> {

    /**
     * 某店铺可用的券 = 适用于本店的券（voucher_shop 含本店）+ 全场通用券（无任何适用店铺记录）。
     * 范围唯一事实源 = voucher_shop（多对多），voucher 表已无 shop_id 冗余列。
     */
    @Select("""
            SELECT v.* FROM voucher v
            WHERE EXISTS (SELECT 1 FROM voucher_shop vs
                          WHERE vs.voucher_id = v.id AND vs.shop_id = #{shopId})
               OR NOT EXISTS (SELECT 1 FROM voucher_shop vs WHERE vs.voucher_id = v.id)
            ORDER BY v.id DESC
            """)
    List<Voucher> selectVouchersOfShop(@Param("shopId") Long shopId);

    /** 🆕 券详情页：适用店铺精简信息（全场通用券返回空列表） */
    @Select("""
            SELECT s.id, s.name, s.images, s.address
            FROM shop s
            JOIN voucher_shop vs ON vs.shop_id = s.id
            WHERE vs.voucher_id = #{voucherId}
            ORDER BY s.id
            """)
    List<ShopSimpleVO> selectScopeShops(@Param("voucherId") Long voucherId);
}
