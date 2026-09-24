from __future__ import annotations

from typing import Protocol

from app.core.model import get_chat_model
from app.schemas.intent import SearchIntent


class StructuredModel(Protocol):
    def invoke(self, input: str) -> SearchIntent: ...


class ChatModel(Protocol):
    def with_structured_output(
        self,
        schema: type[SearchIntent],
    ) -> StructuredModel: ...


INTENT_PROMPT = """请从下面的车辆电路图检索问题中提取结构化搜索意图。

要求：
1. 先判断用户是否正在查找车辆电路图资料。普通问候、闲聊、能力介绍时 is_search_request=false。
2. 只提取用户明确表达的信息，不猜测不存在的品牌、车型、发动机或 ECU 型号。
3. 型号、数字、英文字母编码必须保留原始写法。
4. 无法确定的字段返回 null。
5. keywords 只保留仍有检索价值且没有被其他字段完整表达的词。

问题: {query}
"""


def parse_search_intent(
    query: str,
    *,
    model: ChatModel | None = None,
) -> SearchIntent:
    """使用 LangChain Structured Output 提取检索意图。"""

    query = query.strip()
    if not query:
        return SearchIntent()

    # 1. 初始化模型
    model = model or get_chat_model()

    # 2. 按课程示例绑定 Pydantic Structured Output
    structured_model = model.with_structured_output(SearchIntent)

    # 3. 调用模型并直接获得 Pydantic 对象
    intent = structured_model.invoke(
        INTENT_PROMPT.format(query=query)
    )

    return intent
