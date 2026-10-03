# 查询改写：将自然语言问题转换成检索关键词，保留车型及 ECU 等原始编码。

from __future__ import annotations

from typing import Protocol

from langchain_core.messages import BaseMessage

from app.core.model import get_chat_model


# 声明查询改写仅需的同步 invoke 接口，便于替换模型或单元测试。
class ChatModel(Protocol):
    # 接收提示词字符串，返回带 content 的 LangChain BaseMessage。
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
