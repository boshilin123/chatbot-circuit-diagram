from app.rag.filters import build_metadata_filter
from app.schemas.intent import SearchIntent


def test_build_metadata_filter_uses_structured_fields() -> None:
    intent = SearchIntent(
        brand="东风",
        model="天龙",
        ecu_type="EDC17C53",
        keywords=["针脚"],
    )

    filter_query = build_metadata_filter(intent)

    assert filter_query is not None
    assert 'hierarchy_path LIKE "%东风%"' in filter_query
    assert 'title LIKE "%天龙%"' in filter_query
    assert 'title LIKE "%EDC17C53%"' in filter_query
    assert "针脚" not in filter_query


def test_build_metadata_filter_returns_none_without_structured_fields() -> None:
    intent = SearchIntent(keywords=["针脚", "电脑板"])

    assert build_metadata_filter(intent) is None
