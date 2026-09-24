from __future__ import annotations

import argparse
from pathlib import Path

from app.core.paths import DEFAULT_CIRCUIT_DATA_PATH
from app.rag.loader import load_circuit_documents


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Preview CSV -> LangChain Document ingestion."
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Parse and preview documents without writing to a vector database.",
    )
    parser.add_argument(
        "--data-file",
        type=Path,
        default=DEFAULT_CIRCUIT_DATA_PATH,
        help="CSV data file. Defaults to data/circuit-data.csv.",
    )
    parser.add_argument(
        "--sample-size",
        type=int,
        default=3,
        help="Number of sample documents to print.",
    )
    return parser


def print_summary(data_file: Path, sample_size: int) -> None:
    result = load_circuit_documents(data_file)

    print(f"Data file: {data_file}")
    print(f"Rows: {result.total_rows}")
    print(f"Valid documents: {result.valid_rows}")
    print(f"Invalid rows: {result.invalid_rows}")

    if result.issues:
        print("\nIssues:")
        for issue in result.issues[:10]:
            print(f"- row {issue.row_number}: {issue.message}")

    if result.documents:
        print("\nSample documents:")
        for index, document in enumerate(result.documents[:sample_size], start=1):
            print(f"\n[{index}] {document.page_content}")
            print(f"metadata={document.metadata}")


def main() -> None:
    parser = build_parser()
    args = parser.parse_args()

    if not args.dry_run:
        parser.error("Phase 2 仅提供 --dry-run。Milvus 持久化将在 Phase 3 实现。")

    if args.sample_size < 0:
        parser.error("--sample-size 不能小于 0")

    print_summary(args.data_file, args.sample_size)


if __name__ == "__main__":
    main()
