from app.rag.intent_parser import parse_search_intent
from app.schemas.intent import SearchIntent


class FakeStructuredModel:
    def invoke(self, input: str) -> SearchIntent:
        assert "不猜测不存在" in input
        return SearchIntent(
            brand="东风",
            model="天龙",
            ecu_type="EDC17C53",
            document_type="针脚定义",
        )


class FakeChatModel:
    def with_structured_output(
        self,
        schema: type[SearchIntent],
        *,
        method: str,
    ) -> FakeStructuredModel:
        assert schema is SearchIntent
        assert method == "json_mode"
        return FakeStructuredModel()


class EmptyStructuredModel:
    def invoke(self, input: str) -> None:
        assert input


class EmptyChatModel:
    def with_structured_output(
        self,
        schema: type[SearchIntent],
        *,
        method: str,
    ) -> EmptyStructuredModel:
        assert schema is SearchIntent
        assert method == "json_mode"
        return EmptyStructuredModel()


def test_parse_search_intent_returns_pydantic_model() -> None:
    intent = parse_search_intent(
        "找东风天龙EDC17C53针脚定义",
        model=FakeChatModel(),
    )

    assert intent.brand == "东风"
    assert intent.model == "天龙"
    assert intent.ecu_type == "EDC17C53"
    assert intent.document_type == "针脚定义"


def test_parse_search_intent_skips_blank_query() -> None:
    assert parse_search_intent("   ", model=FakeChatModel()) == SearchIntent()


def test_parse_search_intent_falls_back_to_search_on_empty_model_output() -> None:
    intent = parse_search_intent(
        "三一挖掘机针脚定义",
        model=EmptyChatModel(),
    )

    assert intent.is_search_request is True
    assert intent.keywords == ["三一挖掘机针脚定义"]


def test_parse_search_intent_keeps_greeting_route_on_empty_model_output() -> None:
    intent = parse_search_intent("你好！", model=EmptyChatModel())

    assert intent.is_search_request is False
    assert intent.keywords == []
