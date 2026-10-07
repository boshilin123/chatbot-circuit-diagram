# 命令行：与 Web 共用 LangGraph 检索和多轮澄清流程。

from __future__ import annotations

import argparse
import asyncio
from uuid import uuid4

from app.graph.service import resume_search_workflow, run_search_workflow


async def async_main() -> None:
    parser = argparse.ArgumentParser(
        description="Run the LangGraph circuit diagram search workflow."
    )
    parser.add_argument("message")
    args = parser.parse_args()
    message = args.message.strip()
    if not message:
        parser.error("消息内容不能为空")

    # 内存检查点要求新查询和澄清恢复在同一进程、同一会话中完成。
    session_id = str(uuid4())
    response = await run_search_workflow(session_id, message)
    while True:
        print(response.content)
        if response.type != "options":
            for document in response.documents or []:
                print(
                    f"{document['id']} | {document['fileName']} | "
                    f"{document['hierarchyPath']}"
                )
            return

        options = response.options or []
        for index, option in enumerate(options, start=1):
            print(f"{index}. {option['text']}")

        while True:
            try:
                choice = input("请选择编号（q 退出）：").strip()
            except (EOFError, KeyboardInterrupt):
                return
            if choice.lower() == "q":
                return
            if choice.isascii() and choice.isdecimal() and 1 <= int(choice) <= len(options):
                break
            print("请输入有效的选项编号。")

        response = await resume_search_workflow(
            session_id,
            options[int(choice) - 1]["value"],
        )


def main() -> None:
    asyncio.run(async_main())


if __name__ == "__main__":
    main()
