package com.momentliving.config;

import cn.hutool.core.util.RandomUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
@Configuration
public class PasswordEncoder {
    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder(){
        // 参数：加密强度 4~31，默认10，数字越大加密越慢、越安全
        return new BCryptPasswordEncoder(10);
    }

    //md5加密

//    public static String encode(String password) {
//        // 生成盐
//        String salt = RandomUtil.randomString(20);
//        // 加密
//        return encode(password, salt);
//    }
//
//    private static String encode(String password, String salt) {
//        // 加密
//        return salt + "@" + DigestUtils.md5DigestAsHex((password + salt).getBytes(StandardCharsets.UTF_8));
//    }
//
//    public static Boolean matches(String encodedPassword, String rawPassword) {
//        if (encodedPassword == null || rawPassword == null) {
//            return false;
//        }
//        if (!encodedPassword.contains("@")) {
//            throw new RuntimeException("密码格式不正确！");
//        }
//        String[] arr = encodedPassword.split("@");
//        // 获取盐
//        String salt = arr[0];
//        // 比较
//        return encodedPassword.equals(encode(rawPassword, salt));
//    }

}
