from pydantic import BaseModel, Field


class SearchKnowledgeBaseInput(BaseModel):
    """search_knowledge_base Tool 输入。"""

    query: str = Field(
        min_length=1,
        description="用户要检索的车辆电路图问题，保留原始车型和 ECU 编码。",
    )
    top_k: int = Field(
        default=5,
        ge=1,
        le=20,
        description="最多返回多少条候选文档。",
    )


class GetDocumentInput(BaseModel):
    """get_document_by_id Tool 输入。"""

    document_id: int = Field(
        gt=0,
        description="车辆电路图文档 ID。",
    )
