# 检索结果类型：统一文档字段，显式区分不同阶段 score 的来源。

from pydantic import BaseModel, Field


# 定义所有检索方案共享的 ID、标题、路径、内容和评分字段。
class SearchResult(BaseModel):
    doc_id: int
    title: str
    hierarchy_path: str
    content: str
    score: float


# 标记语义向量检索结果，score 保存 Milvus 返回的原始余弦相似度。
class DenseSearchResult(SearchResult):
    score: float = Field(
        description="Raw cosine similarity score returned by Milvus."
    )


# 标记 BM25 关键词检索结果，score 保存原始 BM25 分数。
class SparseSearchResult(SearchResult):
    score: float = Field(
        description="Raw BM25 score returned by Milvus."
    )


# 标记混合召回结果，score 是 Milvus ranker 输出的融合分数。
class HybridSearchResult(SearchResult):
    score: float = Field(
        description="Fusion score returned by the Milvus ranker."
    )


# 同时保留 retrieval_score 和精排后的 score，便于回看排序变化。
class RerankResult(SearchResult):
    retrieval_score: float = Field(
        description="Score produced by the retrieval stage before Cross-Encoder reranking."
    )
    score: float = Field(
        description="Cross-Encoder relevance score."
    )
