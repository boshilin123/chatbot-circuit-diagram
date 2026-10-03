# BM25 与混合召回：构造 Milvus 搜索请求，解析真实文档字段和检索分数。

from __future__ import annotations

from typing import Any, Protocol

from pymilvus import AnnSearchRequest, MilvusClient, RRFRanker

from app.core.config import get_settings
from app.rag.embeddings import get_embeddings
from app.schemas.search import HybridSearchResult, SparseSearchResult

OUTPUT_FIELDS = [
    "text",
    "doc_id",
    "title",
    "hierarchy_path",
]


# 声明单路与混合检索的客户端接口，测试时可换成模拟客户端。
class MilvusSearchClient(Protocol):
    # 接收 Milvus 单路检索关键字参数，返回服务端原始命中结构。
    def search(self, **kwargs: Any) -> Any: ...

    # 接收多路 AnnSearchRequest 和融合 ranker，返回原始混合检索命中。
    def hybrid_search(self, **kwargs: Any) -> Any: ...


def get_milvus_client() -> MilvusClient:
    """初始化课程示例风格的 MilvusClient。"""

    settings = get_settings()
    return MilvusClient(uri=settings.milvus_uri)


# 从 Milvus 单查询返回的第一组 hits 提取字段，构造指定类型的结果模型。
def _parse_results(
    res: Any,
    result_type: type[SparseSearchResult | HybridSearchResult],
) -> list[SparseSearchResult] | list[HybridSearchResult]:
    if not res:
        return []

    results = []

    # Milvus 单 query 搜索返回第一组 hits
    for hit in res[0]:
        entity = hit.get("entity", {})
        results.append(
            result_type(
                doc_id=int(entity["doc_id"]),
                title=str(entity["title"]),
                hierarchy_path=str(entity["hierarchy_path"]),
                content=str(entity["text"]),
                score=float(hit.get("distance", hit.get("score", 0.0))),
            )
        )

    return results


def sparse_search(
    query: str,
    *,
    top_k: int | None = None,
    filter_query: str | None = None,
    client: MilvusSearchClient | None = None,
) -> list[SparseSearchResult]:
    """BM25 稀疏检索。"""

    query = query.strip()
    if not query:
        return []

    settings = get_settings()
    top_k = top_k or settings.sparse_top_k

    if top_k <= 0:
        raise ValueError("top_k 必须大于 0")

    # 1. 初始化 MilvusClient
    client = client or get_milvus_client()

    # 2. 直接使用文本查询 sparse 字段
    search_kwargs = {
        "collection_name": settings.milvus_hybrid_collection,
        "data": [query],
        "anns_field": "sparse",
        "limit": top_k,
        "search_params": {"metric_type": "BM25"},
        "output_fields": OUTPUT_FIELDS,
    }

    if filter_query:
        search_kwargs["filter"] = filter_query

    res = client.search(**search_kwargs)

    # 3. 统一返回结构
    return _parse_results(res, SparseSearchResult)


def hybrid_search(
    query: str,
    *,
    ranker: RRFRanker | None = None,
    top_k: int | None = None,
    candidate_k: int | None = None,
    filter_query: str | None = None,
    client: MilvusSearchClient | None = None,
) -> list[HybridSearchResult]:
    """Dense + BM25 混合检索。"""

    query = query.strip()
    if not query:
        return []

    settings = get_settings()
    top_k = top_k or settings.hybrid_top_k
    candidate_k = candidate_k or settings.hybrid_candidate_k

    if top_k <= 0 or candidate_k <= 0:
        raise ValueError("top_k 和 candidate_k 必须大于 0")

    # 1. 创建稠密、稀疏请求
    embeddings = get_embeddings()
    # Dense 路先编码查询，Sparse 路直接发送原文本，两者表示不同但查询语义相同。
    query_dense_vector = embeddings.embed_query(query)

    dense_request = AnnSearchRequest(
        data=[query_dense_vector],
        anns_field="dense",
        param={"metric_type": "COSINE"},
        limit=candidate_k,
        filter=filter_query,
    )

    sparse_request = AnnSearchRequest(
        data=[query],
        anns_field="sparse",
        param={"metric_type": "BM25"},
        limit=candidate_k,
        filter=filter_query,
    )

    # 每一路最多取 candidate_k 条，再融合为 top_k 条；candidate_k 应覆盖所需输出规模。
    reqs = [dense_request, sparse_request]

    # 2. 设定 RRF 融合策略
    ranker = ranker or RRFRanker(k=settings.rrf_k)

    # 3. 发送混合检索请求
    client = client or get_milvus_client()
    res = client.hybrid_search(
        collection_name=settings.milvus_hybrid_collection,
        reqs=reqs,
        ranker=ranker,
        limit=top_k,
        output_fields=OUTPUT_FIELDS,
    )

    # 4. 统一返回结构
    return _parse_results(res, HybridSearchResult)
