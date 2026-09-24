from app.graph.nodes.retrieve import route_by_result_count
from app.graph.nodes.understand import route_query


def test_route_query_sends_search_to_rewrite() -> None:
    state = {
        "intent": {
            "is_search_request": True,
        }
    }

    assert route_query(state) == "rewrite_query"


def test_route_query_sends_chitchat_to_chat() -> None:
    state = {
        "intent": {
            "is_search_request": False,
        }
    }

    assert route_query(state) == "chat"


def test_route_by_result_count_enforces_final_limit() -> None:
    assert route_by_result_count(
        {"candidate_documents": []}
    ) == "no_results"

    assert route_by_result_count(
        {"candidate_documents": [{}] * 5}
    ) == "final_answer"

    assert route_by_result_count(
        {"candidate_documents": [{}] * 6}
    ) == "build_facets"
