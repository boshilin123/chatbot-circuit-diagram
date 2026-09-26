from __future__ import annotations

from collections.abc import Callable
from typing import Any

import pytest
from langchain_core.documents import Document

from app.rag import hybrid_vectorstore, vectorstore


class FakeClient:
    def __init__(self, collection_exists: bool) -> None:
        self.collection_exists = collection_exists

    def has_collection(self, collection_name: str) -> bool:
        assert collection_name == "test_collection"
        return self.collection_exists


class FakeVectorStore:
    collection_name = "test_collection"

    def __init__(self, collection_exists: bool) -> None:
        self.client = FakeClient(collection_exists)
        self.calls: list[tuple[str, dict[str, Any]]] = []

    def add_documents(self, **kwargs: Any) -> None:
        self.calls.append(("add", kwargs))

    def upsert(self, **kwargs: Any) -> None:
        self.calls.append(("upsert", kwargs))


def make_document() -> Document:
    return Document(
        page_content="层级路径：电路图 商用车 东风 天龙",
        metadata={
            "doc_id": 123,
            "title": "东风天龙整车仪表电路图",
            "hierarchy_path": "电路图->整车电路图->商用车->东风->天龙",
            "hierarchy_depth": 5,
        },
    )


@pytest.mark.parametrize(
    ("module", "create_name", "index_name"),
    [
        (vectorstore, "create_dense_vectorstore", "index_dense_documents"),
        (hybrid_vectorstore, "create_hybrid_vectorstore", "index_hybrid_documents"),
    ],
)
@pytest.mark.parametrize(
    ("collection_exists", "expected_method"),
    [(False, "add"), (True, "upsert")],
)
def test_index_documents_uses_insert_for_new_collection(
    monkeypatch: pytest.MonkeyPatch,
    module: Any,
    create_name: str,
    index_name: str,
    collection_exists: bool,
    expected_method: str,
) -> None:
    fake = FakeVectorStore(collection_exists)
    create_vectorstore: Callable[..., FakeVectorStore] = lambda **_: fake
    monkeypatch.setattr(module, create_name, create_vectorstore)

    indexed = getattr(module, index_name)([make_document()], recreate=not collection_exists)

    assert indexed == 1
    assert fake.calls[0][0] == expected_method
    assert fake.calls[0][1]["ids"] == ["123"]
