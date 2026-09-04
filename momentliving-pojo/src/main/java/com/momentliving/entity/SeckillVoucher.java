package com.momentliving.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.*;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SeckillVoucher implements Serializable {

    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)

    private Long voucherId;
    private Integer stock;
    private LocalDateTime createTime;
    private LocalDateTime beginTime;
    private LocalDateTime endTime;
    private LocalDateTime updateTime;
}
