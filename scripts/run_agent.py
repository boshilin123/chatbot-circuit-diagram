# 命令行：独立运行 LangChain 工具 Agent，与 Web 的 LangGraph 多轮流程分开。

from __future__ import annotations

import argparse
import asyncio

from app.agents.circuit_agent import run_circuit_agent


# 解析用户消息，等待独立 Agent 执行后打印最终文本。
async def async_main() -> None:
    parser = argparse.ArgumentParser(
        description="Run the LangChain circuit diagram agent."
    )
    parser.add_argument("message")
    args = parser.parse_args()

    # 1. 调用 Agent
    response = await run_circuit_agent(args.message)

    # 2. 输出最终回复
    print(response)


# 用 asyncio.run 创建并管理事件循环，执行异步命令行入口。
def main() -> None:
    asyncio.run(async_main())


if __name__ == "__main__":
    main()
