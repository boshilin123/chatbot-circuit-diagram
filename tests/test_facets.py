from app.graph.nodes.facets import build_facets


def _doc(doc_id: int, brand: str, model: str) -> dict:
    return {
        "doc_id": doc_id,
        "title": f"{brand}_{model}_电路图",
        "hierarchy_path": f"电路图->ECU电路图->工程机械->{brand}->{model}",
        "content": "test",
        "retrieval_score": 0.5,
        "score": 1.0,
    }


def test_build_facets_uses_real_hierarchy_values() -> None:
    state = {
        "candidate_documents": [
            _doc(1, "三一", "SY60"),
            _doc(2, "三一", "SY75"),
            _doc(3, "徐工", "XE135G"),
            _doc(4, "徐工", "XE150"),
            _doc(5, "卡特", "320D"),
            _doc(6, "卡特", "336D"),
        ],
        "used_facets": [],
        "candidate_history": [],
        "clarification_round": 0,
    }

    result = build_facets(state)

    assert result["current_facet"] == "path_level_3"
    assert len(result["current_options"]) == 3
    option_text = " ".join(
        option["text"]
        for option in result["current_options"]
    )
    assert "三一" in option_text
    assert "徐工" in option_text
    assert "卡特" in option_text


def test_build_facets_adds_back_without_exceeding_five_options() -> None:
    state = {
        "candidate_documents": [
            _doc(index, f"品牌{index}", f"型号{index}")
            for index in range(1, 9)
        ],
        "used_facets": [],
        "candidate_history": [{"candidate_documents": []}],
        "clarification_round": 1,
    }

    result = build_facets(state)

    assert len(result["current_options"]) <= 5
    assert result["current_options"][-1]["value"] == "__back__"
