from app.graph.nodes.clarify import apply_selection


def _state() -> dict:
    docs = [
        {
            "doc_id": index,
            "title": f"文档{index}",
            "hierarchy_path": "电路图->ECU",
            "content": "test",
            "retrieval_score": 0.5,
            "score": 1.0,
        }
        for index in range(1, 7)
    ]

    return {
        "candidate_documents": docs,
        "candidate_ids": list(range(1, 7)),
        "selected_filters": {},
        "used_facets": [],
        "clarification_round": 0,
        "current_facet": "path_level_3",
        "current_options": [
            {"id": 1, "text": "三一（3条）", "value": "facet:1"},
            {"id": 2, "text": "徐工（3条）", "value": "facet:2"},
        ],
        "option_groups": {
            "facet:1": [1, 2, 3],
            "facet:2": [4, 5, 6],
        },
        "candidate_history": [],
    }


def test_apply_selection_filters_candidates() -> None:
    result = apply_selection(
        _state(),
        "facet:1",
    )

    assert result["candidate_ids"] == [1, 2, 3]
    assert result["used_facets"] == ["path_level_3"]
    assert result["clarification_round"] == 1
    assert len(result["candidate_history"]) == 1


def test_apply_selection_can_go_back() -> None:
    first = apply_selection(
        _state(),
        "facet:1",
    )

    state = {
        **_state(),
        **first,
    }

    result = apply_selection(
        state,
        "__back__",
    )

    assert result["candidate_ids"] == [1, 2, 3, 4, 5, 6]
    assert result["used_facets"] == []
    assert result["clarification_round"] == 0
