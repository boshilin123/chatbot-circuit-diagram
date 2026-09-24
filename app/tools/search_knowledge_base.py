from __future__ import annotations

import json

from langchain.tools import tool

from app.core.model import get_chat_model
from app.rag.pipeline import retrieve_docs
from app.rag.query_pipeline import prepare_search_query
from app.schemas.tool import SearchKnowledgeBaseInput


@tool(args_schema=SearchKnowledgeBaseInput)
def search_knowledge_base(query: str, top_k: int = 5) -> str:
    """搜索车辆电路图资料库，返回最相关的文档 ID、标题和层级路径。

    当用户询问某个品牌、车型、发动机、ECU、电脑板、仪表、针脚定义、
    线路图或其他车辆电路图资料时，应调用此工具。不要自行编造资料结果。
    """

    # 1. 使用 Structured Output + Query Rewrite 准备检索 Query
    model = get_chat_model()
    query_plan = prepare_search_query(
        query,
        model=model,
    )

    # 2. 执行 Dense + BM25 + RRF + Cross-Encoder
    docs = retrieve_docs(
        query_plan.rewritten_query,
        top_k=top_k,
        filter_query=query_plan.filter_query,
    )

    # 3. 只返回资料库中真实存在的结果
    payload = {
        "original_query": query_plan.original_query,
        "rewritten_query": query_plan.rewritten_query,
        "intent": query_plan.intent.model_dump(),
        "filter_query": query_plan.filter_query,
        "count": len(docs),
        "documents": [
            {
                "id": doc.doc_id,
                "title": doc.title,
                "hierarchy_path": doc.hierarchy_path,
                "score": doc.score,
            }
            for doc in docs
        ],
    }

    return json.dumps(
        payload,
        ensure_ascii=False,
    )
