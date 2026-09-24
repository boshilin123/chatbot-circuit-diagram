from __future__ import annotations

from functools import partial

from pymilvus import RRFRanker

from app.core.config import get_settings
from app.rag.hybrid_search import hybrid_search, sparse_search
from app.rag.pipeline import retrieve_docs
from app.rag.retriever import dense_search
from eval.runners import Retriever


def build_retrievers() -> dict[str, Retriever]:
    """构建 Phase 9 需要比较的四组 Retriever。"""

    settings = get_settings()

    # 1. Dense
    dense: Retriever = lambda query, top_k: dense_search(
        query,
        top_k=top_k,
    )

    # 2. BM25
    bm25: Retriever = lambda query, top_k: sparse_search(
        query,
        top_k=top_k,
    )

    # 3. Dense + BM25 + RRF
    def hybrid(query: str, top_k: int):
        return hybrid_search(
            query,
            ranker=RRFRanker(k=settings.rrf_k),
            top_k=top_k,
            candidate_k=max(
                settings.hybrid_candidate_k,
                top_k,
            ),
        )

    # 4. Hybrid + Cross-Encoder Rerank
    rerank: Retriever = lambda query, top_k: retrieve_docs(
        query,
        top_k=top_k,
        candidate_k=max(
            settings.hybrid_candidate_k,
            top_k,
        ),
    )

    return {
        "dense": dense,
        "bm25": bm25,
        "hybrid_rrf": hybrid,
        "hybrid_rerank": rerank,
    }
