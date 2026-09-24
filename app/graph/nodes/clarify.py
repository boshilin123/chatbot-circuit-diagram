from __future__ import annotations

from copy import deepcopy
from typing import Any

from langgraph.types import interrupt

from app.graph.state import CircuitSearchState


def apply_selection(
    state: CircuitSearchState,
    option_value: str,
) -> dict:
    """应用用户选择，缩小候选集合或返回上一步。"""

    history = deepcopy(state.get("candidate_history", []))

    # 1. 返回上一步：恢复选择前的候选集合和 Facet 状态
    if option_value == "__back__":
        if not history:
            raise ValueError("当前没有可以返回的上一步")

        snapshot = history.pop()

        return {
            "candidate_documents": snapshot["candidate_documents"],
            "candidate_ids": snapshot["candidate_ids"],
            "selected_filters": snapshot["selected_filters"],
            "used_facets": snapshot["used_facets"],
            "clarification_round": max(
                state.get("clarification_round", 1) - 1,
                0,
            ),
            "candidate_history": history,
            "current_facet": None,
            "current_prompt": None,
            "current_options": [],
            "option_groups": {},
            "status": "back",
        }

    # 2. 校验用户选择必须来自当前真实选项
    option_groups = state.get("option_groups", {})
    selected_ids = option_groups.get(option_value)

    if not selected_ids:
        raise ValueError("无效的选择项，请重新选择当前选项")

    selected_id_set = set(selected_ids)
    current_docs = state["candidate_documents"]
    selected_docs = [
        doc
        for doc in current_docs
        if int(doc["doc_id"]) in selected_id_set
    ]

    # 3. 保存当前快照，以支持“返回上一步”
    history.append(
        {
            "candidate_documents": deepcopy(current_docs),
            "candidate_ids": list(state.get("candidate_ids", [])),
            "selected_filters": deepcopy(state.get("selected_filters", {})),
            "used_facets": list(state.get("used_facets", [])),
        }
    )

    # 4. 记录已使用 Facet，避免下一轮重复询问同一维度
    current_facet = state.get("current_facet")
    used_facets = list(state.get("used_facets", []))
    if current_facet and current_facet not in used_facets:
        used_facets.append(current_facet)

    selected_filters = dict(state.get("selected_filters", {}))
    selected_option = next(
        (
            option
            for option in state.get("current_options", [])
            if option["value"] == option_value
        ),
        None,
    )
    if current_facet and selected_option:
        selected_filters[current_facet] = selected_option["text"]

    return {
        "candidate_documents": selected_docs,
        "candidate_ids": [
            int(doc["doc_id"])
            for doc in selected_docs
        ],
        "selected_filters": selected_filters,
        "used_facets": used_facets,
        "clarification_round": state.get("clarification_round", 0) + 1,
        "candidate_history": history,
        "current_facet": None,
        "current_prompt": None,
        "current_options": [],
        "option_groups": {},
        "status": "filtered",
    }


def clarify(state: CircuitSearchState) -> dict:
    """暂停 Graph，等待用户从 3～5 个选项中选择。"""

    # 1. interrupt 将当前选择题返回给前端，并由 Checkpointer 保存 State
    selection: Any = interrupt(
        {
            "type": "options",
            "content": state["current_prompt"],
            "options": state["current_options"],
        }
    )

    # 2. /api/select 使用 Command(resume=optionValue) 恢复 Graph
    if isinstance(selection, dict):
        option_value = str(
            selection.get("optionValue")
            or selection.get("value")
            or ""
        )
    else:
        option_value = str(selection)

    # 3. 应用用户选择
    return apply_selection(
        state,
        option_value,
    )
