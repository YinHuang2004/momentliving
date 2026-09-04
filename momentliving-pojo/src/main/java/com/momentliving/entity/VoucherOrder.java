package com.momentliving.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.*;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class VoucherOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private Long voucherId;
    private Integer payType;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime payTime;
    private LocalDateTime useTime;
    private LocalDateTime refundTime;
    private LocalDateTime updateTime;

    /** 16 位核销码（非库字段，查询时从 voucher_verify 表回填，用户出示给商家核销） */
    @TableField(exist = false)
    private String verifyCode;
}
