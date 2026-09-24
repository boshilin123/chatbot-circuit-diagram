from __future__ import annotations

import argparse

from pymilvus import RRFRanker

from app.core.config import get_settings
from app.rag.hybrid_search import hybrid_search


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Run Dense + BM25 hybrid search with RRF."
    )
    parser.add_argument("query")
    parser.add_argument("--top-k", type=int, default=5)
    args = parser.parse_args()

    settings = get_settings()

    # 1. 定义课程示例中的 RRF 融合策略
    ranker = RRFRanker(k=settings.rrf_k)

    # 2. 执行混合检索
    results = hybrid_search(
        args.query,
        ranker=ranker,
        top_k=args.top_k,
    )

    # 3. 打印 TopK
    for index, result in enumerate(results, start=1):
        print(
            f"[{index}] score={result.score:.4f} "
            f"id={result.doc_id} title={result.title}"
        )
        print(f"    path={result.hierarchy_path}")


if __name__ == "__main__":
    main()
