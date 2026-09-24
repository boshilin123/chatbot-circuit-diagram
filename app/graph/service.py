from __future__ import annotations

from typing import Any

from langgraph.types import Command

from app.graph.workflow import get_circuit_graph
from app.schemas.graph import GraphResponse


def _config(session_id: str) -> dict:
    """thread_id 是 LangGraph Checkpointer 的会话键。"""

    return {
        "configurable": {
            "thread_id": session_id,
        }
    }


def _interrupt_value(result: dict[str, Any]) -> dict[str, Any] | None:
    interrupts = result.get("__interrupt__")
    if not interrupts:
        return None

    first = interrupts[0]
    value = getattr(first, "value", first)

    if isinstance(value, dict):
        return value

    return None


def _frontend_document(doc: dict[str, Any]) -> dict[str, Any]:
    """Graph 内部 snake_case -> 旧前端字段格式。"""

    return {
        "id": int(doc["doc_id"]),
        "fileName": str(doc["title"]),
        "hierarchyPath": str(doc["hierarchy_path"]),
        "keywords": [],
        "score": float(doc.get("score", 0.0)),
    }


def _build_response(result: dict[str, Any]) -> GraphResponse:
    # 1. Graph interrupt -> 前端选择题
    interrupt_value = _interrupt_value(result)
    if interrupt_value is not None:
        return GraphResponse(
            type="options",
            content=str(interrupt_value["content"]),
            options=list(interrupt_value["options"]),
        )

    # 2. 普通问候 / 无结果 -> 文本
    status = result.get("status")
    if status in {"text", "no_results"}:
        return GraphResponse(
            type="text",
            content=str(result.get("final_text", "")),
        )

    # 3. 最终资料结果 -> 1～5 个文档
    final_results = result.get("final_results", [])
    documents = [
        _frontend_document(doc)
        for doc in final_results
    ]

    return GraphResponse(
        type="result",
        content=str(result.get("final_text", "")),
        documents=documents,
    )


async def run_search_workflow(
    session_id: str,
    message: str,
) -> GraphResponse:
    """从 START 开始执行一次新的用户 Query。"""

    graph = get_circuit_graph()

    # 新问题显式重置上一轮候选状态；thread_id 仍保持同一浏览器会话
    initial_state = {
        "original_query": message.strip(),
        "intent": {},
        "rewritten_query": "",
        "filter_query": None,
        "candidate_ids": [],
        "candidate_documents": [],
        "selected_filters": {},
        "used_facets": [],
        "clarification_round": 0,
        "current_facet": None,
        "current_prompt": None,
        "current_options": [],
        "option_groups": {},
        "candidate_history": [],
        "final_results": [],
        "final_text": "",
        "status": "started",
    }

    result = await graph.ainvoke(
        initial_state,
        config=_config(session_id),
    )

    return _build_response(result)


async def resume_search_workflow(
    session_id: str,
    option_value: str,
) -> GraphResponse:
    """使用 Command(resume=...) 从 interrupt 处恢复 Graph。"""

    graph = get_circuit_graph()

    result = await graph.ainvoke(
        Command(resume=option_value),
        config=_config(session_id),
    )

    return _build_response(result)
