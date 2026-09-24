from functools import lru_cache

from pydantic import SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "Circuit Diagram Retrieval Agent"
    app_version: str = "0.3.0"

    deepseek_api_key: SecretStr | None = None
    deepseek_api_base: str = "https://api.deepseek.com"
    deepseek_model: str = "deepseek-chat"

    embedding_model: str = "BAAI/bge-m3"
    embedding_device: str = "cpu"
    embedding_batch_size: int = 32
    embedding_normalize: bool = True

    milvus_uri: str = "http://localhost:19530"
    milvus_dense_collection: str = "circuit_documents_dense"
    milvus_hybrid_collection: str = "circuit_documents_hybrid"
    milvus_consistency_level: str = "Session"
    milvus_timeout: float = 30.0

    dense_top_k: int = 20
    sparse_top_k: int = 20
    hybrid_candidate_k: int = 20
    hybrid_top_k: int = 10
    rrf_k: int = 60

    reranker_model: str = "Qwen/Qwen3-Reranker-0.6B"
    reranker_device: str = "auto"
    reranker_batch_size: int = 16
    reranker_top_k: int = 5
    reranker_min_score: float = 0.0

    graph_candidate_k: int = 30
    graph_max_results: int = 5
    graph_max_options: int = 5

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )


@lru_cache
def get_settings() -> Settings:
    return Settings()
