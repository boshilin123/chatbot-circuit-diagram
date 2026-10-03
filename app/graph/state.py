# 工作流共享状态：每个节点读取所需字段，并返回局部更新交给 LangGraph 合并。

from __future__ import annotations

from typing import Any, TypedDict


class CircuitSearchState(TypedDict, total=False):
    """车辆电路图多轮检索的共享 State。"""

    # 查询阶段：原问题 → 结构化意图 → 改写文本与过滤；filter_fallback 记录是否放宽过滤。
    original_query: str
    intent: dict[str, Any]
    rewritten_query: str
    filter_query: str | None
    filter_fallback: bool

    # 候选阶段：文档列表保留精排顺序，ID 列表用于选项映射和历史状态恢复。
    candidate_ids: list[int]
    candidate_documents: list[dict[str, Any]]

    # 澄清轨迹：保存已选标签、已用维度和轮数，避免重复询问同一个分类条件。
    selected_filters: dict[str, str]
    used_facets: list[str]
    clarification_round: int

    # 本轮选择题：current_options 给前端展示，option_groups 将机器选项值映射到真实 ID。
    current_facet: str | None
    current_prompt: str | None
    current_options: list[dict[str, Any]]
    option_groups: dict[str, list[int]]

    # 业务回退栈：保存选择前候选，与 LangGraph 自己的 checkpoint 历史是两个层次。
    candidate_history: list[dict[str, Any]]

    # 终止输出：service 根据中断或 status 转为 GraphResponse，前端不会看到完整内部状态。
    final_results: list[dict[str, Any]]
    final_text: str
    status: str
