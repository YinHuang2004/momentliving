"""对话主服务（替代 AiChatServiceImpl）。

链路：Hybrid 历史 + RAG 检索 → system 装配 → 工具调用循环（LLM↔Tools）→
流式 SSE（meta / 默认 data / done / error，与 Spring 100% 对齐）。

安全设计（与 Java 版一致）：
- 用户输入只作为 HumanMessage，禁止拼入 System Prompt（防 Prompt 注入）；
- 工具查"用户数据"时身份一律走 ContextVar（服务端写入，AI 决定不了查谁）。
"""
import asyncio
import json
from datetime import datetime
from typing import AsyncIterator

from langchain_core.messages import AIMessage, HumanMessage, SystemMessage, ToolMessage
from langchain_core.language_models import BaseChatModel
from langchain_core.embeddings import Embeddings

from app.clients.auth_header import (
    reset_current_user_id,
    set_current_user_id,
)
from app.config import Settings
from app.exception_handlers import BusinessException
from app.logging_config import get_logger
from app.memory.hybrid_history import HybridChatMessageHistory
from app.prompts.constants import C_END_SYSTEM, RAG_CONTEXT_TEMPLATE
from app.retrievers.pgvector_retriever import PgvectorRetriever
from app.services import conversation_service
from app.tools import C_END_TOOLS

logger = get_logger(__name__)

AI_UNAVAILABLE = "AI 服务暂时不可用，请稍后重试"
MAX_TOOL_ITERATIONS = 6


def _content_text(content) -> str:
    """LLM content 兼容处理：str 或多段 list。"""
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts = []
        for c in content:
            if isinstance(c, str):
                parts.append(c)
            elif isinstance(c, dict):
                parts.append(str(c.get("text") or ""))
        return "".join(parts)
    return str(content or "")


