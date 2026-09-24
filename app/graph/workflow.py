from __future__ import annotations

from functools import lru_cache

from langgraph.checkpoint.memory import InMemorySaver
from langgraph.graph import END, START, StateGraph

from app.graph.nodes.answer import final_answer, no_results
from app.graph.nodes.chat import chat
from app.graph.nodes.clarify import clarify
from app.graph.nodes.facets import build_facets
from app.graph.nodes.retrieve import retrieve, route_by_result_count
from app.graph.nodes.rewrite import rewrite_query_node
from app.graph.nodes.understand import route_query, understand_query
from app.graph.state import CircuitSearchState


@lru_cache
def get_circuit_graph():
    """构建课程风格的 StateGraph 工作流。"""

    # 1. 定义 StateGraph
    builder = StateGraph(CircuitSearchState)

    # 2. 注册 Nodes
    builder.add_node("understand_query", understand_query)
    builder.add_node("chat", chat)
    builder.add_node("rewrite_query", rewrite_query_node)
    builder.add_node("retrieve", retrieve)
    builder.add_node("build_facets", build_facets)
    builder.add_node("clarify", clarify)
    builder.add_node("final_answer", final_answer)
    builder.add_node("no_results", no_results)

    # 3. 定义固定 Edge
    builder.add_edge(START, "understand_query")
    builder.add_edge("rewrite_query", "retrieve")
    builder.add_edge("build_facets", "clarify")
    builder.add_edge("chat", END)
    builder.add_edge("final_answer", END)
    builder.add_edge("no_results", END)

    # 4. 定义 Conditional Edge
    builder.add_conditional_edges(
        "understand_query",
        route_query,
        {
            "chat": "chat",
            "rewrite_query": "rewrite_query",
        },
    )

    builder.add_conditional_edges(
        "retrieve",
        route_by_result_count,
        {
            "no_results": "no_results",
            "final_answer": "final_answer",
            "build_facets": "build_facets",
        },
    )

    builder.add_conditional_edges(
        "clarify",
        route_by_result_count,
        {
            "no_results": "no_results",
            "final_answer": "final_answer",
            "build_facets": "build_facets",
        },
    )

    # 5. InMemorySaver 保存本地多轮短期状态
    checkpointer = InMemorySaver()

    return builder.compile(
        checkpointer=checkpointer,
    )
