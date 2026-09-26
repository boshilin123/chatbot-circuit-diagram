from types import SimpleNamespace

from app.graph.nodes import retrieve as retrieve_node
from app.schemas.search import HybridSearchResult, RerankResult


def test_retrieve_retries_without_filter_after_filtered_zero_hit(
    monkeypatch,
) -> None:
    """硬过滤零命中时应回退无过滤检索，避免同义表达导致假阴性。"""

    filter_query = 'title LIKE "%线路图%"'
    retrieved = HybridSearchResult(
        doc_id=8,
        title="徐工_XE135GII挖掘机_仪表显示器针脚定义",
        hierarchy_path="电路图->ECU电路图->工程机械->徐工->XE135GII",
        content="徐工 XE135GII 仪表显示器针脚定义",
        score=0.5,
    )
    reranked = RerankResult(
        **retrieved.model_dump(exclude={"score"}),
        retrieval_score=retrieved.score,
        score=0.9,
    )
    search_filters: list[str | None] = []

    def fake_hybrid_search(query, **kwargs):
        assert query == "徐工仪表线路图"
        search_filters.append(kwargs["filter_query"])
        return [] if kwargs["filter_query"] else [retrieved]

    monkeypatch.setattr(
        retrieve_node,
        "get_settings",
        lambda: SimpleNamespace(
            rrf_k=60,
            graph_candidate_k=30,
            reranker_min_score=0.0,
        ),
    )
    monkeypatch.setattr(retrieve_node, "hybrid_search", fake_hybrid_search)
    monkeypatch.setattr(
        retrieve_node,
        "cross_encoder_rerank",
        lambda query, docs, **kwargs: [reranked] if docs else [],
    )

    result = retrieve_node.retrieve(
        {
            "rewritten_query": "徐工仪表线路图",
            "filter_query": filter_query,
        }
    )

    assert search_filters == [filter_query, None]
    assert result["filter_fallback"] is True
    assert result["filter_query"] is None
    assert result["candidate_ids"] == [8]


def test_retrieve_retries_without_filter_after_filtered_rerank_zero_hit(
    monkeypatch,
) -> None:
    """过滤召回非空但精排全淘汰时，仍应回退无过滤检索。"""

    filter_query = 'title LIKE "%线路图%"'
    filtered_doc = HybridSearchResult(
        doc_id=999,
        title="弱相关的线路图",
        hierarchy_path="电路图->其他",
        content="弱相关候选",
        score=0.1,
    )
    fallback_doc = HybridSearchResult(
        doc_id=8,
        title="徐工_XE135GII挖掘机_仪表显示器针脚定义",
        hierarchy_path="电路图->ECU电路图->工程机械->徐工->XE135GII",
        content="徐工 XE135GII 仪表显示器针脚定义",
        score=0.5,
    )
    reranked = RerankResult(
        **fallback_doc.model_dump(exclude={"score"}),
        retrieval_score=fallback_doc.score,
        score=0.9,
    )
    search_filters: list[str | None] = []
    rerank_ids: list[list[int]] = []

    def fake_hybrid_search(query, **kwargs):
        search_filters.append(kwargs["filter_query"])
        return [filtered_doc] if kwargs["filter_query"] else [fallback_doc]

    def fake_rerank(query, docs, **kwargs):
        ids = [doc.doc_id for doc in docs]
        rerank_ids.append(ids)
        return [reranked] if ids == [8] else []

    monkeypatch.setattr(
        retrieve_node,
        "get_settings",
        lambda: SimpleNamespace(
            rrf_k=60,
            graph_candidate_k=30,
            reranker_min_score=0.5,
        ),
    )
    monkeypatch.setattr(retrieve_node, "hybrid_search", fake_hybrid_search)
    monkeypatch.setattr(retrieve_node, "cross_encoder_rerank", fake_rerank)

    result = retrieve_node.retrieve(
        {
            "rewritten_query": "徐工仪表线路图",
            "filter_query": filter_query,
        }
    )

    assert search_filters == [filter_query, None]
    assert rerank_ids == [[999], [8]]
    assert result["filter_fallback"] is True
    assert result["candidate_ids"] == [8]