class ChatService:
    def __init__(self, settings: Settings, chat_model: BaseChatModel, embedding: Embeddings):
        self.settings = settings
        self.chat_model = chat_model
        self.retriever = PgvectorRetriever(embedding)
        self.tools = {t.name: t for t in C_END_TOOLS}
        self.llm_with_tools = chat_model.bind_tools(C_END_TOOLS)

    # ===== 对外入口 =====

    async def chat(self, message: str, conversation_id: int | None, user_id: int, user_type: int) -> dict:
        """同步对话（POST /ai/chat）。返回 AiMessage VO（camelCase）。"""
        self._check_enabled()
        message = self._validate(message)
        conv_id = (await conversation_service.get_or_create(conversation_id, user_id, user_type))[0]

        token = set_current_user_id(user_id)
        try:
            history = await self._load_history(conv_id)
            knowledge = await self._retrieve_knowledge(message)
            # 用户消息落库（Redis 窗口 + PG）
            saved_user = await history.aadd_message(HumanMessage(content=message))

            reply = await self._invoke_with_tools(C_END_SYSTEM, knowledge, history, message)

            saved = await history.aadd_message(AIMessage(content=reply))
            await conversation_service.touch_conversation(conv_id, reply)
            conversation_service.spawn_title_task(conv_id, message, self.chat_model)

            return {
                "id": saved[0] if saved else 0,
                "conversationId": conv_id,
                "role": "assistant",
                "content": reply,
                "toolCalls": None,
                "createdAt": (saved[1] if saved else datetime.now()).isoformat(timespec="seconds"),
            }
        finally:
            reset_current_user_id(token)

    async def stream_events(
        self, message: str, conversation_id: int | None, user_id: int, user_type: int
    ) -> AsyncIterator[dict]:
        """
        流式对话 SSE 事件生成器（与 Spring 协议 100% 对齐）：
        1. meta:  {"conversationId":123}   —— 首推
        2. 默认:  "文本增量"                —— 无 event 名
        3. done:  "[DONE]"
        4. error: 友好提示                  —— 流内异常只能走事件，不能 raise
        """
        try:
            self._check_enabled()
            message = self._validate(message)
            conv_id = (await conversation_service.get_or_create(conversation_id, user_id, user_type))[0]

            token = set_current_user_id(user_id)
            try:
                history = await self._load_history(conv_id)
                knowledge = await self._retrieve_knowledge(message)
                await history.aadd_message(HumanMessage(content=message))

                # 先推 meta（前端拿到会话 ID 才能继续在同一会话追问）
                yield {
                    "event": "meta",
                    "data": json.dumps({"conversationId": conv_id}, ensure_ascii=False),
                }

                full = ""
                messages = self._build_messages(C_END_SYSTEM, knowledge, history, message)
                for _ in range(MAX_TOOL_ITERATIONS):
                    aggregated = None
                    has_tool_calls = False
                    async for chunk in self.llm_with_tools.astream(messages):
                        aggregated = chunk if aggregated is None else aggregated + chunk
                        text = _content_text(chunk.content)
                        if text:
                            full += text
                            yield {"data": text}  # 默认事件（无 event 名）
                        if getattr(chunk, "tool_call_chunks", None):
                            has_tool_calls = True

                    if aggregated is None or not has_tool_calls:
                        break

                    # 模型要调工具：执行后带结果继续下一轮
                    ai_msg = AIMessage(
                        content=aggregated.content,
                        additional_kwargs=aggregated.additional_kwargs,
                        tool_calls=aggregated.tool_calls,
                    )
                    messages.append(ai_msg)
                    for tc in aggregated.tool_calls:
                        messages.append(
                            ToolMessage(
                                content=await self._run_tool(tc),
                                tool_call_id=tc["id"],
                            )
                        )

                if full:
                    await history.aadd_message(AIMessage(content=full))
                    await conversation_service.touch_conversation(conv_id, full)
                    conversation_service.spawn_title_task(conv_id, message, self.chat_model)

                yield {"event": "done", "data": "[DONE]"}
            finally:
                reset_current_user_id(token)

        except BusinessException as e:
            logger.info("流式对话业务异常", msg=e.message)
            yield {"event": "error", "data": e.message}
        except Exception as e:
            logger.exception("流式对话异常", error=str(e))
            yield {"event": "error", "data": AI_UNAVAILABLE}

    # ===== 内部工具 =====

    def _check_enabled(self) -> None:
        if not self.settings.business.enabled:
            raise BusinessException("AI 功能未开启")

    @staticmethod
    def _validate(message: str) -> str:
        if not message or not message.strip():
            raise BusinessException("消息内容不能为空")
        if len(message) > 2000:
            raise BusinessException("消息内容过长（最多 2000 字）")
        return message.strip()

    def _history(self, conversation_id: int) -> HybridChatMessageHistory:
        return HybridChatMessageHistory(
            conversation_id=conversation_id,
            window_size=max(self.settings.business.history_rounds * 2, 4),
        )

    async def _load_history(self, conversation_id: int) -> HybridChatMessageHistory:
        """加载并预热历史，返回 history 对象本身（可 aadd_message，也可 *history 展开）。"""
        history = self._history(conversation_id)
        await history.aget_messages()
        return history

    async def _retrieve_knowledge(self, query: str) -> str:
        """RAG 检索（pgvector 余弦 top-k，异常降级关键词匹配），并按 max_chars 截断。"""
        if not self.settings.business.rag_enabled:
            return ""
        try:
            from app.services.knowledge_service import retrieve_context

            return await retrieve_context(
                query,
                self.settings.business.rag_top_k,
                self.settings.business.knowledge_max_chars,
                self.retriever,
            )
        except Exception as e:
            logger.warning("RAG 检索失败，忽略知识库上下文", error=str(e))
            return ""

    @staticmethod
    def _build_system(knowledge: str) -> str:
        if not knowledge or not knowledge.strip():
            return C_END_SYSTEM
        return C_END_SYSTEM + RAG_CONTEXT_TEMPLATE % knowledge

    def _build_messages(self, _system_unused, knowledge, history, message) -> list:
        # 保留独立函数便于非流式/流式两路共用
        return [
            SystemMessage(content=self._build_system(knowledge)),
            *history,
            HumanMessage(content=message),
        ]

    async def _invoke_with_tools(self, _system_unused, knowledge, history, message) -> str:
        """非流式工具调用循环（替代 Spring ChatClient .tools() 的自动循环）。"""
        messages = self._build_messages(_system_unused, knowledge, history, message)
        resp = None
        for _ in range(MAX_TOOL_ITERATIONS):
            resp = await self.llm_with_tools.ainvoke(messages)
            if getattr(resp, "tool_calls", None):
                messages.append(resp)
                for tc in resp.tool_calls:
                    messages.append(
                        ToolMessage(content=await self._run_tool(tc), tool_call_id=tc["id"])
                    )
                continue
            break
        return _content_text(resp.content) if resp is not None else ""

    async def _run_tool(self, tool_call: dict) -> str:
        """执行单个工具，异常兜底为"给模型看的"错误文本（不向上抛）。"""
        name = tool_call.get("name")
        args = tool_call.get("args") or {}
        tool = self.tools.get(name)
        if tool is None:
            return json.dumps({"error": f"未知工具：{name}"}, ensure_ascii=False)
        try:
            result = await tool.ainvoke(args)
            if isinstance(result, str):
                return result
            return json.dumps(result, ensure_ascii=False, default=str)
        except Exception as e:
            logger.warning("工具调用失败", tool=name, args=args, error=str(e))
            return json.dumps({"error": f"工具 {name} 调用失败：{e}"}, ensure_ascii=False)
