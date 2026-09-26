from __future__ import annotations

from functools import lru_cache
from typing import Any, Protocol

from langchain.agents import create_agent

from app.core.model import get_chat_model
from app.tools.get_document import get_document_by_id
from app.tools.search_knowledge_base import search_knowledge_base

SYSTEM_PROMPT = """你是车辆电路图资料导航助手。

你的主要任务是帮助用户查找车辆、工程机械、发动机、ECU、电脑板、仪表、
针脚定义、线路图和整车电路图等资料。

工具使用规则：
1. 普通问候、能力介绍等非检索问题可以直接回答。
2. 只要用户要查找具体资料、文档、ID、车型、ECU 或电路图，必须调用
   search_knowledge_base，不要凭模型记忆编造结果。
3. 当用户明确提供文档 ID 并要求查看该资料时，可以调用 get_document_by_id。
4. 只能向用户展示工具真实返回的文档 ID、标题和层级路径。
5. 不要虚构不存在的车型、ECU 型号、文档 ID 或检索结果。
6. 多轮候选收敛与“结果超过 5 条时必须继续澄清”的硬规则由后续 LangGraph
   工作流负责，不要自行编造选择项。
"""


class CircuitAgent(Protocol):
    async def ainvoke(self, input: dict[str, Any]) -> dict[str, Any]: ...


@lru_cache
def get_circuit_agent() -> CircuitAgent:
    """按照课程 create_agent 示例创建车辆电路图 Agent。"""

    # 1. 初始化模型
    model = get_chat_model()

    # 2. 定义 Agent 可以调用的工具
    tools = [
        search_knowledge_base,
        get_document_by_id,
    ]

    # 3. 创建 LangChain Agent
    agent = create_agent(
        model=model,
        tools=tools,
        system_prompt=SYSTEM_PROMPT,
    )

    return agent


def _message_content(message: Any) -> str:
    content = getattr(message, "content", "")

    if isinstance(content, str):
        return content

    return str(content)


async def run_circuit_agent(
    message: str,
    *,
    agent: CircuitAgent | None = None,
) -> str:
    """调用 Agent 并返回最终文本回复。"""

    message = message.strip()
    if not message:
        return ""

    # 1. 获取 Agent
    agent = agent or get_circuit_agent()

    # 2. 按课程 create_agent 的 messages 输入格式调用
    result = await agent.ainvoke(
        {
            "messages": [
                {
                    "role": "user",
                    "content": message,
                }
            ]
        }
    )

    # 3. 返回 Agent 最后一条消息
    messages = result.get("messages", [])
    if not messages:
        return ""

    return _message_content(messages[-1]).strip()
