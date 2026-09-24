from pathlib import Path

from eval.benchmark import labeled_cases, load_benchmark


def test_load_benchmark_and_filter_verified_cases(
    tmp_path: Path,
) -> None:
    path = tmp_path / "benchmark.jsonl"
    path.write_text(
        (
            '{"query":"A","expected_ids":[1],"label_status":"verified"}\n'
            '{"query":"B","expected_ids":[],"label_status":"pending"}\n'
        ),
        encoding="utf-8",
    )

    cases = load_benchmark(path)
    verified = labeled_cases(cases)

    assert len(cases) == 2
    assert len(verified) == 1
    assert verified[0].query == "A"
