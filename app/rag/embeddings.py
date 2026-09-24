from functools import lru_cache

from langchain_core.embeddings import Embeddings
from langchain_huggingface import HuggingFaceEmbeddings

from app.core.config import get_settings


@lru_cache
def get_dense_embeddings() -> Embeddings:
    """Create the local dense embedding model used by Milvus."""

    settings = get_settings()

    return HuggingFaceEmbeddings(
        model_name=settings.embedding_model,
        model_kwargs={
            "device": settings.embedding_device,
        },
        encode_kwargs={
            "batch_size": settings.embedding_batch_size,
            "normalize_embeddings": settings.embedding_normalize,
        },
        query_encode_kwargs={
            "normalize_embeddings": settings.embedding_normalize,
        },
        show_progress=False,
    )
