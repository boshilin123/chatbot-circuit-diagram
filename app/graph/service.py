from __future__ import annotations

from typing import Any

from langgraph.types import Command

from app.graph.nodes.clarify import BACK_OPTION_VALUE
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


def _has_pending_interrupt(snapshot: Any) -> bool:
    """判断当前 thread 是否仍有待处理的 interrupt。

    图一旦跑到 END，旧选项的 resume 不会重新执行节点，而是直接返回上一轮
    的最终状态（表现为"点哪个选项都是同一个答案"）。因此必须先校验。
    """

    if getattr(snapshot, "interrupts", ()):
        return True

    return any(
        getattr(task, "interrupts", ())
        for task in (getattr(snapshot, "tasks", ()) or ())
    )


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
        "filter_fallback": False,
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


def _snapshot_accepts_option(
    snapshot: Any,
    option_value: str,
) -> bool:
    """该 checkpoint 的澄清选项里是否存在用户点的这一项。"""

    values = getattr(snapshot, "values", None) or {}

    if option_value == BACK_OPTION_VALUE:
        return bool(values.get("candidate_history"))

    return option_value in (values.get("option_groups") or {})


async def _prepare_resume(
    graph: Any,
    config: dict,
    option_value: str,
) -> bool:
    """确保当前会话存在一个**干净的**、能接受该选项的 interrupt。

    为什么要统一"重建"而不是直接 resume：
    1. 图跑到 END 后再对旧 interrupt 直接 resume，LangGraph 会重放该 checkpoint
       已写入的 resume 值，新选项被静默忽略（实测连非法值都不报错）——这就是
       "点哪个选项都是同一个答案"的根因；
    2. 更隐蔽的是，一次非法的选择会让节点抛错并把那个坏值留在 checkpoint 里，
       之后即便点合法选项也会重放坏值，会话被"毒化"。

    因此这里按**选项值**找到最近一个"选项集合里含这一项"的 interrupt，统一从它
    前一个 checkpoint 用 `ainvoke(None, ...)` 重新执行澄清节点（纯计算，不重新检索），
    生成全新的 interrupt，再由调用方 resume。首次选择、同轮改选、跨轮次回头改选、
    非法选择后的恢复，全部走同一条路径。
    """

    snapshot = await graph.aget_state(config)
    pending_now = _has_pending_interrupt(snapshot)

    candidates: list[Any] = []
    if pending_now and _snapshot_accepts_option(snapshot, option_value):
        candidates.append(snapshot)

    async for past in graph.aget_state_history(config):
        if not _has_pending_interrupt(past):
            continue

        if not _snapshot_accepts_option(past, option_value):
            continue

        candidates.append(past)

    for candidate in candidates:
        parent_config = getattr(candidate, "parent_config", None)
        if parent_config is None:
            continue

        await graph.ainvoke(None, config=parent_config)
        return True

    # 有待处理 interrupt 但选项不属于任何一轮（例如非法值）：不重建，
    # 交给 Graph 抛出"无效的选择项"这类精确错误
    return pending_now


async def resume_search_workflow(
    session_id: str,
    option_value: str,
) -> GraphResponse:
    """使用 Command(resume=...) 从 interrupt 处恢复 Graph。"""

    graph = get_circuit_graph()
    config = _config(session_id)

    # 1. 准备能接受该选项的 interrupt：首次选择直接用，改选（含跨轮次）则重新生成
    if not await _prepare_resume(graph, config, option_value):
        raise ValueError(
            "当前会话没有可用的选择项（可能已完成，或服务重启导致会话状态丢失），"
            "请重新输入查询"
        )

    # 2. 恢复 Graph
    result = await graph.ainvoke(
        Command(resume=option_value),
        config=config,
    )

    return _build_response(result)
