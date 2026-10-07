"""Exercise the compiled graph and entry points with external services stubbed."""

from types import SimpleNamespace

import httpx
import pytest
from langchain_core.messages import AIMessage
from langgraph.checkpoint.memory import InMemorySaver

from app.graph import service, workflow
from app.graph.nodes import chat, retrieve, rewrite, understand
from app.main import app
from app.schemas.intent import SearchIntent
from app.schemas.search import RerankResult
from scripts import run_agent


@pytest.fixture
def graph_dependencies(monkeypatch):
    calls = []
    documents = []

    def parse(query):
        calls.append("understand")
        return SearchIntent(is_search_request=query != "hello")

    def rewrite_query(query):
        calls.append("rewrite")
        return "rewritten query"

    def search(query, **kwargs):
        assert query == "rewritten query"
        calls.append("retrieve")
        return documents.copy()

    def rerank(query, docs, **kwargs):
        calls.append("rerank")
        return docs

    class FakeChatModel:
        async def ainvoke(self, messages):
            calls.append("chat")
            return AIMessage(content="Hello!")

    monkeypatch.setattr(understand, "parse_search_intent", parse)
    monkeypatch.setattr(rewrite, "rewrite_query", rewrite_query)
    monkeypatch.setattr(retrieve, "hybrid_search", search)
    monkeypatch.setattr(retrieve, "cross_encoder_rerank", rerank)
    monkeypatch.setattr(chat, "get_chat_model", FakeChatModel)
    graph = workflow.build_circuit_graph(checkpointer=InMemorySaver())
    monkeypatch.setattr(service, "get_circuit_graph", lambda: graph)
    return SimpleNamespace(calls=calls, documents=documents)


def populate(dependencies, count):
    dependencies.documents[:] = [
        RerankResult(
            doc_id=index,
            title=f"Circuit diagram {index}",
            hierarchy_path=f"diagrams->ECU->brand{(index - 1) // 2}",
            content="test",
            retrieval_score=0.5,
            score=0.9,
        )
        for index in range(1, count + 1)
    ]


@pytest.mark.parametrize("count", [0, 1, 5])
async def test_graph_search_result_boundaries(graph_dependencies, count):
    populate(graph_dependencies, count)
    response = await service.run_search_workflow("search", "find circuit diagram")
    assert graph_dependencies.calls == ["understand", "rewrite", "retrieve", "rerank"]
    assert response.type == ("result" if count else "text")
    assert len(response.documents or []) == count
    assert [doc["id"] for doc in response.documents or []] == list(range(1, count + 1))


async def test_graph_clarifies_resumes_and_switches_without_retrieval(graph_dependencies):
    populate(graph_dependencies, 6)
    response = await service.run_search_workflow("clarify", "find circuit diagram")
    assert response.type == "options"
    assert len(response.options) == 3
    results = []
    for option in response.options[:2]:
        result = await service.resume_search_workflow("clarify", option["value"])
        assert result.type == "result"
        assert len(result.documents) == 2
        results.append({doc["id"] for doc in result.documents})
    assert results[0].isdisjoint(results[1])
    assert graph_dependencies.calls == ["understand", "rewrite", "retrieve", "rerank"]


async def test_graph_greeting_skips_retrieval(graph_dependencies):
    response = await service.run_search_workflow("greeting", "hello")
    assert response.type == "text"
    assert response.content == "Hello!"
    assert graph_dependencies.calls == ["understand", "chat"]


async def test_http_chat_and_selection_use_graph(graph_dependencies):
    populate(graph_dependencies, 6)
    async with httpx.AsyncClient(
        transport=httpx.ASGITransport(app=app), base_url="http://test"
    ) as client:
        response = await client.post(
            "/api/chat", json={"sessionId": "api", "message": "find circuit diagram"}
        )
        assert response.status_code == 200
        payload = response.json()
        assert payload["code"] == 1
        assert payload["data"]["type"] == "options"
        response = await client.post(
            "/api/select",
            json={
                "sessionId": "api",
                "optionId": payload["data"]["options"][0]["id"],
                "optionValue": payload["data"]["options"][0]["value"],
            },
        )
        assert response.status_code == 200
        payload = response.json()
        assert payload["code"] == 1
        assert payload["data"]["type"] == "result"
        assert len(payload["data"]["documents"]) == 2
        assert payload["data"]["document"] == payload["data"]["documents"][0]
    assert graph_dependencies.calls.count("retrieve") == 1


async def test_cli_completes_graph_clarification(graph_dependencies, monkeypatch, capsys):
    populate(graph_dependencies, 6)
    monkeypatch.setattr("sys.argv", ["run_agent", "find circuit diagram"])
    choices = iter(["invalid", "0", "999", "1"])
    monkeypatch.setattr("builtins.input", lambda prompt: next(choices))
    await run_agent.async_main()
    output = capsys.readouterr().out
    assert "Circuit diagram" in output
    assert "diagrams->ECU->brand" in output
    assert graph_dependencies.calls.count("retrieve") == 1


async def test_cli_blank_message_skips_graph(graph_dependencies, monkeypatch):
    monkeypatch.setattr("sys.argv", ["run_agent", "   "])
    with pytest.raises(SystemExit) as exc:
        await run_agent.async_main()
    assert exc.value.code == 2
    assert graph_dependencies.calls == []
