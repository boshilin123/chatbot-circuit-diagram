from __future__ import annotations

from typing import Protocol

from langchain_core.documents import Document

from app.core.config import get_settings
from app.rag.vectorstore import create_dense_vector_store
from app.schemas.search import DenseSearchResult


class DenseVectorStore(Protocol):
    def similarity_search_with_score(
        self,
        query: str,
        *,
        k: int,
        expr: str | None = None,
    ) -> list[tuple[Document, float]]: ...


class DenseRetriever:
    """Thin retrieval service around the Milvus vector store."""

    def __init__(self, vector_store: DenseVectorStore | None = None) -> None:
        self._vector_store = vector_store or create_dense_vector_store()

    def search(
        self,
        query: str,
        *,
        top_k: int | None = None,
        expr: str | None = None,
    ) -> list[DenseSearchResult]:
        normalized_query = query.strip()
        if not normalized_query:
            return []

        settings = get_settings()
        k = top_k or settings.dense_top_k

        if k <= 0:
            raise ValueError("top_k 必须大于 0")

        results = self._vector_store.similarity_search_with_score(
            normalized_query,
            k=k,
            expr=expr,
        )

        return [
            DenseSearchResult(
                doc_id=int(document.metadata["doc_id"]),
                title=str(document.metadata["title"]),
                hierarchy_path=str(document.metadata["hierarchy_path"]),
                content=document.page_content,
                score=float(score),
            )
            for document, score in results
        ]
