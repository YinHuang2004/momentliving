package com.momentliving.utils;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.UUID;

/**
 * 阿里云 OSS 上传工具（配置前缀 aliyun.oss）
 *
 * 用法：@Resource OssUtil ossUtil;
 *       String url = ossUtil.upload(ossUtil.generateObjectKey("a.jpg", "blogs"), inputStream);
 */
@Component
@Data
@ConfigurationProperties(prefix = "aliyun.oss")
public class OssUtil {

    private String endpoint;        // 例如 oss-cn-hangzhou.aliyuncs.com
    private String accessKeyId;     // RAM 用户 AccessKey ID
    private String accessKeySecret; // RAM 用户 AccessKey Secret
    private String bucketName;      // 例如 momentliving-images
    private String urlPrefix;       // 例如 https://momentliving-images.oss-cn-hangzhou.aliyuncs.com

    /**
     * 有效访问前缀：优先取配置 urlPrefix；缺失时按 OSS 规范自动拼 https://{bucket}.{endpoint}，
     * 避免 urlPrefix 漏配导致返回 "null/avatars/..." 这类坏 URL。
     */
    public String effectiveUrlPrefix() {
        if (urlPrefix != null && !urlPrefix.isBlank()) {
            return urlPrefix;
        }
        return "https://" + bucketName + "." + endpoint;
    }

    /**
     * 上传文件流到 OSS
     *
     * @param objectKey   对象路径（如 blogs/0/3/uuid.jpg）
     * @param inputStream 文件流
     * @return 完整访问 URL
     */
    public String upload(String objectKey, InputStream inputStream, String contentType) {
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try {
            // ContentType 按真实类型设置：优先用 multipart 的 image/*，否则按对象名后缀兜底。
            // 不能所有图都写死 image/jpeg——PNG/WebP 被当成 JPEG 解码会显示灰图。
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(resolveContentType(objectKey, contentType));
            ossClient.putObject(bucketName, objectKey, inputStream, metadata);
        } finally {
            ossClient.shutdown();
        }
        return effectiveUrlPrefix() + "/" + objectKey;
    }

    /** 解析 Content-Type：可信的 image/* 直接用，否则按后缀推断，兜底 image/jpeg */
    private String resolveContentType(String objectKey, String contentType) {
        if (contentType != null && contentType.startsWith("image/")) {
            return contentType;
        }
        String lower = objectKey.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".bmp")) return "image/bmp";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        return "image/jpeg";
    }

    /**
     * 删除 OSS 对象
     *
     * @param objectKey 对象路径（不含 urlPrefix，调用方从完整 URL 中截取）
     */
    public void delete(String objectKey) {
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try {
            ossClient.deleteObject(bucketName, objectKey);
        } finally {
            ossClient.shutdown();
        }
    }

    /**
     * 生成唯一对象名：UUID + hashCode 哈希分目录（防单目录文件过多）
     * 例：blogs/0/3/8f4a2b1c9d3e.jpg
     *
     * @param originalFilename 原始文件名（取后缀）
     * @param dir              业务目录（blogs/shops/avatars/...）
     */
    public String generateObjectKey(String originalFilename, String dir) {
        // 后缀：兼容 "a.jpg" 与 "a.jpeg?x=1" 等异常情况，取最后一个 . 之后的内容
        int dot = originalFilename.lastIndexOf(".");
        String suffix = dot >= 0 ? originalFilename.substring(dot) : ".jpg";
        // 只保留字母数字，防止后缀带特殊字符
        suffix = suffix.replaceAll("[^a-zA-Z0-9.]", "").toLowerCase();
        if (suffix.length() > 10) {
            suffix = ".jpg";
        }

        String name = UUID.randomUUID().toString().replace("-", "");
        int hash = name.hashCode();
        int d1 = hash & 0xF;
        int d2 = (hash >> 4) & 0xF;
        return dir + "/" + d1 + "/" + d2 + "/" + name + suffix;
    }

    /**
     * 从完整 URL 中提取 objectKey（去掉 urlPrefix 前缀）
     */
    public String extractObjectKey(String url) {
        String prefix = effectiveUrlPrefix() + "/";
        return url.startsWith(prefix) ? url.substring(prefix.length()) : url;
    }
}
