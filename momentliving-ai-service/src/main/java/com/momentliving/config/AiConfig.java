package com.momentliving.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Spring AI 核心配置：ChatClient（C 端默认人设）+ SSE 异步线程池
 */
@Configuration
public class AiConfig {

    /**
     * C 端用户助手「小评」：贴合本地生活点评场景，涉数据必走工具调用，禁止编造。
     * 商家端人设在 AiPromptConstants.B_END_SYSTEM，由商家接口运行时覆盖。
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("""
                        你是一刻生活（本地生活服务点评平台）的智能助手「小评」。
                        你的职责是帮助用户发现好店、用好优惠券、解答平台使用问题、生成探店内容。
                        回答要求：
                        1. 回答简洁友好，符合本地生活场景，默认使用中文；
                        2. 涉及店铺/优惠券/博客等平台数据时，必须调用工具获取实时数据，禁止编造数据；
                        3. 不知道或超出平台范围的问题，引导用户查看帮助中心或联系客服；
                        4. 不回答与平台业务无关的问题（闲聊、时事、代码等）。
                        """)
                .build();
    }

    /**
     * SSE 流式对话 / 异步标题生成的执行线程池。
     * 独立线程池避免占用 Tomcat 工作线程；注意：线程池线程没有 ThreadLocal 用户身份，
     * 需要用户身份的工具（VoucherTools 等）一律走 ToolContext 显式传 ID。
     */
    @Bean("aiExecutor")
    public ThreadPoolTaskExecutor aiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("ai-exec-");
        executor.initialize();
        return executor;
    }
}
