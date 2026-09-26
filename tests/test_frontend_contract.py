"""前端渲染的静态契约测试。

背景：`appendResult(document, ...)` 曾把形参命名为 `document`，遮蔽了浏览器全局
`document`，导致函数体里的 `document.createElement` 抛 TypeError，
**所有结果卡片都渲染不出来**（页面只剩一句"已为你找到 N 条…"）。

这里用静态检查守住这个坑：方法定义的形参里不允许出现 `document`。
"""

from __future__ import annotations

import re

from app.core.paths import PROJECT_ROOT

CHAT_JS = PROJECT_ROOT / "frontend" / "js" / "chat.js"

# 匹配方法/函数定义行，例如 "    appendResult(doc, index = 1) {"
DEFINITION_PATTERN = re.compile(
    r"^\s*(?:async\s+)?[A-Za-z_$][\w$]*\s*\(([^)]*)\)\s*\{",
)


def _parameter_names(line: str) -> list[str]:
    match = DEFINITION_PATTERN.match(line)
    if match is None:
        return []

    return [
        parameter.split("=")[0].strip()
        for parameter in match.group(1).split(",")
        if parameter.strip()
    ]


def test_chat_js_does_not_shadow_global_document() -> None:
    source = CHAT_JS.read_text(encoding="utf-8")
    offenders = [
        (number, line.strip())
        for number, line in enumerate(source.splitlines(), start=1)
        if "document" in _parameter_names(line)
    ]

    assert not offenders, (
        "以下函数的形参遮蔽了浏览器全局 document，会导致卡片渲染抛 "
        f"TypeError：{offenders}"
    )


def test_chat_js_renders_result_cards_with_text_content() -> None:
    """卡片取值必须用 textContent 注入，避免资料标题里的 HTML 被执行。"""

    source = CHAT_JS.read_text(encoding="utf-8")

    assert "createResultItem" in source
    # 结果卡片区域不应再出现把后端字段直接拼进 innerHTML 的写法
    assert "result-label\">文档ID：</span>${" not in source
    assert "result-label\">层级路径：</span>${" not in source
