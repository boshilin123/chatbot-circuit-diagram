from __future__ import annotations

import argparse

from app.rag.query_pipeline import prepare_search_query


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Preview Structured Output + Query Rewrite."
    )
    parser.add_argument("query")
    args = parser.parse_args()

    # 1. 准备标准检索 Query
    plan = prepare_search_query(args.query)

    # 2. 输出结构化结果
    print(f"original_query={plan.original_query}")
    print(f"rewritten_query={plan.rewritten_query}")
    print(f"intent={plan.intent.model_dump()}")
    print(f"filter_query={plan.filter_query}")


if __name__ == "__main__":
    main()
