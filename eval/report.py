from __future__ import annotations

from collections import defaultdict
from collections.abc import Sequence

from eval.metrics import percentile
from eval.schema import RetrievalEvalRecord, RetrievalSummary


def summarize_records(
    records: Sequence[RetrievalEvalRecord],
) -> list[RetrievalSummary]:
    """按检索方法汇总离线评估指标。"""

    groups: dict[str, list[RetrievalEvalRecord]] = defaultdict(list)

    for record in records:
        groups[record.method].append(record)

    summaries = []

    for method, method_records in groups.items():
        count = len(method_records)
        latencies = [
            record.latency_ms
            for record in method_records
        ]

        summaries.append(
            RetrievalSummary(
                method=method,
                query_count=count,
                hit_at_1=sum(
                    record.hit_at_1
                    for record in method_records
                ) / count,
                hit_at_5=sum(
                    record.hit_at_5
                    for record in method_records
                ) / count,
                recall_at_5=sum(
                    record.recall_at_5
                    for record in method_records
                ) / count,
                mrr_at_5=sum(
                    record.mrr_at_5
                    for record in method_records
                ) / count,
                p50_latency_ms=percentile(
                    latencies,
                    50,
                ),
                p95_latency_ms=percentile(
                    latencies,
                    95,
                ),
            )
        )

    return sorted(
        summaries,
        key=lambda summary: summary.method,
    )


def render_markdown_report(
    summaries: Sequence[RetrievalSummary],
) -> str:
    """生成可直接放入 README 的 Markdown 表格。"""

    lines = [
        "# Retrieval Evaluation",
        "",
        "| Method | Queries | Hit@1 | Hit@5 | Recall@5 | MRR@5 | P50 ms | P95 ms |",
        "|---|---:|---:|---:|---:|---:|---:|---:|",
    ]

    for summary in summaries:
        lines.append(
            "| "
            f"{summary.method} | "
            f"{summary.query_count} | "
            f"{summary.hit_at_1:.4f} | "
            f"{summary.hit_at_5:.4f} | "
            f"{summary.recall_at_5:.4f} | "
            f"{summary.mrr_at_5:.4f} | "
            f"{summary.p50_latency_ms:.2f} | "
            f"{summary.p95_latency_ms:.2f} |"
        )

    return "\n".join(lines) + "\n"
