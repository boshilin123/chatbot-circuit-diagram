from typing import Literal

from pydantic import BaseModel, Field


class ChatRequest(BaseModel):
    sessionId: str = Field(min_length=1)
    message: str = Field(min_length=1)


class SelectRequest(BaseModel):
    sessionId: str = Field(min_length=1)
    optionId: str | int
    optionValue: str


class ChatResponseData(BaseModel):
    type: Literal["text", "options", "result"] = "text"
    content: str
    options: list[dict] | None = None
    document: dict | None = None
    documents: list[dict] | None = None


class ApiResult(BaseModel):
    code: int
    msg: str
    data: ChatResponseData | None = None

    @classmethod
    def success(cls, data: ChatResponseData) -> "ApiResult":
        return cls(code=1, msg="success", data=data)

    @classmethod
    def error(cls, message: str) -> "ApiResult":
        return cls(code=0, msg=message, data=None)
