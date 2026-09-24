from __future__ import annotations

import json
from pathlib import Path

from eval.schema import EvalCase


def load_benchmark(path: str | Path) -> list[EvalCase]:
    """读取 JSONL Benchmark。"""

    path = Path(path)
    cases: list[EvalCase] = []

    with path.open("r", encoding="utf-8") as file:
        for line_number, line in enumerate(file, start=1):
            line = line.strip()
            if not line:
                continue

            try:
                payload = json.loads(line)
                cases.append(EvalCase.model_validate(payload))
            except Exception as exc:
                raise ValueError(
                    f"Benchmark 第 {line_number} 行格式错误: {exc}"
                ) from exc

    return cases


def save_benchmark(
    cases: list[EvalCase],
    path: str | Path,
) -> None:
    """写入 JSONL Benchmark。"""

    path = Path(path)
    path.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    with path.open("w", encoding="utf-8") as file:
        for case in cases:
            file.write(
                case.model_dump_json()
                + "\n"
            )


def labeled_cases(
    cases: list[EvalCase],
) -> list[EvalCase]:
    """只返回已经人工确认 Ground Truth 的样本。"""

    return [
        case
        for case in cases
        if case.is_labeled
    ]
