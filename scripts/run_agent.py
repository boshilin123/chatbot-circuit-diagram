from __future__ import annotations

import argparse
import asyncio

from app.agents.circuit_agent import run_circuit_agent


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


def main() -> None:
    asyncio.run(async_main())


if __name__ == "__main__":
    main()
