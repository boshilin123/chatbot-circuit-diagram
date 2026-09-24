from functools import lru_cache

from langchain_core.embeddings import Embeddings
from langchain_huggingface import HuggingFaceEmbeddings

from app.core.config import get_settings


@lru_cache
def get_embeddings() -> Embeddings:
    """初始化稠密向量模型。"""

    settings = get_settings()

    # 1. 创建 Embedding 模型
    embeddings = HuggingFaceEmbeddings(
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

    # 2. 返回统一的 LangChain Embeddings 接口
    return embeddings
