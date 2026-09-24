from langchain_core.messages import AIMessage

from app.agents.circuit_agent import run_circuit_agent


class FakeAgent:
    async def ainvoke(self, input: dict) -> dict:
        assert input["messages"][0]["role"] == "user"
        assert input["messages"][0]["content"] == "帮我找东风天龙仪表电路图"

        return {
            "messages": [
                AIMessage(content="找到 2 条相关资料。")
            ]
        }


async def test_run_circuit_agent_returns_last_message() -> None:
    response = await run_circuit_agent(
        "帮我找东风天龙仪表电路图",
        agent=FakeAgent(),
    )

    assert response == "找到 2 条相关资料。"


async def test_run_circuit_agent_skips_blank_message() -> None:
    response = await run_circuit_agent(
        "   ",
        agent=FakeAgent(),
    )

    assert response == ""
