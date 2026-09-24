from __future__ import annotations

import argparse

from app.rag.hybrid_vectorstore import index_hybrid_documents
from app.rag.loader import load_circuit_documents


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Index circuit documents into the Hybrid Milvus collection."
    )
    parser.add_argument(
        "--recreate",
        action="store_true",
        help="Drop and rebuild the Hybrid collection before indexing.",
    )
    parser.add_argument(
        "--batch-size",
        type=int,
        default=256,
    )
    return parser


def main() -> None:
    args = build_parser().parse_args()

    # 1. 加载 LangChain Documents
    result = load_circuit_documents()

    # 2. 写入 Dense + BM25 Hybrid Collection
    indexed = index_hybrid_documents(
        result.documents,
        recreate=args.recreate,
        batch_size=args.batch_size,
    )

    # 3. 输出导入摘要
    print(f"Loaded rows: {result.total_rows}")
    print(f"Valid documents: {result.valid_rows}")
    print(f"Invalid rows: {result.invalid_rows}")
    print(f"Indexed documents: {indexed}")


if __name__ == "__main__":
    main()
