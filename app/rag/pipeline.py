from __future__ import annotations

from pymilvus import RRFRanker

from app.core.config import get_settings
from app.rag.hybrid_search import hybrid_search
from app.rag.reranker import cross_encoder_rerank
from app.schemas.search import RerankResult


def retrieve_docs(
    query: str,
    *,
    top_k: int | None = None,
    candidate_k: int | None = None,
    filter_query: str | None = None,
) -> list[RerankResult]:
    """混合检索：Dense + BM25 + RRF + Cross-Encoder。"""

    query = query.strip()
    if not query:
        return []

    settings = get_settings()
    top_k = top_k or settings.reranker_top_k
    candidate_k = candidate_k or settings.hybrid_candidate_k

    # 1. 定义 RRF 融合策略
    ranker = RRFRanker(k=settings.rrf_k)

    # 2. Dense + BM25 + RRF 多路召回
    docs = hybrid_search(
        query,
        ranker=ranker,
        top_k=candidate_k,
        candidate_k=candidate_k,
        filter_query=filter_query,
    )

    # 3. Cross-Encoder 精排
    final_docs = cross_encoder_rerank(
        query,
        docs,
        top_k=top_k,
    )

    return final_docs
