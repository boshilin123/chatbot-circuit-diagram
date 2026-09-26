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


def _typed_doc(doc_id: int, title: str, hierarchy_path: str) -> dict:
    return {
        "doc_id": doc_id,
        "title": title,
        "hierarchy_path": hierarchy_path,
        "content": "test",
        "retrieval_score": 0.5,
        "score": 1.0,
    }


def _typed_state() -> dict:
    """复刻真实检索里"路径只有两种、标题才有区分度"的场景。"""

    return {
        "candidate_documents": [
            _typed_doc(2332, "东风_天龙旗舰版_仪表电路图.DOCX", "电路图->仪表模块->东风->天龙"),
            _typed_doc(2333, "东风_天龙_天锦_仪表_电路图.DOCX", "电路图->仪表模块->东风->天龙"),
            _typed_doc(4105, "东风_天龙旗舰版_仪表电路图.DOCX", "整车->仪表模块->东风->天龙"),
            _typed_doc(4110, "东风_天龙_天锦_仪表_电路图.DOCX", "整车->仪表模块->东风->天龙"),
            _typed_doc(4107, "东风_天龙_VECU_针脚定义【四插头】", "整车->仪表模块->东风->天龙"),
            _typed_doc(4104, "东风_天龙D320_BCM_针脚定义【五插头】", "整车->仪表模块->东风->天龙"),
        ],
        "used_facets": [],
        "candidate_history": [],
        "clarification_round": 0,
    }


def test_build_facets_prefers_document_type_dimension() -> None:
    result = build_facets(_typed_state())

    assert result["current_facet"] == "document_type"
    assert "资料类型" in result["current_prompt"]

    option_text = " ".join(option["text"] for option in result["current_options"])
    assert "电路图" in option_text
    assert "针脚定义" in option_text


def test_build_facets_document_type_covers_every_candidate() -> None:
    state = _typed_state()
    result = build_facets(state)

    covered = {
        doc_id
        for doc_ids in result["option_groups"].values()
        for doc_id in doc_ids
    }
    expected = {
        doc["doc_id"]
        for doc in state["candidate_documents"]
    }

    assert covered == expected


def test_build_facets_fallback_labels_are_meaningful() -> None:
    """所有维度都失效时，选项标签必须是真实标题，而不是"第 N 组"。"""

    state = {
        "candidate_documents": [
            _typed_doc(index, f"资料{index}.DOCX", "电路图->ECU电路图")
            for index in range(1, 9)
        ],
        "used_facets": [],
        "candidate_history": [],
        "clarification_round": 0,
    }

    result = build_facets(state)

    option_text = " ".join(option["text"] for option in result["current_options"])
    assert "第 1 组" not in option_text
    assert "资料1" in option_text


def test_build_facets_respects_used_facets() -> None:
    """已问过的"资料类型"不应重复提问。"""

    state = _typed_state()
    state["used_facets"] = ["document_type"]

    result = build_facets(state)

    assert result["current_facet"] != "document_type"


def test_select_dimension_prefers_smaller_other_bucket(monkeypatch) -> None:
    """分组过多需要合并时，应选"其他"更小的维度，而不是分组数更多的维度。"""

    from app.graph.nodes import facets

    docs = [
        {"doc_id": index, "title": "x", "hierarchy_path": "a->b"}
        for index in range(1, 13)
    ]

    monkeypatch.setattr(
        facets,
        "_group_by_document_type",
        lambda docs: {"电路图": list(range(1, 13))},
    )

    level_groups = {
        # 7 组：[4,2,2,1,1,1,1] → 合并后最大 4
        3: {
            "品牌A": [1, 2, 3, 4],
            "品牌B": [5, 6],
            "品牌C": [7, 8],
            "品牌D": [9],
            "品牌E": [10],
            "品牌F": [11],
            "品牌G": [12],
        },
        # 12 组但全是单条 → 合并后"其他"=8，最大 8
        4: {f"型号{index}": [index] for index in range(1, 13)},
    }
    monkeypatch.setattr(
        facets,
        "_group_by_path_level",
        lambda docs, level: level_groups.get(level, {"全部": list(range(1, 13))}),
    )

    facet_key, _, groups = facets._select_dimension(
        docs,
        max_depth=5,
        used_facets=set(),
        max_options=5,
    )

    assert facet_key == "path_level_3"
    assert max(len(doc_ids) for _, doc_ids in groups) == 4
