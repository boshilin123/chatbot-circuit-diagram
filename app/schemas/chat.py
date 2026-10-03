# 前端接口契约：声明聊天/选择请求以及统一响应，字段沿用前端的 camelCase 命名。

from typing import Literal

from pydantic import BaseModel, Field


# 约束新问题请求的 sessionId 与 message 均至少一个字符。
class ChatRequest(BaseModel):
    sessionId: str = Field(min_length=1)
    message: str = Field(min_length=1)


# 约束选择请求，sessionId 指定会话，optionValue 指定机器可识别的选项。
class SelectRequest(BaseModel):
    sessionId: str = Field(min_length=1)
    # 允许前端提交数字或字符串 ID，实际恢复依据下面的 optionValue。
    optionId: str | int
    optionValue: str


# 描述文本、选择题和资料卡片三种响应的共同数据结构。
class ChatResponseData(BaseModel):
    type: Literal["text", "options", "result"] = "text"
    content: str
    options: list[dict] | None = None
    document: dict | None = None
    documents: list[dict] | None = None


# 统一业务返回格式，code=1 表示成功，code=0 表示业务失败。
class ApiResult(BaseModel):
    code: int
    msg: str
    data: ChatResponseData | None = None

    # 用统一成功码和提示包装 ChatResponseData。
    @classmethod
    def success(cls, data: ChatResponseData) -> "ApiResult":
        return cls(code=1, msg="success", data=data)

    # 用 code=0 和用户可读提示构造失败响应。
    @classmethod
    def error(cls, message: str) -> "ApiResult":
        return cls(code=0, msg=message, data=None)
