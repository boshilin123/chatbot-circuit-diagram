from __future__ import annotations

from typing import Literal

from pymilvus import RRFRanker

from app.core.config import get_settings
from app.graph.state import CircuitSearchState
from app.rag.hybrid_search import hybrid_search
from app.rag.reranker import cross_encoder_rerank


def retrieve(state: CircuitSearchState) -> dict:
    """Dense + BM25 + RRF + Cross-Encoder 召回候选集。"""

    settings = get_settings()
    query = state["rewritten_query"]

    # 1. Dense + BM25 + RRF 召回较大的候选集合
    ranker = RRFRanker(k=settings.rrf_k)
    docs = hybrid_search(
        query,
        ranker=ranker,
        top_k=settings.graph_candidate_k,
        candidate_k=settings.graph_candidate_k,
        filter_query=state.get("filter_query"),
    )

    # 2. Cross-Encoder 精排，但暂时保留足够候选供 LangGraph 澄清
    docs = cross_encoder_rerank(
        query,
        docs,
        top_k=settings.graph_candidate_k,
        min_score=settings.reranker_min_score,
    )

    candidate_documents = [
        doc.model_dump()
        for doc in docs
    ]

    return {
        "candidate_documents": candidate_documents,
        "candidate_ids": [
            int(doc["doc_id"])
            for doc in candidate_documents
        ],
        "selected_filters": {},
        "used_facets": [],
        "clarification_round": 0,
        "current_facet": None,
        "current_prompt": None,
        "current_options": [],
        "option_groups": {},
        "candidate_history": [],
        "status": "retrieved",
    }


def route_by_result_count(
    state: CircuitSearchState,
) -> Literal["no_results", "final_answer", "build_facets"]:
    """硬编码业务规则：0、<=5、>5 三路分支。"""

    settings = get_settings()
    count = len(state.get("candidate_documents", []))

    if count == 0:
        return "no_results"

    if count <= settings.graph_max_results:
        return "final_answer"

    return "build_facets"
