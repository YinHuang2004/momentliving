package com.momentliving.mapper;

import com.momentliving.vo.DashboardVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 管理后台 Dashboard 只读聚合查询。
 * 说明：管理端只读不写，直接跨表聚合（本项目各服务共用 momentliving 库），
 * 不通过 Feign 拆 N 个计数接口，避免为看板拉跨服务 N+1。
 */
@Mapper
public interface DashboardMapper {

    @Select("select count(*) from shop")
    long countShop();

    @Select("select count(*) from voucher")
    long countVoucher();

    @Select("select count(*) from merchant where status = 1")
    long countMerchant();

    /** 分类 → 店铺数分布（无店铺的分类也列出，count=0） */
    @Select("select st.name as name, count(s.id) as cnt from shop_type st " +
            "left join shop s on s.type_id = st.id " +
            "group by st.id, st.name order by st.sort")
    List<Map<String, Object>> typeDistribution();

    /** 近 7 日入驻申请量（按日聚合；缺日补零在 Java 侧做） */
    @Select("select date_format(create_time, '%m-%d') as d, count(*) as cnt " +
            "from merchant_apply " +
            "where create_time >= date_sub(curdate(), interval 6 day) " +
            "group by d order by d")
    List<Map<String, Object>> applyTrend();
}
