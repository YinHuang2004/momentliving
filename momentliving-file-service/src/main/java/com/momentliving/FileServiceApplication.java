package com.momentliving;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 文件服务启动类
 * 职责：全项目统一文件上传/删除（博客图、店铺图、头像、券图、评价图、类型图标）
 * 存储策略：LocalImageStorage（本地磁盘）/ OssImageStorage（阿里云 OSS），由 storage.type 配置切换
 */
@SpringBootApplication
public class FileServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(FileServiceApplication.class, args);
    }
}
