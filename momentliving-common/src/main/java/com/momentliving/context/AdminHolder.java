package com.momentliving.context;

import com.momentliving.vo.AdminVO;

/**
 * ThreadLocal 管理员上下文（管理端专用，与 UserHolder 隔离）
 * 用于管理端请求线程中传递管理员信息
 */
public class AdminHolder {
    private static final ThreadLocal<AdminVO> tl = new ThreadLocal<>();

    public static void saveAdmin(AdminVO admin) {
        tl.set(admin);
    }

    public static AdminVO getAdmin() {
        return tl.get();
    }

    public static void removeAdmin() {
        tl.remove();
    }
}
