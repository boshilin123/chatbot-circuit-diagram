from langchain_core.messages import AIMessage

from app.rag.query_rewriter import rewrite_query


class FakeChatModel:
    def invoke(self, input: str) -> AIMessage:
        assert "只输出关键词不要解释" in input
        assert "EDC17C53" in input
        return AIMessage(content="东风 天龙 EDC17C53 ECU 针脚")


def test_rewrite_query_preserves_vehicle_codes() -> None:
    rewritten = rewrite_query(
        "帮我找东风天龙EDC17C53电脑板针脚图",
        model=FakeChatModel(),
    )

    assert rewritten == "东风 天龙 EDC17C53 ECU 针脚"


def test_rewrite_query_skips_blank_query() -> None:
    assert rewrite_query("   ", model=FakeChatModel()) == ""
