from __future__ import annotations

from app.graph.state import CircuitSearchState
from app.rag.filters import build_metadata_filter
from app.rag.query_rewriter import rewrite_query
from app.schemas.intent import SearchIntent


def rewrite_query_node(state: CircuitSearchState) -> dict:
    """改写检索 Query，并构建 Metadata Filter。"""

    intent = SearchIntent.model_validate(state["intent"])

    # 1. Query Rewrite
    rewritten_query = rewrite_query(state["original_query"])

    # 2. Structured Intent -> Milvus Filter
    filter_query = build_metadata_filter(intent)

    return {
        "rewritten_query": rewritten_query,
        "filter_query": filter_query,
        "status": "rewritten",
    }
