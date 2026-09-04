package com.momentliving.context;


import com.momentliving.vo.UserVO;

/**
 * ThreadLocal 用户上下文，用于在当前请求线程中传递用户信息
 * ⭐ 已上移到 momentliving-common：供所有服务（含 momentliving-api 的 Feign 拦截器）共享，避免每个服务复制一份
 */
public class UserHolder {
    private static final ThreadLocal<UserVO> tl = new ThreadLocal<>();

    public static void saveUser(UserVO user) {
        tl.set(user);
    }

    public static UserVO getUser() {
        return tl.get();
    }

    public static void removeUser() {
        tl.remove();
    }
}
