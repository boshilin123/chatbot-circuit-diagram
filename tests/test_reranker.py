from app.rag.reranker import cross_encoder_rerank
from app.schemas.search import HybridSearchResult


class FakeCrossEncoder:
    def predict(
        self,
        sentences: list[tuple[str, str]],
        *,
        batch_size: int,
    ) -> list[float]:
        assert batch_size == 16
        assert len(sentences) == 3
        return [1.5, -0.2, 8.0]


def test_cross_encoder_rerank_sorts_and_filters_results() -> None:
    docs = [
        HybridSearchResult(
            doc_id=1,
            title="候选A",
            hierarchy_path="p1",
            content="内容A",
            score=0.90,
        ),
        HybridSearchResult(
            doc_id=2,
            title="候选B",
            hierarchy_path="p2",
            content="内容B",
            score=0.95,
        ),
        HybridSearchResult(
            doc_id=3,
            title="候选C",
            hierarchy_path="p3",
            content="内容C",
            score=0.80,
        ),
    ]

    results = cross_encoder_rerank(
        "测试查询",
        docs,
        top_k=2,
        min_score=0.0,
        model=FakeCrossEncoder(),
    )

    assert [doc.doc_id for doc in results] == [3, 1]
    assert results[0].score == 8.0
    assert results[0].retrieval_score == 0.80
    assert results[1].score == 1.5


def test_cross_encoder_rerank_returns_empty_for_blank_query() -> None:
    assert cross_encoder_rerank(
        "   ",
        [],
        model=FakeCrossEncoder(),
    ) == []
