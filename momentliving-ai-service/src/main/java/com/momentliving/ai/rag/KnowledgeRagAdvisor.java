package com.momentliving.ai.rag;

import com.momentliving.ai.config.AiProperties;
import com.momentliving.ai.prompt.Prompts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 自研 RAG Advisor（同时实现 Call/Stream 两个 SPI）：
 * 调用模型前用「最后一条用户消息」做向量检索，把命中的知识片段拼进
 * system prompt（RAG_CONTEXT_TEMPLATE），与 Python 版把知识挂 system 的行为对齐
 * （区别于 Spring AI 内置 QuestionAnswerAdvisor 挂 user 消息的做法）。
 *
 * <p>检索失败仅告警、不阻断对话（静默降级为纯对话）。
 */
@Component
public class KnowledgeRagAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRagAdvisor.class);

    private final PgKnowledgeVectorStore vectorStore;
    private final AiProperties props;

    public KnowledgeRagAdvisor(PgKnowledgeVectorStore vectorStore, AiProperties props) {
        this.vectorStore = vectorStore;
        this.props = props;
    }

    @Override
    public String getName() {
        return "KnowledgeRagAdvisor";
    }

    @Override
    public int getOrder() {
        return 50;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        return chain.nextCall(augment(request));
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        return chain.nextStream(augment(request));
    }

    /** 检索知识片段并注入 system prompt；开关关闭/无用户消息/检索失败时原样放行。 */
    private ChatClientRequest augment(ChatClientRequest request) {
        if (!props.ragEnabled()) {
            return request;
        }
        String query = extractLastUserText(request.prompt());
        if (query.isBlank()) {
            return request;
        }
        try {
            List<Document> docs = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(query)
                    .topK(props.ragTopK())
                    .similarityThreshold(PgKnowledgeVectorStore.MIN_SCORE)
                    .build());
            String context = joinWithinLimit(docs, props.knowledgeMaxChars());
            if (context.isBlank()) {
                return request;
            }
            Prompt augmented = request.prompt()
                    .augmentSystemMessage(Prompts.RAG_CONTEXT_TEMPLATE.formatted(context));
            return request.mutate().prompt(augmented).build();
        } catch (Exception e) {
            log.warn("RAG 知识检索失败，跳过知识注入（对话继续）", e);
            return request;
        }
    }

    /** 取提示词中最后一条用户消息文本作为检索 query。 */
    private String extractLastUserText(Prompt prompt) {
        List<Message> messages = prompt.getInstructions();
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage um) {
                return um.getText() == null ? "" : um.getText();
            }
        }
        return "";
    }

    /** 按顺序累加片段，超过 maxChars 即停（与 Python 版 retrieve_context 拼接策略一致）。 */
    private String joinWithinLimit(List<Document> docs, int maxChars) {
        StringBuilder sb = new StringBuilder();
        for (Document doc : docs) {
            String text = doc.getText();
            if (text == null || text.isBlank()) {
                continue;
            }
            if (sb.length() > 0 && sb.length() + text.length() > maxChars) {
                break;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(text);
        }
        return sb.toString().strip();
    }
}
