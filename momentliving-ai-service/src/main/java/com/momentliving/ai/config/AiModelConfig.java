package com.momentliving.ai.config;

import com.momentliving.ai.memory.HybridChatMemory;
import com.momentliving.ai.prompt.Prompts;
import com.momentliving.ai.rag.KnowledgeRagAdvisor;
import com.momentliving.ai.tool.BlogTools;
import com.momentliving.ai.tool.MerchantTools;
import com.momentliving.ai.tool.ShopTools;
import com.momentliving.ai.tool.VoucherTools;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Spring AI 装配：
 * <ul>
 *   <li>ChatModel —— Spring AI Alibaba 自动配置的 DashScope 原生实现（qwen-plus）</li>
 *   <li>EmbeddingModel —— Spring AI 官方 OpenAI 协议实现，指向硅基流动 bge-m3（1024 维）</li>
 *   <li>ChatClient —— 默认 system prompt + 混合记忆 Advisor + 自研 RAG Advisor + 6 个工具</li>
 *   <li>RestClient —— 上游微服务调用（工具内使用）</li>
 * </ul>
 */
@Configuration
public class AiModelConfig {

    /**
     * Embedding 模型：OpenAI 兼容协议连硅基流动。
     * ⚠️ @Primary：SAA starter 也会装配 DashScopeEmbeddingModel，检索必须走 bge-m3（与
     * PG vector(1024) 维度对齐），混用其他 embedding 会直接维度不匹配报错。
     */
    @Bean
    @Primary
    public EmbeddingModel embeddingModel(AiProperties props) {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(props.embedding().baseUrl())
                .apiKey(props.embedding().apiKey())
                .build();
        return new OpenAiEmbeddingModel(api, MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder().model(props.embedding().model()).build());
    }

    /** 注册 @Tool 工具对象（6 个 C 端工具 + 2 个商家工具，商家工具有身份守卫）。 */
    @Bean
    public ToolCallbackProvider toolCallbackProvider(ShopTools shopTools, BlogTools blogTools,
                                                     VoucherTools voucherTools, MerchantTools merchantTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(shopTools, blogTools, voucherTools, merchantTools)
                .build();
    }

    /**
     * 工具抛异常时「不」中断对话：异常信息作为工具结果返回给模型，由 LLM 兜底解释
     * （对齐 Python 版 _run_tool 的行为：上游服务挂了对话不中断，只是查不到实时数据）。
     */
    @Bean
    public ToolExecutionExceptionProcessor toolExecutionExceptionProcessor() {
        return new DefaultToolExecutionExceptionProcessor(false);
    }

    /** 上游微服务调用客户端（带读写超时）。 */
    @Bean
    public RestClient upstreamRestClient(AiProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(props.upstream().timeoutSeconds()));
        factory.setReadTimeout(Duration.ofSeconds(props.upstream().timeoutSeconds()));
        return RestClient.builder().requestFactory(factory).build();
    }

    /**
     * 主对话客户端（「小评」）：
     * 默认 system = C_END_SYSTEM；Advisor 链 = 混合记忆 + 自研 RAG 注入；
     * 工具 = 全量 @Tool 对象；conversationId 与身份按请求传入。
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel, ChatMemory chatMemory,
                                 ToolCallbackProvider toolCallbackProvider, KnowledgeRagAdvisor ragAdvisor) {
        return ChatClient.builder(chatModel)
                .defaultSystem(Prompts.C_END_SYSTEM)
                .defaultToolCallbacks(toolCallbackProvider)
                .defaultAdvisors(new Advisor[]{
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        ragAdvisor})
                .build();
    }
}
