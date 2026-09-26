from __future__ import annotations

import argparse
from pathlib import Path

from app.core.paths import PROJECT_ROOT
from eval.benchmark import labeled_cases, load_benchmark
from eval.evaluate import build_retrievers
from eval.report import render_markdown_report, summarize_records
from eval.runners import evaluate_retriever

DEFAULT_BENCHMARK = PROJECT_ROOT / "eval" / "benchmark.jsonl"
DEFAULT_REPORT = PROJECT_ROOT / "eval" / "retrieval_report.md"


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Evaluate Dense / BM25 / Hybrid / Rerank retrieval."
    )
    parser.add_argument(
        "--benchmark",
        type=Path,
        default=DEFAULT_BENCHMARK,
    )
    parser.add_argument(
        "--report",
        type=Path,
        default=DEFAULT_REPORT,
    )
    parser.add_argument(
        "--top-k",
        type=int,
        default=5,
    )
    args = parser.parse_args()

    # 1. 只使用人工确认的 Ground Truth
    cases = labeled_cases(
        load_benchmark(args.benchmark)
    )

    if not cases:
        raise RuntimeError(
            "没有已验证的 Benchmark。请先为 expected_ids 填写真实文档 ID，"
            "并将 label_status 改为 verified。"
        )

    # 2. 依次运行四种检索方案
    records = []
    for method, retriever in build_retrievers().items():
        records.extend(
            evaluate_retriever(
                method,
                cases,
                retriever,
                top_k=args.top_k,
            )
        )

    # 3. 汇总指标并输出 Markdown 报告
    summaries = summarize_records(records)
    report = render_markdown_report(summaries)

    args.report.write_text(
        report,
        encoding="utf-8",
    )

    print(report)
    print(f"Report: {args.report}")


if __name__ == "__main__":
    main()
