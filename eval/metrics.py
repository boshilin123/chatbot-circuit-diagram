from __future__ import annotations

from collections.abc import Sequence

from eval.schema import BusinessEvalSample, BusinessSummary


def hit_at_k(
    expected_ids: Sequence[int],
    retrieved_ids: Sequence[int],
    k: int,
) -> float:
    """TopK 中只要命中一个相关文档即记为 1。"""

    if k <= 0:
        raise ValueError("k 必须大于 0")

    expected = set(expected_ids)
    if not expected:
        return 0.0

    return float(
        bool(expected.intersection(retrieved_ids[:k]))
    )


def recall_at_k(
    expected_ids: Sequence[int],
    retrieved_ids: Sequence[int],
    k: int,
) -> float:
    """计算 TopK 找回了多少 Ground Truth。"""

    if k <= 0:
        raise ValueError("k 必须大于 0")

    expected = set(expected_ids)
    if not expected:
        return 0.0

    retrieved = set(retrieved_ids[:k])
    return len(expected.intersection(retrieved)) / len(expected)


def mrr_at_k(
    expected_ids: Sequence[int],
    retrieved_ids: Sequence[int],
    k: int,
) -> float:
    """计算第一个相关结果的 Reciprocal Rank。"""

    if k <= 0:
        raise ValueError("k 必须大于 0")

    expected = set(expected_ids)
    if not expected:
        return 0.0

    for rank, doc_id in enumerate(
        retrieved_ids[:k],
        start=1,
    ):
        if doc_id in expected:
            return 1.0 / rank

    return 0.0


def percentile(
    values: Sequence[float],
    p: float,
) -> float:
    """使用线性插值计算 P50 / P95 等百分位。"""

    if not values:
        return 0.0

    if not 0 <= p <= 100:
        raise ValueError("p 必须位于 0～100")

    values = sorted(float(value) for value in values)
    if len(values) == 1:
        return values[0]

    position = (len(values) - 1) * p / 100
    lower = int(position)
    upper = min(lower + 1, len(values) - 1)
    weight = position - lower

    return (
        values[lower] * (1 - weight)
        + values[upper] * weight
    )


def summarize_business_metrics(
    samples: Sequence[BusinessEvalSample],
) -> BusinessSummary:
    """汇总 LangGraph 业务指标。"""

    if not samples:
        return BusinessSummary(
            sample_count=0,
            final_le_5_rate=0.0,
            average_clarification_turns=0.0,
            average_llm_calls=0.0,
        )

    # 1. 最终结果 <=5 比例
    final_le_5 = sum(
        sample.final_result_count <= 5
        for sample in samples
    )

    # 2. 平均澄清轮数
    clarification_turns = sum(
        sample.clarification_turns
        for sample in samples
    )

    # 3. 平均 LLM 调用次数
    llm_calls = sum(
        sample.llm_call_count
        for sample in samples
    )

    count = len(samples)

    return BusinessSummary(
        sample_count=count,
        final_le_5_rate=final_le_5 / count,
        average_clarification_turns=clarification_turns / count,
        average_llm_calls=llm_calls / count,
    )
