from __future__ import annotations

from collections.abc import Sequence

from app.schemas.search import SearchResult


def reciprocal_rank_fusion(
    ranked_lists: Sequence[Sequence[SearchResult]],
    *,
    k: int = 60,
) -> list[SearchResult]:
    """使用 RRF 融合多个按相关性排序的检索结果列表。

    RRF 只关心文档排名，不直接使用 Dense / BM25 原始分数。
    """

    if k <= 0:
        raise ValueError("k 必须大于 0")

    rrf_scores: dict[int, float] = {}
    results: dict[int, SearchResult] = {}

    # 1. 累加每个文档在每个结果列表中的 RRF 分数
    for ranked_list in ranked_lists:
        for rank, doc in enumerate(ranked_list, start=1):
            rrf_scores[doc.doc_id] = (
                rrf_scores.get(doc.doc_id, 0.0)
                + 1.0 / (k + rank)
            )
            results[doc.doc_id] = doc

    # 2. 按 RRF 总分降序排序
    sorted_docs = sorted(
        rrf_scores.items(),
        key=lambda item: item[1],
        reverse=True,
    )

    # 3. 返回融合后的文档列表
    return [
        results[doc_id].model_copy(
            update={"score": score},
        )
        for doc_id, score in sorted_docs
    ]
