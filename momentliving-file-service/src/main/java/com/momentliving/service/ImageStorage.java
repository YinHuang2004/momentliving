package com.momentliving.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储接口（面向接口编程）
 * 当前唯一实现：OssImageStorage（阿里云 OSS 直传）
 * 后续如需本地存储/CDN，新增实现类即可，业务侧零改动
 */
public interface ImageStorage {

    /**
     * 上传文件
     *
     * @param file 待上传文件
     * @param dir  业务目录（blogs/shops/avatars/icons/vouchers/reviews）
     * @return 可访问的完整 URL
     */
    String upload(MultipartFile file, String dir);

    /**
     * 删除文件
     *
     * @param url 上传时返回的完整 URL
     */
    void delete(String url);
}
