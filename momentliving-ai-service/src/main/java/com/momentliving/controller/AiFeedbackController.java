package com.momentliving.controller;

import com.momentliving.dto.AiFeedbackDTO;
import com.momentliving.result.Result;
import com.momentliving.service.AiConversationService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 回答反馈（点赞/点踩/评分，用于优化 Prompt 与知识库）
 */
@RestController
@RequestMapping("/ai/feedback")
public class AiFeedbackController {

    @Resource
    private AiConversationService conversationService;

    @PostMapping
    public Result<Void> feedback(@RequestBody AiFeedbackDTO dto) {
        conversationService.saveFeedback(dto);
        return Result.success();
    }
}
