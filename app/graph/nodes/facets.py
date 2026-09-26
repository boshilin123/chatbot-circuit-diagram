from __future__ import annotations

import re
from collections import defaultdict
from math import ceil
from typing import Any

from app.core.config import get_settings
from app.graph.nodes.clarify import BACK_OPTION_VALUE
from app.graph.state import CircuitSearchState

# 1. 路径层级 → 维度名称。
# 不同数据分支的层级语义并不统一（"工程机械->三一->德国仪表" 与 "东风->天龙*系列->天龙*通用"
# 的第 2 层含义不同），因此只对稳定的前两层命名，其余使用通用名称，避免误导用户。
LEVEL_FACET_LABELS = {
    0: "资料大类",
    1: "资料类型",
}
GENERIC_FACET_LABEL = "更具体的分类"

# 2. 资料类型维度：标题里的"资料类型"往往比层级路径更能区分候选
DOCUMENT_TYPE_FACET = "document_type"
DOCUMENT_TYPE_LABEL = "资料类型"

# 长词优先，避免"整车电路图"被"电路图"提前截断
DOCUMENT_TYPE_KEYWORDS = (
    "整车电路图",
    "系统原理图",
    "原理图",
    "电路图",
    "线束图",
    "接线图",
    "针脚定义",
    "针脚",
    "端子定义",
    "维修手册",
    "使用说明",
    "故障码",
    "诊断",
)

UNCLASSIFIED_GROUP_LABEL = "未分类"
OTHER_GROUP_LABEL = "其他"

# 所有维度都失效时按排名切组，key 前缀固定、轮次后缀在 build_facets 中拼接
RANK_FALLBACK_FACET = "rank_group"

TITLE_EXTENSION_PATTERN = re.compile(r"\.(docx?|pdf|xlsx?|pptx?)$", re.IGNORECASE)
TITLE_BRACKET_PATTERN = re.compile(r"【[^】]*】")


def _path_segments(doc: dict[str, Any]) -> list[str]:
    return [
        segment.strip()
        for segment in str(doc["hierarchy_path"]).split("->")
        if segment.strip()
    ]


def _document_type(doc: dict[str, Any]) -> str | None:
    """从标题 / 层级路径中识别资料类型，识别不到返回 None。"""

    text = f"{doc.get('title', '')} {doc.get('hierarchy_path', '')}"

    for keyword in DOCUMENT_TYPE_KEYWORDS:
        if keyword in text:
            return keyword

    return None


def _short_title(doc: dict[str, Any], limit: int = 18) -> str:
    """取标题的短名称，用于兜底分组时给用户一个可辨认的标签。"""

    title = TITLE_EXTENSION_PATTERN.sub("", str(doc.get("title", "")))
    title = TITLE_BRACKET_PATTERN.sub("", title).strip("_ ")

    if not title:
        return f"ID {doc.get('doc_id')}"

    return title if len(title) <= limit else f"{title[:limit]}…"


def _group_by_path_level(
    docs: list[dict[str, Any]],
    level: int,
) -> dict[str, list[int]]:
    """按层级路径的第 level 段分组，缺少该段的资料归入"未分类"。"""

    groups: dict[str, list[int]] = defaultdict(list)

    for doc in docs:
        segments = _path_segments(doc)
        key = (
            segments[level]
            if level < len(segments)
            else UNCLASSIFIED_GROUP_LABEL
        )
        groups[key].append(int(doc["doc_id"]))

    return dict(groups)


def _group_by_document_type(
    docs: list[dict[str, Any]],
) -> dict[str, list[int]]:
    """按标题中识别出的资料类型分组。"""

    groups: dict[str, list[int]] = defaultdict(list)

    for doc in docs:
        groups[_document_type(doc) or UNCLASSIFIED_GROUP_LABEL].append(
            int(doc["doc_id"])
        )

    return dict(groups)


def _limit_groups(
    groups: dict[str, list[int]],
    max_options: int,
) -> list[tuple[str, list[int]]]:
    """维度分组过多时保留主要分类，其余合并为"其他"。"""

    sorted_groups = sorted(
        groups.items(),
        key=lambda item: (-len(item[1]), item[0]),
    )

    if len(sorted_groups) <= max_options:
        return sorted_groups

    keep_count = max_options - 1
    kept = sorted_groups[:keep_count]
    other_ids = [
        doc_id
        for _, doc_ids in sorted_groups[keep_count:]
        for doc_id in doc_ids
    ]

    return [*kept, (OTHER_GROUP_LABEL, other_ids)]


def _fallback_rank_groups(
    docs: list[dict[str, Any]],
    max_options: int,
) -> list[tuple[str, list[int]]]:
    """所有维度都无法有效区分时按排名分组，并用组内首条标题作为标签。"""

    group_count = min(
        max_options,
        max(2, ceil(len(docs) / 5)),
    )
    chunk_size = ceil(len(docs) / group_count)

    groups: list[tuple[str, list[int]]] = []
    for index in range(group_count):
        chunk = docs[index * chunk_size: (index + 1) * chunk_size]
        if not chunk:
            continue

        groups.append(
            (
                _short_title(chunk[0]),
                [int(doc["doc_id"]) for doc in chunk],
            )
        )

    return groups


