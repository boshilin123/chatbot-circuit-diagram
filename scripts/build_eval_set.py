from __future__ import annotations

import argparse
from pathlib import Path

from app.core.paths import DEFAULT_KEYWORDS_PATH, PROJECT_ROOT
from eval.benchmark import save_benchmark
from eval.schema import EvalCase


DEFAULT_OUTPUT = PROJECT_ROOT / "eval" / "benchmark.jsonl"


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Build an unlabeled retrieval benchmark from keywords.txt."
    )
    parser.add_argument(
        "--keywords",
        type=Path,
        default=DEFAULT_KEYWORDS_PATH,
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=DEFAULT_OUTPUT,
    )
    args = parser.parse_args()

    # 1. 读取真实用户查询
    queries = [
        line.strip()
        for line in args.keywords.read_text(
            encoding="utf-8-sig"
        ).splitlines()
        if line.strip()
    ]

    # 2. 生成待人工确认 Ground Truth 的 EvalCase
    cases = [
        EvalCase(
            query=query,
            expected_ids=[],
            source="keywords.txt",
            label_status="pending",
        )
        for query in queries
    ]

    # 3. 写入 JSONL
    save_benchmark(
        cases,
        args.output,
    )

    print(f"Benchmark queries: {len(cases)}")
    print(f"Output: {args.output}")
    print("Ground Truth is pending manual verification.")


if __name__ == "__main__":
    main()
