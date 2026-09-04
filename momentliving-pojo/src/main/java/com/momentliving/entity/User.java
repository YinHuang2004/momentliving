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
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(type= IdType.AUTO)
    private Long id;
    private String phone;
    private String email;
    private String nickName;
    private String images;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
