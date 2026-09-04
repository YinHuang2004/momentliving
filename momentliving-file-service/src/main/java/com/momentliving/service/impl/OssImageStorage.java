package com.momentliving.service.impl;

import com.momentliving.service.ImageStorage;
import com.momentliving.utils.OssUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 阿里云 OSS 存储实现（项目唯一存储方式）
 * 上传 → OSS Bucket → 返回完整 URL（https://momentliving-images.oss-cn-hangzhou.aliyuncs.com/...）
 */
@Component
public class OssImageStorage implements ImageStorage {

    @Resource
    private OssUtil ossUtil;

    @Override
    public String upload(MultipartFile file, String dir) {
        try {
            String objectKey = ossUtil.generateObjectKey(file.getOriginalFilename(), dir);
            return ossUtil.upload(objectKey, file.getInputStream(), file.getContentType());
        } catch (IOException e) {
            throw new RuntimeException("OSS 上传失败", e);
        }
    }

    @Override
    public void delete(String url) {
        ossUtil.delete(ossUtil.extractObjectKey(url));
    }
}
