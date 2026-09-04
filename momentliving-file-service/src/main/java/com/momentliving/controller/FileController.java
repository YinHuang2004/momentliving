package com.momentliving.controller;

import com.momentliving.result.Result;
import com.momentliving.service.ImageStorage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

/**
 * 统一文件服务接口
 *
 * POST   /file/upload?dir=blogs|shops|avatars|icons|vouchers|reviews  上传
 * DELETE /file/delete?url=xxx                                         删除
 *
 * 安全校验：目录白名单 + 图片后缀白名单 + 文件名清洗；文件大小由 Nacos 配置
 * spring.servlet.multipart.max-file-size 限制（默认 5MB）。
 */
@Slf4j
@RestController
public class FileController {

    /** 允许的业务目录白名单（防止任意路径穿越到 OSS 其他目录） */
    private static final Set<String> ALLOWED_DIRS = Set.of(
            "blogs", "shops", "avatars", "icons", "vouchers", "reviews"
    );

    /** 允许的图片后缀白名单（SVG 禁止：可内嵌 JavaScript 导致 XSS） */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "webp", "gif", "bmp"
    );

    @Resource
    private ImageStorage imageStorage;

    /**
     * 通用上传：POST /file/upload?dir=blogs （form-data: file）
     */
    @PostMapping("/file/upload")
    public Result<String> upload(@RequestParam("file") MultipartFile file,
                                 @RequestParam(value = "dir", defaultValue = "blogs") String dir) {
        if (file == null || file.isEmpty()) {
            return Result.error("文件不能为空");
        }
        String safeDir = checkDir(dir);
        String extension = getSafeExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            return Result.error("不支持的文件类型: " + extension + "，允许: " + ALLOWED_EXTENSIONS);
        }
        String url = imageStorage.upload(file, safeDir);
        log.debug("文件上传成功: dir={}, ext={}, url={}", safeDir, extension, url);
        return Result.success(url);
    }

    /**
     * 通用删除：DELETE /file/delete?url=xxx
     */
    @DeleteMapping("/file/delete")
    public Result<Void> delete(@RequestParam("url") String url) {
        if (url == null || url.isBlank()) {
            return Result.error("url 不能为空");
        }
        imageStorage.delete(url);
        return Result.success();
    }

    /** 校验业务目录白名单，防止 dir 传入 ../../ 等路径穿越 */
    private String checkDir(String dir) {
        if (!ALLOWED_DIRS.contains(dir)) {
            throw new IllegalArgumentException("非法的业务目录: " + dir + "，允许值: " + ALLOWED_DIRS);
        }
        return dir;
    }

    /** 从原始文件名提取安全后缀（小写、去空格、防路径穿越） */
    private String getSafeExtension(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "";
        }
        // 取最后一个 . 之后的部分，防止 a.b.c.png 取错；去目录分隔符防穿越
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == originalFilename.length() - 1) {
            return "";
        }
        return originalFilename.substring(dotIndex + 1).trim().toLowerCase()
                .replaceAll("[\\\\/]", "");
    }
}
