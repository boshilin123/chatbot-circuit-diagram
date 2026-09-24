from __future__ import annotations

from typing import Literal

from app.graph.state import CircuitSearchState
from app.rag.intent_parser import parse_search_intent


def understand_query(state: CircuitSearchState) -> dict:
    """使用 Structured Output 理解用户查询。"""

    # 1. 提取结构化检索意图
    intent = parse_search_intent(state["original_query"])

    # 2. 写入 LangGraph State
    return {
        "intent": intent.model_dump(),
        "status": "understood",
    }


def route_query(
    state: CircuitSearchState,
) -> Literal["chat", "rewrite_query"]:
    """普通对话与资料检索分流。"""

    if state.get("intent", {}).get("is_search_request", True):
        return "rewrite_query"

    return "chat"
