# 集中配置：把 .env、环境变量及默认值映射为带类型的 Settings。

from functools import lru_cache

from pydantic import SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


# 声明应用、DeepSeek、Embedding、Milvus、精排和图工作流的全部配置。
class Settings(BaseSettings):
    app_name: str = "Circuit Diagram Retrieval Agent"
    app_version: str = "0.3.0"

    # 远程聊天模型配置：真实密钥来自环境，不写进源代码；调用时才显式取出 SecretStr。
    deepseek_api_key: SecretStr | None = None
    deepseek_api_base: str = "https://api.deepseek.com"
    deepseek_model: str = "deepseek-chat"

    # 稠密编码配置：batch_size 控制一次编码量，normalize 统一文档和查询向量的长度约定。
    embedding_model: str = "BAAI/bge-m3"
    embedding_device: str = "cpu"
    embedding_batch_size: int = 32
    embedding_normalize: bool = True

    # Milvus 配置：Dense 和 Hybrid 是两个独立集合，索引脚本分别建立。
    milvus_uri: str = "http://localhost:19530"
    milvus_dense_collection: str = "circuit_documents_dense"
    milvus_hybrid_collection: str = "circuit_documents_hybrid"
    milvus_consistency_level: str = "Session"
    milvus_timeout: float = 30.0

    # 召回参数：hybrid_candidate_k 控制每路候选规模，hybrid_top_k 控制融合后的输出规模。
    dense_top_k: int = 20
    sparse_top_k: int = 20
    hybrid_candidate_k: int = 20
    hybrid_top_k: int = 10
    rrf_k: int = 60

    # 精排参数：独立管线默认返回 5 条，并严格保留 score 大于 min_score 的候选。
    reranker_model: str = "Qwen/Qwen3-Reranker-0.6B"
    reranker_device: str = "auto"
    reranker_batch_size: int = 16
    reranker_top_k: int = 5
    reranker_min_score: float = 0.0

    # Web 工作流先保留较大候选集，再通过选择收敛到 graph_max_results；选项数包含返回上一步。
    graph_candidate_k: int = 30
    graph_max_results: int = 5
    graph_max_options: int = 5

    # .env 使用 UTF-8，环境键名不区分大小写；忽略无关字段，允许同文件包含其他工具配置。
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )


# 延迟创建并缓存配置对象，避免每次调用都重新读取 .env。
@lru_cache
def get_settings() -> Settings:
    return Settings()
