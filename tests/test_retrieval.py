from langchain_core.documents import Document

from app.rag.retriever import dense_search


class FakeVectorStore:
    def similarity_search_with_score(
        self,
        query: str,
        *,
        k: int,
        expr: str | None = None,
    ) -> list[tuple[Document, float]]:
        assert query == "东风天龙仪表"
        assert k == 3
        assert expr is None

        return [
            (
                Document(
                    page_content="层级路径：电路图 整车电路图 商用车 东风 天龙",
                    metadata={
                        "doc_id": 123,
                        "title": "东风天龙整车仪表电路图",
                        "hierarchy_path": "电路图->整车电路图->商用车->东风->天龙",
                    },
                ),
                0.91,
            )
        ]


def test_dense_search_maps_vectorstore_results() -> None:
    results = dense_search(
        "东风天龙仪表",
        top_k=3,
        vectorstore=FakeVectorStore(),
    )

    assert len(results) == 1
    assert results[0].doc_id == 123
    assert results[0].title == "东风天龙整车仪表电路图"
    assert results[0].score == 0.91


def test_dense_search_skips_blank_query() -> None:
    assert dense_search("   ", top_k=3, vectorstore=FakeVectorStore()) == []
