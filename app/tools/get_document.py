from __future__ import annotations

import json

from langchain.tools import tool

from app.rag.document_store import get_document
from app.schemas.tool import GetDocumentInput


@tool(args_schema=GetDocumentInput)
def get_document_by_id(document_id: int) -> str:
    """根据文档 ID 获取车辆电路图资料的标题和层级路径。"""

    # 1. 从本地标准文档索引中获取资料
    doc = get_document(document_id)

    # 2. 文档不存在时明确返回 not_found
    if doc is None:
        return json.dumps(
            {
                "status": "not_found",
                "document_id": document_id,
            },
            ensure_ascii=False,
        )

    # 3. 返回资料库中的真实元数据
    return json.dumps(
        {
            "status": "found",
            "document": {
                "id": int(doc.metadata["doc_id"]),
                "title": str(doc.metadata["title"]),
                "hierarchy_path": str(doc.metadata["hierarchy_path"]),
            },
        },
        ensure_ascii=False,
    )
