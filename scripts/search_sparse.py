from __future__ import annotations

import argparse

from app.rag.hybrid_search import sparse_search


def main() -> None:
    parser = argparse.ArgumentParser(description="Run a BM25 sparse search.")
    parser.add_argument("query")
    parser.add_argument("--top-k", type=int, default=5)
    args = parser.parse_args()

    # 1. 执行 BM25 检索
    results = sparse_search(args.query, top_k=args.top_k)

    # 2. 打印 TopK
    for index, result in enumerate(results, start=1):
        print(
            f"[{index}] score={result.score:.4f} "
            f"id={result.doc_id} title={result.title}"
        )
        print(f"    path={result.hierarchy_path}")


if __name__ == "__main__":
    main()
