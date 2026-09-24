from __future__ import annotations

from typing import Protocol

from langchain_core.messages import BaseMessage

from app.core.model import get_chat_model


class ChatModel(Protocol):
    def invoke(self, input: str) -> BaseMessage: ...


REWRITE_PROMPT = """将以下车辆电路图检索问题改写为适合检索的关键词形式。
提取核心概念，用空格分隔。保留原问题中的品牌、车型、发动机、ECU 型号和字母数字编码。
不要猜测或补充用户没有提供的具体型号。只输出关键词不要解释。

问题: {query}
关键词:
"""


def rewrite_query(
    query: str,
    *,
    model: ChatModel | None = None,
) -> str:
    """按照课程 Query Rewrite 示例改写用户问题。"""

    query = query.strip()
    if not query:
        return ""

    # 1. 编写提示词并初始化模型
    model = model or get_chat_model()
    rewrite_prompt = REWRITE_PROMPT.format(query=query)

    # 2. 调用模型，重写用户问题
    response = model.invoke(rewrite_prompt)
    rewritten = str(response.content).strip()

    # 3. 模型返回空内容时回退到原始 Query
    return rewritten or query
