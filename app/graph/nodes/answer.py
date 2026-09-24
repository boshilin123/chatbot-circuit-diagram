from __future__ import annotations

from app.core.config import get_settings
from app.graph.state import CircuitSearchState


def final_answer(state: CircuitSearchState) -> dict:
    """生成最终不超过 5 条的资料结果。"""

    settings = get_settings()

    # 1. Graph 硬限制最终结果数量
    final_results = state.get(
        "candidate_documents",
        [],
    )[:settings.graph_max_results]

    # 2. 输出 ID + 标题，具体卡片由前端渲染
    lines = [
        f"{index}. [ID:{doc['doc_id']}] {doc['title']}"
        for index, doc in enumerate(final_results, start=1)
    ]

    content = (
        f"找到 {len(final_results)} 条最相关资料：\n"
        + "\n".join(lines)
    )

    return {
        "final_results": final_results,
        "final_text": content,
        "status": "final",
    }


def no_results(state: CircuitSearchState) -> dict:
    """没有命中资料时的确定性结果。"""

    return {
        "final_results": [],
        "final_text": (
            "暂时没有找到匹配的车辆电路图资料。"
            "你可以补充品牌、车型、发动机或 ECU 型号后重新查询。"
        ),
        "status": "no_results",
    }
