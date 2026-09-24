from __future__ import annotations

from app.schemas.intent import SearchIntent


FILTER_FIELDS = (
    "brand",
    "model",
    "component",
    "ecu_type",
    "document_type",
)


def _escape_like_value(value: str) -> str:
    """转义 Milvus LIKE 表达式中的特殊字符。"""

    return (
        value
        .replace("\\", "\\\\")
        .replace('"', '\\"')
        .replace("%", "\\%")
        .replace("_", "\\_")
    )


def build_metadata_filter(intent: SearchIntent) -> str | None:
    """根据高置信度结构化意图构建 Milvus Metadata Filter。

    普通 keywords 不直接加入硬过滤，避免过度限制召回。
    """

    conditions: list[str] = []

    # 1. 只使用明确的结构化字段
    for field_name in FILTER_FIELDS:
        value = getattr(intent, field_name)
        if not value:
            continue

        value = _escape_like_value(value.strip())
        if not value:
            continue

        # 2. 当前数据的业务属性主要存在于 title / hierarchy_path 中
        conditions.append(
            "("
            f'hierarchy_path LIKE "%{value}%" '
            "OR "
            f'title LIKE "%{value}%"'
            ")"
        )

    # 3. 多个明确条件同时成立时使用 AND 收窄范围
    if not conditions:
        return None

    return " AND ".join(conditions)
