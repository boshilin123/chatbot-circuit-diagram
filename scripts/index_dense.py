from __future__ import annotations

import argparse

from app.rag.loader import load_circuit_documents
from app.rag.vectorstore import index_dense_documents


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Index circuit documents into the dense Milvus collection."
    )
    parser.add_argument(
        "--recreate",
        action="store_true",
        help="Drop and rebuild the dense collection before indexing.",
    )
    parser.add_argument(
        "--batch-size",
        type=int,
        default=256,
        help="Number of documents per Milvus upsert batch.",
    )
    return parser


def main() -> None:
    args = build_parser().parse_args()

    result = load_circuit_documents()
    indexed = index_dense_documents(
        result.documents,
        recreate=args.recreate,
        batch_size=args.batch_size,
    )

    print(f"Loaded rows: {result.total_rows}")
    print(f"Valid documents: {result.valid_rows}")
    print(f"Invalid rows: {result.invalid_rows}")
    print(f"Indexed documents: {indexed}")


if __name__ == "__main__":
    main()
