package com.momentliving.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.momentliving.entity.Admin;

/** 管理员表 Mapper（入驻申请时校验账号与管理员是否冲突，两服务共库直查） */
public interface AdminMapper extends BaseMapper<Admin> {
}
