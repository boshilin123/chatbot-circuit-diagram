"""澄清 resume 的可恢复性判断测试。

覆盖三类点击：首次选择、同一轮改选、跨轮次回头改选上一轮的选项。
关键点：不能直接 resume 旧的 interrupt checkpoint（LangGraph 会重放旧 resume 值），
必须先按选项值找到对应轮次，从它前一个 checkpoint 重新生成 interrupt。
"""

from __future__ import annotations

from typing import Any, NamedTuple

from app.graph.service import (
    _has_pending_interrupt,
    _prepare_resume,
    _snapshot_accepts_option,
)


class FakeTask(NamedTuple):
    name: str
    interrupts: tuple = ()


class FakeSnapshot(NamedTuple):
    interrupts: tuple = ()
    tasks: tuple = ()
    values: dict | None = None
    config: dict | None = None
    parent_config: dict | None = None


class FakeGraph:
    def __init__(self, latest: FakeSnapshot, history: list[FakeSnapshot]) -> None:
        self.latest = latest
        self.history = history
        self.calls: list[tuple[Any, dict]] = []

    async def aget_state(self, config: dict) -> FakeSnapshot:
        return self.latest

    async def aget_state_history(self, config: dict):
        for item in self.history:
            yield item

    async def ainvoke(self, input: Any, config: dict) -> dict:
        self.calls.append((input, config))
        return {}


def _pending(values: dict | None = None, parent: dict | None = None) -> FakeSnapshot:
    return FakeSnapshot(
        tasks=(FakeTask("clarify", ("x",)),),
        values=values,
        parent_config=parent,
    )


def test_has_pending_interrupt_detects_snapshot_and_task_level() -> None:
    assert _has_pending_interrupt(FakeSnapshot()) is False
    assert _has_pending_interrupt(FakeSnapshot(interrupts=("x",))) is True
    assert _has_pending_interrupt(
        FakeSnapshot(tasks=(FakeTask("clarify", ("x",)),))
    ) is True
    assert _has_pending_interrupt(
        FakeSnapshot(tasks=(FakeTask("clarify"),))
    ) is False


def test_snapshot_accepts_option_reads_option_groups() -> None:
    snapshot = _pending(values={"option_groups": {"path_level_2:2": [7, 8]}})

    assert _snapshot_accepts_option(snapshot, "path_level_2:2") is True
    assert _snapshot_accepts_option(snapshot, "path_level_2:3") is False
    assert _snapshot_accepts_option(FakeSnapshot(), "path_level_2:2") is False


def test_snapshot_accepts_back_option_only_with_history() -> None:
    with_history = _pending(values={"candidate_history": [{"candidate_ids": []}]})
    without_history = _pending(values={"candidate_history": []})

    assert _snapshot_accepts_option(with_history, "__back__") is True
    assert _snapshot_accepts_option(without_history, "__back__") is False


async def test_prepare_resume_rebuilds_interrupt_before_first_click() -> None:
    """首次点击也走"重建"路径，避免后续任何一次异常把旧 resume 值留在 checkpoint 里。"""

    parent = {"configurable": {"checkpoint_id": "clarify-parent"}}
    graph = FakeGraph(
        latest=_pending(
            values={"option_groups": {"document_type:1": [1, 2]}},
            parent=parent,
        ),
        history=[],
    )

    assert await _prepare_resume(
        graph,
        {"configurable": {"thread_id": "t"}},
        "document_type:1",
    ) is True
    assert graph.calls == [(None, parent)]


async def test_prepare_resume_forks_when_graph_finished() -> None:
    parent = {"configurable": {"checkpoint_id": "parent"}}
    graph = FakeGraph(
        latest=FakeSnapshot(),
        history=[
            FakeSnapshot(),
            _pending(
                values={"option_groups": {"document_type:2": [3, 4]}},
                parent=parent,
            ),
        ],
    )

    assert await _prepare_resume(
        graph,
        {"configurable": {"thread_id": "t"}},
        "document_type:2",
    ) is True
    # 必须从 clarify 之前的 checkpoint 重新执行，且输入为 None（纯重算，不重新检索）
    assert graph.calls == [(None, parent)]


async def test_prepare_resume_finds_older_round_for_cross_round_switch() -> None:
    """跨轮次改选：当前 interrupt 没有这一项，就回溯到提供过它的那一轮。"""

    older_parent = {"configurable": {"checkpoint_id": "round1-parent"}}
    graph = FakeGraph(
        latest=_pending(values={"option_groups": {"path_level_3:1": [1, 2]}}),
        history=[
            _pending(values={"option_groups": {"path_level_3:1": [1, 2]}}),
            _pending(
                values={"option_groups": {"path_level_2:2": [3, 4]}},
                parent=older_parent,
            ),
        ],
    )

    assert await _prepare_resume(
        graph,
        {"configurable": {"thread_id": "t"}},
        "path_level_2:2",
    ) is True
    assert graph.calls == [(None, older_parent)]


async def test_prepare_resume_returns_false_without_any_interrupt() -> None:
    graph = FakeGraph(latest=FakeSnapshot(), history=[FakeSnapshot()])

    assert await _prepare_resume(
        graph,
        {"configurable": {"thread_id": "t"}},
        "document_type:1",
    ) is False
    assert graph.calls == []


async def test_prepare_resume_lets_graph_reject_unknown_option() -> None:
    """当前 interrupt 待处理但不含该选项（例如非法值）：不 fork，交给 Graph 报精确错误。"""

    graph = FakeGraph(
        latest=_pending(values={"option_groups": {"document_type:1": [1]}}),
        history=[
            _pending(
                values={"option_groups": {"document_type:1": [1]}},
                parent={"configurable": {"checkpoint_id": "p"}},
            )
        ],
    )

    assert await _prepare_resume(
        graph,
        {"configurable": {"thread_id": "t"}},
        "not_a_real_option",
    ) is True
    assert graph.calls == []


async def test_prepare_resume_returns_false_when_parent_config_missing() -> None:
    """历史里匹配的 interrupt 没有父 checkpoint 且当前无待处理项：无法恢复。"""

    graph = FakeGraph(
        latest=FakeSnapshot(),
        history=[
            _pending(values={"option_groups": {"document_type:1": [1]}}, parent=None),
        ],
    )

    assert await _prepare_resume(
        graph,
        {"configurable": {"thread_id": "t"}},
        "document_type:1",
    ) is False
    assert graph.calls == []
