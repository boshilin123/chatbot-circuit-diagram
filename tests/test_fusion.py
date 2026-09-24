from app.rag.fusion import reciprocal_rank_fusion
from app.schemas.search import DenseSearchResult, SparseSearchResult


def test_reciprocal_rank_fusion_uses_rank_not_raw_score() -> None:
    dense_results = [
        DenseSearchResult(
            doc_id=1,
            title="A",
            hierarchy_path="p1",
            content="A",
            score=0.95,
        ),
        DenseSearchResult(
            doc_id=2,
            title="B",
            hierarchy_path="p2",
            content="B",
            score=0.90,
        ),
    ]

    sparse_results = [
        SparseSearchResult(
            doc_id=2,
            title="B",
            hierarchy_path="p2",
            content="B",
            score=8.0,
        ),
        SparseSearchResult(
            doc_id=1,
            title="A",
            hierarchy_path="p1",
            content="A",
            score=2.0,
        ),
    ]

    results = reciprocal_rank_fusion(
        [dense_results, sparse_results],
        k=60,
    )

    assert [result.doc_id for result in results] == [1, 2]
    assert results[0].score == results[1].score
