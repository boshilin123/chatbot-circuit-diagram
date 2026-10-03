# 图编排入口：把意图理解、查询改写、检索、澄清和回答连接成确定的业务流程。

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


def build_circuit_graph(*, checkpointer=None):
    """Build the shared workflow for FastAPI or LangGraph Studio."""

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

    # compile 固化节点与边；保存运行状态的能力由传入 checkpointer 决定。
    return builder.compile(
        checkpointer=checkpointer,
    )


@lru_cache
def get_circuit_graph():
    """Build the FastAPI graph with process-local conversation state."""

    # 图实例与会话状态在本进程内复用，服务重启后内存会话丢失。
    return build_circuit_graph(checkpointer=InMemorySaver())


# Studio/Agent Server 由运行时提供 checkpointer；这里不绑定 Web 的内存存储。
# langgraph.json 引用 studio_graph，用于 Studio 图形展示与交互调试。
studio_graph = build_circuit_graph()