def _is_usable_dimension(
    groups: dict[str, list[int]],
    doc_count: int,
    max_options: int,
    *,
    allow_overflow: bool,
) -> bool:
    """维度必须真的把候选拆开，且选项数量落在可用区间。"""

    if doc_count <= 1 or len(groups) < 2:
        return False

    if allow_overflow:
        return len(groups) > max_options

    return len(groups) <= max_options


def _select_dimension(
    docs: list[dict[str, Any]],
    max_depth: int,
    used_facets: set[str],
    max_options: int,
) -> tuple[str, str, list[tuple[str, list[int]]]]:
    """挑选最能区分候选的维度。

    优先级：
    1. 选项数天然落在 2～max_options 的维度，取分组数最多者；
       （分组数相同时优先"资料类型"，因为它最贴近用户挑选资料的习惯）
    2. 分组过多但可以合并为"其他"的维度；
    3. 全部失败时按排名兜底。
    """

    dimensions: list[tuple[str, str, dict[str, list[int]]]] = []

    if DOCUMENT_TYPE_FACET not in used_facets:
        dimensions.append(
            (
                DOCUMENT_TYPE_FACET,
                DOCUMENT_TYPE_LABEL,
                _group_by_document_type(docs),
            )
        )

    for level in range(max_depth):
        facet_key = f"path_level_{level}"
        if facet_key in used_facets:
            continue

        dimensions.append(
            (
                facet_key,
                LEVEL_FACET_LABELS.get(level, GENERIC_FACET_LABEL),
                _group_by_path_level(docs, level),
            )
        )

    def rank(dimension: tuple[str, str, dict[str, list[int]]]) -> tuple[int, int]:
        facet_key, _, groups = dimension
        return (len(groups), 1 if facet_key == DOCUMENT_TYPE_FACET else 0)

    def balance_rank(
        dimension: tuple[str, str, dict[str, list[int]]],
    ) -> tuple[int, int, int]:
        """分组过多需要合并时，优先让"其他"这个桶尽量小。"""

        facet_key, _, groups = dimension
        merged = _limit_groups(groups, max_options)

        return (
            max(len(doc_ids) for _, doc_ids in merged),
            -len(groups),
            0 if facet_key == DOCUMENT_TYPE_FACET else 1,
        )

    clean = [
        dimension
        for dimension in dimensions
        if _is_usable_dimension(
            dimension[2],
            len(docs),
            max_options,
            allow_overflow=False,
        )
    ]
    if clean:
        facet_key, label, groups = max(clean, key=rank)
        return facet_key, label, _limit_groups(groups, max_options)

    overflow = [
        dimension
        for dimension in dimensions
        if _is_usable_dimension(
            dimension[2],
            len(docs),
            max_options,
            allow_overflow=True,
        )
    ]
    if overflow:
        facet_key, label, groups = min(overflow, key=balance_rank)
        return facet_key, label, _limit_groups(groups, max_options)

    return (
        RANK_FALLBACK_FACET,
        "",
        _fallback_rank_groups(docs, max_options),
    )


def _build_prompt(
    doc_count: int,
    facet_key: str,
    label: str,
) -> str:
    if facet_key == DOCUMENT_TYPE_FACET:
        return f"找到 {doc_count} 条相关资料，请选择需要的资料类型："

    if facet_key.startswith(RANK_FALLBACK_FACET):
        return f"找到 {doc_count} 条相关资料，请选择更接近你需求的一组："

    return f"找到 {doc_count} 条相关资料，请按「{label}」继续选择："


def build_facets(state: CircuitSearchState) -> dict:
    """从当前候选集合中生成可解释的澄清选项。"""

    settings = get_settings()
    docs = state["candidate_documents"]
    used_facets = set(state.get("used_facets", []))
    history = state.get("candidate_history", [])

    # 返回上一步也占一个选项位，确保单轮总选项不超过 5
    include_back = bool(history)
    facet_slots = settings.graph_max_options - (1 if include_back else 0)

    max_depth = (
        max(len(_path_segments(doc)) for doc in docs)
        if docs
        else 0
    )

    facet_key, facet_label, chosen_groups = _select_dimension(
        docs,
        max_depth,
        used_facets,
        facet_slots,
    )

    # 兜底分组每一轮都要换 key，否则会被 used_facets 误判为"已问过"
    if facet_key == RANK_FALLBACK_FACET:
        facet_key = (
            f"{RANK_FALLBACK_FACET}_"
            f"{state.get('clarification_round', 0)}"
        )

    # 1. 构造旧前端兼容的 id / text / value 选项
    options = []
    option_groups: dict[str, list[int]] = {}

    for index, (label, doc_ids) in enumerate(chosen_groups, start=1):
        value = f"{facet_key}:{index}"
        options.append(
            {
                "id": index,
                "text": f"{label}（{len(doc_ids)}条）",
                "value": value,
            }
        )
        option_groups[value] = doc_ids

    # 2. 第二轮起提供"返回上一步"
    if include_back:
        options.append(
            {
                "id": len(options) + 1,
                "text": "返回上一步",
                "value": BACK_OPTION_VALUE,
            }
        )

    return {
        "current_facet": facet_key,
        "current_prompt": _build_prompt(
            len(docs),
            facet_key,
            facet_label,
        ),
        "current_options": options,
        "option_groups": option_groups,
        "status": "clarifying",
    }
