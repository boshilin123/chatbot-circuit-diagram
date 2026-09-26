from __future__ import annotations

from collections.abc import Sequence

from langchain_core.documents import Document
from langchain_milvus import Milvus

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

DENSE_SEARCH_PARAMS = {
    "metric_type": "COSINE",
    "params": {
        "ef": 64,
    },
}


def _prepare_document(document: Document) -> Document:
    """整理写入 Milvus 的 Document metadata。"""

    metadata = {
        "doc_id": int(document.metadata["doc_id"]),
        "title": str(document.metadata["title"]),
        "hierarchy_path": str(document.metadata["hierarchy_path"]),
        "hierarchy_depth": int(document.metadata["hierarchy_depth"]),
    }

    return Document(
        page_content=document.page_content,
        metadata=metadata,
    )


def create_dense_vectorstore(*, drop_old: bool = False) -> Milvus:
    """创建 Dense Retrieval 使用的 Milvus VectorStore。"""

    settings = get_settings()

    # 1. 初始化 Embedding 模型
    embeddings = get_embeddings()

    # 2. 创建 Milvus VectorStore
    vectorstore = Milvus(
        embedding_function=embeddings,
        collection_name=settings.milvus_dense_collection,
        collection_description="Vehicle circuit diagram dense retrieval collection",
        connection_args={"uri": settings.milvus_uri},
        consistency_level=settings.milvus_consistency_level,
        index_params=DENSE_INDEX_PARAMS,
        search_params=DENSE_SEARCH_PARAMS,
        drop_old=drop_old,
        auto_id=False,
        enable_dynamic_field=True,
    )

    return vectorstore


def index_dense_documents(
    docs: Sequence[Document],
    *,
    recreate: bool = False,
    batch_size: int = 256,
) -> int:
    """将 LangChain Documents 写入 Dense Collection。"""

    if batch_size <= 0:
        raise ValueError("batch_size 必须大于 0")

    # 1. 整理写入 Milvus 的文档
    docs = [_prepare_document(doc) for doc in docs]
    if not docs:
        return 0

    # 2. 使用文档 ID 作为稳定主键
    ids = [str(doc.metadata["doc_id"]) for doc in docs]

    # 3. 初始化向量库并写入数据
    vectorstore = create_dense_vectorstore(drop_old=recreate)
    write_documents = (
        vectorstore.upsert
        if vectorstore.client.has_collection(vectorstore.collection_name)
        else vectorstore.add_documents
    )
    write_documents(ids=ids, documents=docs, batch_size=batch_size)

    return len(docs)
