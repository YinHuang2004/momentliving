package com.momentliving.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Shop implements Serializable {

    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Long typeId;
    private String images;
    private String area;
    private String address;
    private Double x;
    private Double y;
    private Long avgPrice;
    private Integer sold;
    private Integer comments;
    private Integer score;
    private String openHours;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
