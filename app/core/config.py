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
    milvus_consistency_level: str = "Session"
    milvus_timeout: float = 30.0

    dense_top_k: int = 20

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )


@lru_cache
def get_settings() -> Settings:
    return Settings()
