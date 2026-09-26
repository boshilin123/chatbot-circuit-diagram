from __future__ import annotations

from typing import Any, TypedDict


class CircuitSearchState(TypedDict, total=False):
    """车辆电路图多轮检索的共享 State。"""

    original_query: str
    intent: dict[str, Any]
    rewritten_query: str
    filter_query: str | None
    filter_fallback: bool

    candidate_ids: list[int]
    candidate_documents: list[dict[str, Any]]

    selected_filters: dict[str, str]
    used_facets: list[str]
    clarification_round: int

    current_facet: str | None
    current_prompt: str | None
    current_options: list[dict[str, Any]]
    option_groups: dict[str, list[int]]

    candidate_history: list[dict[str, Any]]

    final_results: list[dict[str, Any]]
    final_text: str
    status: str
