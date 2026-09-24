from pydantic import BaseModel

from app.schemas.intent import SearchIntent


class SearchQueryPlan(BaseModel):
    """进入 Retriever 前的标准查询结构。"""

    original_query: str
    rewritten_query: str
    intent: SearchIntent
    filter_query: str | None = None
