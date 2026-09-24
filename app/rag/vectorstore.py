from __future__ import annotations

from collections.abc import Sequence

from langchain_core.documents import Document
from langchain_milvus import Milvus

from app.core.config import get_settings
from app.rag.embeddings import get_dense_embeddings


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


def _document_for_milvus(document: Document) -> Document:
    """Keep Milvus metadata simple and filter-friendly.

    The loader may keep derived Python-only metadata such as a list of hierarchy
    segments. Milvus only needs stable scalar metadata at the dense stage.
    """

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


def create_dense_vector_store(*, drop_old: bool = False) -> Milvus:
    settings = get_settings()

    return Milvus(
        embedding_function=get_dense_embeddings(),
        collection_name=settings.milvus_dense_collection,
        collection_description="Vehicle circuit diagram dense retrieval collection",
        connection_args={"uri": settings.milvus_uri},
        consistency_level=settings.milvus_consistency_level,
        index_params=DENSE_INDEX_PARAMS,
        search_params=DENSE_SEARCH_PARAMS,
        drop_old=drop_old,
        auto_id=False,
        enable_dynamic_field=True,
        timeout=settings.milvus_timeout,
    )


def index_dense_documents(
    documents: Sequence[Document],
    *,
    recreate: bool = False,
    batch_size: int = 256,
) -> int:
    """Upsert LangChain Documents into the dense Milvus collection."""

    if batch_size <= 0:
        raise ValueError("batch_size 必须大于 0")

    prepared = [_document_for_milvus(document) for document in documents]
    if not prepared:
        return 0

    ids = [str(document.metadata["doc_id"]) for document in prepared]
    vector_store = create_dense_vector_store(drop_old=recreate)

    vector_store.upsert(
        ids=ids,
        documents=prepared,
        batch_size=batch_size,
    )

    return len(prepared)
