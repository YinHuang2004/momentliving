package com.momentliving.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.momentliving.entity.Review;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 店铺评价 Mapper（评分聚合用 SQL 直查，走 idx_shop_id 索引）
 */
@Mapper
public interface ReviewMapper extends BaseMapper<Review> {

    /** 店铺平均分（无评价返回 null，由调用方兜底） */
    @Select("select avg(rating) from review where shop_id = #{shopId} and status = 1")
    Double selectAvgRating(Long shopId);

    /** 店铺有效评价数 */
    @Select("select count(*) from review where shop_id = #{shopId} and status = 1")
    Long selectReviewCount(Long shopId);
}
