from typing import Any

from app.rag.hybrid_search import sparse_search


class FakeMilvusClient:
    def search(self, **kwargs: Any) -> list[list[dict]]:
        assert kwargs["anns_field"] == "sparse"
        assert kwargs["data"] == ["EDC17C53"]
        assert kwargs["limit"] == 3

        return [[
            {
                "distance": 6.8,
                "entity": {
                    "text": "层级路径：电路图 ECU电路图",
                    "doc_id": 99,
                    "title": "EDC17C53 ECU针脚定义",
                    "hierarchy_path": "电路图->ECU电路图",
                },
            }
        ]]

    def hybrid_search(self, **kwargs: Any) -> list[list[dict]]:
        raise AssertionError("This test only covers sparse_search")


def test_sparse_search_maps_milvus_results() -> None:
    results = sparse_search(
        "EDC17C53",
        top_k=3,
        client=FakeMilvusClient(),
    )

    assert len(results) == 1
    assert results[0].doc_id == 99
    assert results[0].title == "EDC17C53 ECU针脚定义"
    assert results[0].score == 6.8
