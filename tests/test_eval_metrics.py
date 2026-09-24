from eval.metrics import (
    hit_at_k,
    mrr_at_k,
    percentile,
    recall_at_k,
)


def test_hit_at_k() -> None:
    assert hit_at_k([2], [1, 2, 3], 1) == 0.0
    assert hit_at_k([2], [1, 2, 3], 5) == 1.0


def test_recall_at_k_supports_multiple_ground_truth_ids() -> None:
    assert recall_at_k(
        [2, 4],
        [1, 2, 3, 4],
        3,
    ) == 0.5


def test_mrr_at_k_uses_first_relevant_rank() -> None:
    assert mrr_at_k(
        [3, 5],
        [1, 2, 3, 5],
        5,
    ) == 1 / 3


def test_percentile() -> None:
    assert percentile(
        [10, 20, 30],
        50,
    ) == 20
