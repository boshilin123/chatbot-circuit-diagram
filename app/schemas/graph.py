from typing import Literal

from pydantic import BaseModel


class GraphResponse(BaseModel):
    type: Literal["text", "options", "result"]
    content: str
    options: list[dict] | None = None
    documents: list[dict] | None = None
