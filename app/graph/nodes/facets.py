from __future__ import annotations

from collections import defaultdict
from math import ceil
from typing import Any

from app.core.config import get_settings
from app.graph.state import CircuitSearchState


FACET_LABELS = {
    0: "资料大类",
    1: "电路图类型",
    2: "应用场景",
    3: "品牌",
    4: "车型或系列",
}


def _path_segments(doc: dict[str, Any]) -> list[str]:
    return [
        segment.strip()
        for segment in str(doc["hierarchy_path"]).split("->")
        if segment.strip()
    ]


def _group_by_path_level(
    docs: list[dict[str, Any]],
    level: int,
) -> dict[str, list[int]]:
    groups: dict[str, list[int]] = defaultdict(list)

    for doc in docs:
        segments = _path_segments(doc)
        if level >= len(segments):
            continue

        groups[segments[level]].append(int(doc["doc_id"]))

    return dict(groups)


def _limit_groups(
    groups: dict[str, list[int]],
    max_options: int,
) -> list[tuple[str, list[int]]]:
    sorted_groups = sorted(
        groups.items(),
        key=lambda item: (-len(item[1]), item[0]),
    )

    if len(sorted_groups) <= max_options:
        return sorted_groups

    # 保留主要分类，其余合并为“其他”
    keep_count = max_options - 1
    kept = sorted_groups[:keep_count]
    other_ids = [
        doc_id
        for _, doc_ids in sorted_groups[keep_count:]
        for doc_id in doc_ids
    ]

    return [
        *kept,
        ("其他", other_ids),
    ]


def _fallback_rank_groups(
    docs: list[dict[str, Any]],
    max_options: int,
) -> list[tuple[str, list[int]]]:
    """层级路径无法形成有效 Facet 时按排名分组兜底。"""

    group_count = min(
        max_options,
        max(3, ceil(len(docs) / 5)),
    )
    chunk_size = ceil(len(docs) / group_count)

    groups = []
    for index in range(group_count):
        chunk = docs[
            index * chunk_size:
            (index + 1) * chunk_size
        ]
        if not chunk:
            continue

        groups.append(
            (
                f"相关资料第 {index + 1} 组",
                [int(doc["doc_id"]) for doc in chunk],
            )
        )

    return groups


def build_facets(state: CircuitSearchState) -> dict:
    """从当前候选集合中生成 3～5 个真实分类选项。"""

    settings = get_settings()
    docs = state["candidate_documents"]
    used_facets = set(state.get("used_facets", []))
    history = state.get("candidate_history", [])

    # 返回上一步也占一个选项位，确保单轮总选项不超过 5
    include_back = bool(history)
    facet_slots = settings.graph_max_options - (1 if include_back else 0)
    min_groups = 2 if include_back else 3

    chosen_facet = None
    chosen_label = None
    chosen_groups = None

    # 1. 优先从层级路径中寻找真实发生分叉的维度
    max_depth = max(
        len(_path_segments(doc))
        for doc in docs
    )

    for level in range(max_depth):
        facet_key = f"path_level_{level}"
        if facet_key in used_facets:
            continue

        groups = _group_by_path_level(docs, level)
        if len(groups) < min_groups:
            continue

        chosen_facet = facet_key
        chosen_label = FACET_LABELS.get(level, "更具体的分类")
        chosen_groups = _limit_groups(
            groups,
            facet_slots,
        )
        break

    # 2. 层级路径无法继续区分时，用相关性排名分组兜底
    if chosen_groups is None:
        chosen_facet = f"rank_group_{state.get('clarification_round', 0)}"
        chosen_label = "相关资料分组"
        chosen_groups = _fallback_rank_groups(
            docs,
            facet_slots,
        )

    # 3. 构造旧前端兼容的 id / text / value 选项
    options = []
    option_groups: dict[str, list[int]] = {}

    for index, (label, doc_ids) in enumerate(chosen_groups, start=1):
        value = f"{chosen_facet}:{index}"
        options.append(
            {
                "id": index,
                "text": f"{label}（{len(doc_ids)}条）",
                "value": value,
            }
        )
        option_groups[value] = doc_ids

    if include_back:
        options.append(
            {
                "id": len(options) + 1,
                "text": "返回上一步",
                "value": "__back__",
            }
        )

    prompt = (
        f"找到 {len(docs)} 条相关资料，"
        f"请按“{chosen_label}”继续选择："
    )

    return {
        "current_facet": chosen_facet,
        "current_prompt": prompt,
        "current_options": options,
        "option_groups": option_groups,
        "status": "clarifying",
    }
