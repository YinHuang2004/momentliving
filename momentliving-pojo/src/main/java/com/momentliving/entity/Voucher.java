package com.momentliving.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
public class Voucher implements Serializable {

    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String subTitle;
    private String rules;
    private Long payValue;
    private Long actualValue;
    private Integer type;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
