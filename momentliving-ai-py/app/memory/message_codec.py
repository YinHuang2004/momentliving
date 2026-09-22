"""消息 <-> dict 序列化（LangChain 标准格式，兼容 RedisChatMessageHistory 存储格式）。"""
import json
from typing import List

from langchain_core.messages import (
    AIMessage,
    BaseMessage,
    HumanMessage,
    SystemMessage,
    ToolMessage,
)

_TYPE_TO_CLASS = {
    "human": HumanMessage,
    "ai": AIMessage,
    "system": SystemMessage,
    "tool": ToolMessage,
}
_CLASS_TO_TYPE = {cls: t for t, cls in _TYPE_TO_CLASS.items()}


def message_to_dict(msg: BaseMessage) -> dict:
    return {
        "type": _CLASS_TO_TYPE[type(msg)],
        "data": {
            "content": msg.content,
            "additional_kwargs": msg.additional_kwargs,
        },
    }


def dict_to_message(d: dict) -> BaseMessage:
    cls = _TYPE_TO_CLASS[d["type"]]
    data = dict(d.get("data") or {})
    content = data.pop("content", "")
    kwargs = data.get("additional_kwargs") or {}
    return cls(content=content, **kwargs)


def serialize_messages(messages: List[BaseMessage]) -> str:
    return json.dumps([message_to_dict(m) for m in messages], ensure_ascii=False)


def deserialize_messages(s: str) -> List[BaseMessage]:
    if not s:
        return []
    try:
        return [dict_to_message(d) for d in json.loads(s)]
    except Exception:
        return []
