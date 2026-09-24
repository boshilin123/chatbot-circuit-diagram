from __future__ import annotations

import argparse

from app.rag.pipeline import retrieve_docs


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Run Hybrid RRF + Cross-Encoder reranking."
    )
    parser.add_argument("query")
    parser.add_argument("--top-k", type=int, default=5)
    parser.add_argument("--candidate-k", type=int, default=20)
    args = parser.parse_args()

    # 1. 执行完整检索与精排
    docs = retrieve_docs(
        args.query,
        top_k=args.top_k,
        candidate_k=args.candidate_k,
    )

    # 2. 打印最终结果
    for index, doc in enumerate(docs, start=1):
        print(
            f"[{index}] rerank={doc.score:.4f} "
            f"retrieval={doc.retrieval_score:.4f} "
            f"id={doc.doc_id} title={doc.title}"
        )
        print(f"    path={doc.hierarchy_path}")


if __name__ == "__main__":
    main()
