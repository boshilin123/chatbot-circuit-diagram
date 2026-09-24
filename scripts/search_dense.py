from __future__ import annotations

import argparse

from app.rag.retriever import DenseRetriever


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Run a dense Milvus search.")
    parser.add_argument("query", help="Natural-language search query.")
    parser.add_argument("--top-k", type=int, default=5)
    return parser


def main() -> None:
    args = build_parser().parse_args()

    results = DenseRetriever().search(args.query, top_k=args.top_k)

    for index, result in enumerate(results, start=1):
        print(
            f"[{index}] score={result.score:.4f} "
            f"id={result.doc_id} title={result.title}"
        )
        print(f"    path={result.hierarchy_path}")


if __name__ == "__main__":
    main()
