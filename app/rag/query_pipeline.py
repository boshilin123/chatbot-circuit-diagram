from __future__ import annotations

from typing import Any

from app.rag.filters import build_metadata_filter
from app.rag.intent_parser import parse_search_intent
from app.rag.query_rewriter import rewrite_query
from app.schemas.query import SearchQueryPlan


def prepare_search_query(
    query: str,
    *,
    model: Any | None = None,
) -> SearchQueryPlan:
    """在进入 Retriever 前完成意图解析、Query Rewrite 与 Filter 构建。"""

    query = query.strip()
    if not query:
        return SearchQueryPlan(
            original_query="",
            rewritten_query="",
            intent=parse_search_intent(""),
            filter_query=None,
        )

    # 1. Structured Output 提取结构化意图
    intent = parse_search_intent(
        query,
        model=model,
    )

    # 2. 按课程 Query Rewrite 示例生成检索 Query
    rewritten_query = rewrite_query(
        query,
        model=model,
    )

    # 3. 将高置信度意图转换为 Metadata Filter
    filter_query = build_metadata_filter(intent)

    return SearchQueryPlan(
        original_query=query,
        rewritten_query=rewritten_query,
        intent=intent,
        filter_query=filter_query,
    )
