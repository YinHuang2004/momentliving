package com.momentliving.api.client;

import com.momentliving.api.config.FileClientConfig;
import com.momentliving.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件服务 Feign 客户端：业务服务内部上传/删除文件统一走它。
 *
 * 用法：
 * {@code
 *   @Resource
 *   private FileClient fileClient;
 *
 *   String url = fileClient.upload(file, "shops").getData();
 *   fileClient.delete(url);
 * }
 *
 * dir 允许值：blogs / shops / avatars / icons / vouchers / reviews
 */
@FeignClient(name = "file-service", configuration = FileClientConfig.class)
public interface FileClient {

    /**
     * 上传文件
     *
     * @param file 文件（feign-form 编码为 multipart/form-data）
     * @param dir  业务目录（blogs/shops/avatars/icons/vouchers/reviews）
     * @return 完整访问 URL
     */
    @PostMapping(value = "/file/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Result<String> upload(@RequestPart("file") MultipartFile file,
                          @RequestParam("dir") String dir);

    /**
     * 删除文件
     *
     * @param url 上传时返回的完整 URL
     */
    @DeleteMapping("/file/delete")
    Result<Void> delete(@RequestParam("url") String url);
}
