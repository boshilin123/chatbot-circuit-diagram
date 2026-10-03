# 本地精确查询：建立 CSV 文档 ID 索引，供 Agent 工具查找指定资料元数据。

from __future__ import annotations

from functools import lru_cache

from langchain_core.documents import Document

from app.rag.loader import load_circuit_documents


# CSV 只在首次查询时读取；更新数据后需清缓存或重启服务。
@lru_cache
def get_document_index() -> dict[int, Document]:
    """构建按 doc_id 查询的本地文档索引。"""

    # 1. 加载 CSV -> LangChain Documents
    result = load_circuit_documents()

    # 2. 建立 doc_id -> Document 映射
    return {
        int(doc.metadata["doc_id"]): doc
        for doc in result.documents
    }


def get_document(document_id: int) -> Document | None:
    """根据文档 ID 精确获取车辆电路图资料。"""

    return get_document_index().get(document_id)
