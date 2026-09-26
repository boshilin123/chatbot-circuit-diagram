from __future__ import annotations

import re
from typing import Protocol

from app.core.model import get_chat_model
from app.schemas.intent import SearchIntent


class StructuredModel(Protocol):
    def invoke(self, input: str) -> SearchIntent | None: ...


class ChatModel(Protocol):
    def with_structured_output(
        self,
        schema: type[SearchIntent],
        *,
        method: str,
    ) -> StructuredModel: ...


INTENT_PROMPT = """请从下面的车辆电路图检索问题中提取结构化搜索意图，并以 JSON 格式返回。

返回对象必须包含且只包含以下字段：
{{
  "is_search_request": true,
  "brand": "品牌或 null",
  "model": "车型/系列或 null",
  "component": "部件/系统或 null",
  "ecu_type": "ECU/控制器/发动机编码或 null",
  "document_type": "针脚定义/电路图/线路图等资料类型或 null",
  "keywords": ["未被上述字段表达的其他关键词"]
}}

要求：
1. 先判断用户是否正在查找车辆电路图资料。普通问候、闲聊、能力介绍时 is_search_request=false。
2. 只提取用户明确表达的信息，不猜测不存在的品牌、车型、发动机或 ECU 型号。
3. 型号、数字、英文字母编码必须保留原始写法。
4. 无法确定的字段返回 null。
5. keywords 只保留仍有检索价值且没有被其他字段完整表达的词。
6. “针脚定义”、“电路图”、“线路图”必须放入 document_type，不要放入 keywords。
7. “仪表”、“发动机电脑板”、“液压电脑板”等必须放入 component。

问题: {query}
"""

NON_SEARCH_FALLBACK_PATTERN = re.compile(
    r"^(?:你好|您好|嗨|hello|hi|你是谁|你能做什么|谢谢|再见)[!！?？。,.，\s]*$",
    re.IGNORECASE,
)


def _fallback_intent(query: str) -> SearchIntent:
    """Structured Output 空响应时的确定性降级。

    对明确的短问候仍走普通对话；其他非空输入优先当作检索请求，
    并把原问题保留为软关键词，避免丢失明确存在的资料查询。
    """

    is_search_request = NON_SEARCH_FALLBACK_PATTERN.fullmatch(query) is None
    return SearchIntent(
        is_search_request=is_search_request,
        keywords=[query] if is_search_request else [],
    )


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
    structured_model = model.with_structured_output(
        SearchIntent,
        method="json_mode",
    )

    # 3. 调用模型并直接获得 Pydantic 对象
    intent = structured_model.invoke(
        INTENT_PROMPT.format(query=query)
    )

    return intent if intent is not None else _fallback_intent(query)
