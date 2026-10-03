# 图服务响应模型：隔离 LangGraph 内部状态与 HTTP 层，统一工作流输出。

from typing import Literal

from pydantic import BaseModel


# 表达图执行后的文本、待选项或资料结果。
class GraphResponse(BaseModel):
    type: Literal["text", "options", "result"]
    content: str
    options: list[dict] | None = None
    documents: list[dict] | None = None
