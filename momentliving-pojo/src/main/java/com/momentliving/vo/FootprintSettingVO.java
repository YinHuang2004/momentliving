package com.momentliving.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 足迹（购物记录）可见性设置
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FootprintSettingVO {
    /** 足迹是否对他人可见（缺省可见） */
    private Boolean visible;
    /** 足迹清空时间戳（毫秒，0=未清空；早于该时间的记录对他人隐藏） */
    private Long clearedTime;
}
