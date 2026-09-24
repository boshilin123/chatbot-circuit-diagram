from __future__ import annotations

from collections.abc import Callable, Sequence
from time import perf_counter

from app.schemas.search import SearchResult
from eval.metrics import hit_at_k, mrr_at_k, recall_at_k
from eval.schema import EvalCase, RetrievalEvalRecord


Retriever = Callable[
    [str, int],
    Sequence[SearchResult],
]


def evaluate_retriever(
    method: str,
    cases: Sequence[EvalCase],
    retriever: Retriever,
    *,
    top_k: int = 5,
) -> list[RetrievalEvalRecord]:
    """运行一个 Retriever，并记录每条 Query 的指标和延迟。"""

    records: list[RetrievalEvalRecord] = []

    for case in cases:
        # 1. 记录单次检索耗时
        start = perf_counter()
        docs = retriever(
            case.query,
            top_k,
        )
        latency_ms = (
            perf_counter() - start
        ) * 1000

        # 2. 提取检索结果 ID
        retrieved_ids = [
            int(doc.doc_id)
            for doc in docs
        ]

        # 3. 计算课程式 TopK 检索指标
        records.append(
            RetrievalEvalRecord(
                method=method,
                query=case.query,
                expected_ids=case.expected_ids,
                retrieved_ids=retrieved_ids,
                latency_ms=latency_ms,
                hit_at_1=hit_at_k(
                    case.expected_ids,
                    retrieved_ids,
                    1,
                ),
                hit_at_5=hit_at_k(
                    case.expected_ids,
                    retrieved_ids,
                    5,
                ),
                recall_at_5=recall_at_k(
                    case.expected_ids,
                    retrieved_ids,
                    5,
                ),
                mrr_at_5=mrr_at_k(
                    case.expected_ids,
                    retrieved_ids,
                    5,
                ),
            )
        )

    return records
