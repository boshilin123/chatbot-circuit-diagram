from __future__ import annotations

from langchain_core.messages import HumanMessage, SystemMessage

from app.core.model import get_chat_model
from app.graph.state import CircuitSearchState


SYSTEM_PROMPT = """你是车辆电路图资料导航助手。
当前用户不是在执行资料检索，请简洁回答普通问候、能力介绍或闲聊。
不要在没有检索资料库的情况下编造文档 ID、车型资料或电路图结果。
"""


async def chat(state: CircuitSearchState) -> dict:
    """处理不需要检索资料库的普通对话。"""

    # 1. 初始化模型
    model = get_chat_model()

    # 2. 直接完成普通对话，不进入 RAG
    response = await model.ainvoke(
        [
            SystemMessage(content=SYSTEM_PROMPT),
            HumanMessage(content=state["original_query"]),
        ]
    )

    content = (
        response.content
        if isinstance(response.content, str)
        else str(response.content)
    )

    return {
        "final_text": content.strip(),
        "final_results": [],
        "status": "text",
    }
