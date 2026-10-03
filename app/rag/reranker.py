# 精排模块：对查询和每条候选组成的文本对评分，重新排序并按阈值过滤。

from __future__ import annotations

from collections.abc import Sequence
from functools import lru_cache
from typing import Protocol

import torch
from sentence_transformers import CrossEncoder

from app.core.config import get_settings
from app.schemas.search import RerankResult, SearchResult


# 声明精排所需的 predict 接口，允许测试注入固定评分模型。
class CrossEncoderModel(Protocol):
    # 接收 (query, content) 文本对列表及批次大小，返回分数序列。
    def predict(
        self,
        sentences: list[tuple[str, str]],
        *,
        batch_size: int,
    ) -> Sequence[float]: ...


# 缓存精排模型，避免重复加载权重。
@lru_cache
def get_reranker_model() -> CrossEncoder:
    """初始化课程示例中的 Cross-Encoder Reranker。"""

    settings = get_settings()

    # 1. 自动选择运行设备
    if settings.reranker_device == "auto":
        device = "cuda" if torch.cuda.is_available() else "cpu"
    else:
        device = settings.reranker_device

    # 2. 初始化 Qwen3 Reranker
    model = CrossEncoder(
        settings.reranker_model,
        device=device,
    )

    return model


def cross_encoder_rerank(
    query: str,
    docs: Sequence[SearchResult],
    *,
    top_k: int | None = None,
    min_score: float | None = None,
    model: CrossEncoderModel | None = None,
) -> list[RerankResult]:
    """使用 Cross-Encoder 对召回结果做精排。"""

    query = query.strip()
    if not query or not docs:
        return []

    settings = get_settings()
    top_k = top_k or settings.reranker_top_k
    min_score = (
        settings.reranker_min_score
        if min_score is None
        else min_score
    )

    if top_k <= 0:
        raise ValueError("top_k 必须大于 0")

    # 1. 初始化 Cross-Encoder
    model = model or get_reranker_model()

    # 2. 计算 Query-Document Pair 分数
    pairs = [(query, doc.content) for doc in docs]
    scores = model.predict(
        pairs,
        batch_size=settings.reranker_batch_size,
    )

    # 3. 保存精排分数；strict=True 检查每条候选都得到一个分数
    reranked_docs = [
        RerankResult(
            doc_id=doc.doc_id,
            title=doc.title,
            hierarchy_path=doc.hierarchy_path,
            content=doc.content,
            retrieval_score=doc.score,
            score=float(score),
        )
        for doc, score in zip(docs, scores, strict=True)
    ]

    # 4. 按精排分数降序排序
    reranked_docs = sorted(
        reranked_docs,
        key=lambda doc: doc.score,
        reverse=True,
    )

    # 5. 只保留分数严格大于阈值的资料，再截取 TopK
    positive_docs = [
        doc for doc in reranked_docs
        if doc.score > min_score
    ]

    return positive_docs[:top_k]
