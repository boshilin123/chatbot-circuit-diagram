from __future__ import annotations

from collections.abc import Sequence

from langchain_core.documents import Document
from langchain_milvus import BM25BuiltInFunction, Milvus

from app.core.config import get_settings
from app.rag.embeddings import get_embeddings


DENSE_INDEX_PARAMS = {
    "index_type": "HNSW",
    "metric_type": "COSINE",
    "params": {
        "M": 16,
        "efConstruction": 200,
    },
}

SPARSE_INDEX_PARAMS = {
    "index_type": "AUTOINDEX",
    "metric_type": "BM25",
    "params": {},
}

DENSE_SEARCH_PARAMS = {
    "metric_type": "COSINE",
    "params": {
        "ef": 64,
    },
}

SPARSE_SEARCH_PARAMS = {
    "metric_type": "BM25",
    "params": {},
}


def _prepare_document(doc: Document) -> Document:
    """整理写入 Hybrid Collection 的 Document metadata。"""

    return Document(
        page_content=doc.page_content,
        metadata={
            "doc_id": int(doc.metadata["doc_id"]),
            "title": str(doc.metadata["title"]),
            "hierarchy_path": str(doc.metadata["hierarchy_path"]),
            "hierarchy_depth": int(doc.metadata["hierarchy_depth"]),
        },
    )


def create_hybrid_vectorstore(*, drop_old: bool = False) -> Milvus:
    """初始化 Dense + BM25 Hybrid VectorStore。"""

    settings = get_settings()

    # 1. 初始化稠密向量模型
    embeddings = get_embeddings()

    # 2. 初始化 Milvus 内置 BM25 函数
    bm25_function = BM25BuiltInFunction(
        analyzer_params={"type": "chinese"},
        output_field_names="sparse",
    )

    # 3. 初始化同时包含 dense / sparse 字段的 Collection
    vectorstore = Milvus(
        embedding_function=embeddings,
        builtin_function=bm25_function,
        collection_name=settings.milvus_hybrid_collection,
        collection_description="Vehicle circuit diagram hybrid retrieval collection",
        connection_args={"uri": settings.milvus_uri},
        consistency_level=settings.milvus_consistency_level,
        vector_field=["dense", "sparse"],
        index_params=[DENSE_INDEX_PARAMS, SPARSE_INDEX_PARAMS],
        search_params=[DENSE_SEARCH_PARAMS, SPARSE_SEARCH_PARAMS],
        drop_old=drop_old,
        auto_id=False,
        enable_dynamic_field=True,
        timeout=settings.milvus_timeout,
    )

    return vectorstore


def index_hybrid_documents(
    docs: Sequence[Document],
    *,
    recreate: bool = False,
    batch_size: int = 256,
) -> int:
    """将 Documents 写入 Dense + BM25 Hybrid Collection。"""

    if batch_size <= 0:
        raise ValueError("batch_size 必须大于 0")

    # 1. 整理文档
    docs = [_prepare_document(doc) for doc in docs]
    if not docs:
        return 0

    # 2. 使用 doc_id 作为稳定主键
    ids = [str(doc.metadata["doc_id"]) for doc in docs]

    # 3. 写入 Hybrid Collection
    vectorstore = create_hybrid_vectorstore(drop_old=recreate)
    vectorstore.upsert(
        ids=ids,
        documents=docs,
        batch_size=batch_size,
    )

    return len(docs)
