from __future__ import annotations

from typing import Protocol

from langchain_core.documents import Document

from app.core.config import get_settings
from app.rag.vectorstore import create_dense_vectorstore
from app.schemas.search import DenseSearchResult


class VectorStore(Protocol):
    def similarity_search_with_score(
        self,
        query: str,
        *,
        k: int,
        expr: str | None = None,
    ) -> list[tuple[Document, float]]: ...


def dense_search(
    query: str,
    *,
    top_k: int | None = None,
    expr: str | None = None,
    vectorstore: VectorStore | None = None,
) -> list[DenseSearchResult]:
    """稠密向量检索。"""

    query = query.strip()
    if not query:
        return []

    settings = get_settings()
    top_k = top_k or settings.dense_top_k

    if top_k <= 0:
        raise ValueError("top_k 必须大于 0")

    # 1. 初始化向量库
    vectorstore = vectorstore or create_dense_vectorstore()

    # 2. 执行稠密向量检索
    retrieved_docs = vectorstore.similarity_search_with_score(
        query,
        k=top_k,
        expr=expr,
    )

    # 3. 统一返回结构
    return [
        DenseSearchResult(
            doc_id=int(doc.metadata["doc_id"]),
            title=str(doc.metadata["title"]),
            hierarchy_path=str(doc.metadata["hierarchy_path"]),
            content=doc.page_content,
            score=float(score),
        )
        for doc, score in retrieved_docs
    ]
