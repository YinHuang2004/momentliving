package com.momentliving.ai.controller;

import com.momentliving.ai.common.UserIdentity;
import com.momentliving.ai.dto.KnowledgeDocVO;
import com.momentliving.ai.dto.KnowledgeSearchRequest;
import com.momentliving.ai.dto.KnowledgeUploadRequest;
import com.momentliving.ai.service.KnowledgeService;
import com.momentliving.result.Result;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识库管理接口（仅平台管理员：X-Admin-Id）。
 */
@RestController
@RequestMapping("/ai/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @PostMapping("/upload")
    public Result<KnowledgeDocVO> upload(
            @RequestHeader(value = UserIdentity.HEADER_ADMIN, required = false) String admin,
            @RequestBody(required = false) KnowledgeUploadRequest req) {
        UserIdentity identity = UserIdentity.of(null, null, admin);
        return Result.success(knowledgeService.upload(identity, req));
    }

    @GetMapping("/list")
    public Result<List<KnowledgeDocVO>> list(
            @RequestHeader(value = UserIdentity.HEADER_ADMIN, required = false) String admin) {
        return Result.success(knowledgeService.list(UserIdentity.of(null, null, admin)));
    }

    @DeleteMapping("/{docId}")
    public Result<Void> delete(
            @RequestHeader(value = UserIdentity.HEADER_ADMIN, required = false) String admin,
            @PathVariable long docId) {
        knowledgeService.delete(UserIdentity.of(null, null, admin), docId);
        return Result.success();
    }

    @PostMapping("/search")
    public Result<String> search(
            @RequestHeader(value = UserIdentity.HEADER_ADMIN, required = false) String admin,
            @RequestBody(required = false) KnowledgeSearchRequest req) {
        return Result.success(knowledgeService.searchPreview(UserIdentity.of(null, null, admin), req));
    }
}
