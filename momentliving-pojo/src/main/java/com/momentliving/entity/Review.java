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
 * 店铺评价表（大众点评灵魂功能）
 * 对应表：review
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Review implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 评价人ID */
    private Long userId;

    /** 店铺ID */
    private Long shopId;

    /** 关联订单ID（可为空：未下单直接评价） */
    private Long orderId;

    /** 星级评分 1-5 */
    private Integer rating;

    /** 评价内容 */
    private String content;

    /** 图片URL（逗号分隔） */
    private String images;

    /** 状态：1正常 0已删除 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
