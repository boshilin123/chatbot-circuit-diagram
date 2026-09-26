from __future__ import annotations

import argparse
import re

from app.core.paths import PROJECT_ROOT
from app.rag.loader import load_circuit_documents
from eval.benchmark import load_benchmark

DEFAULT_BENCHMARK = PROJECT_ROOT / "eval" / "benchmark.jsonl"


def normalize(text: str) -> str:
    """用于人工标注辅助的轻量文本归一化。"""

    return re.sub(
        r"[^0-9a-zA-Z\u4e00-\u9fff]+",
        "",
        text,
    ).lower()


def character_bigrams(text: str) -> set[str]:
    text = normalize(text)
    if len(text) < 2:
        return {text} if text else set()

    return {
        text[index:index + 2]
        for index in range(len(text) - 1)
    }


def candidate_score(
    query: str,
    document_text: str,
) -> float:
    """只用于人工 Ground Truth 标注辅助，不参与正式 RAG。"""

    query_terms = character_bigrams(query)
    doc_terms = character_bigrams(document_text)

    if not query_terms:
        return 0.0

    return (
        len(query_terms.intersection(doc_terms))
        / len(query_terms)
    )


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Suggest CSV candidates for manual benchmark labeling."
    )
    parser.add_argument(
        "--top-k",
        type=int,
        default=10,
    )
    parser.add_argument(
        "--case",
        type=int,
        default=None,
        help="Only print one 1-based benchmark case.",
    )
    args = parser.parse_args()

    cases = load_benchmark(DEFAULT_BENCHMARK)
    docs = load_circuit_documents().documents

    selected_cases = cases
    if args.case is not None:
        selected_cases = [
            cases[args.case - 1]
        ]

    for case_index, case in enumerate(selected_cases, start=1):
        ranked = sorted(
            docs,
            key=lambda doc: candidate_score(
                case.query,
                (
                    f"{doc.metadata['title']} "
                    f"{doc.metadata['hierarchy_path']}"
                ),
            ),
            reverse=True,
        )[:args.top_k]

        print(f"\n[{case_index}] Query: {case.query}")

        for rank, doc in enumerate(ranked, start=1):
            score = candidate_score(
                case.query,
                (
                    f"{doc.metadata['title']} "
                    f"{doc.metadata['hierarchy_path']}"
                ),
            )
            print(
                f"  {rank}. score={score:.3f} "
                f"ID={doc.metadata['doc_id']} "
                f"{doc.metadata['title']}"
            )


if __name__ == "__main__":
    main()
