from pathlib import Path

import pytest

from app.rag.loader import load_circuit_documents


def write_csv(path: Path, content: str) -> Path:
    path.write_text(content, encoding="utf-8")
    return path


def test_loader_maps_one_row_to_one_document(tmp_path: Path) -> None:
    csv_path = write_csv(
        tmp_path / "circuit.csv",
        (
            "ID,层级路径,关联文件名称\n"
            "1,电路图->ECU电路图->工程机械->三一->SY60,"
            "三一_SY60挖掘机_仪表显示器针脚定义\n"
        ),
    )

    result = load_circuit_documents(csv_path)

    assert result.total_rows == 1
    assert result.valid_rows == 1
    assert result.invalid_rows == 0

    document = result.documents[0]
    assert document.metadata["doc_id"] == 1
    assert document.metadata["title"] == "三一_SY60挖掘机_仪表显示器针脚定义"
    assert document.metadata["hierarchy_path"].endswith("三一->SY60")
    assert document.metadata["hierarchy_segments"][-2:] == ["三一", "SY60"]
    assert "层级路径：电路图 ECU电路图 工程机械 三一 SY60" in document.page_content
    assert "关联文件名称：三一_SY60挖掘机_仪表显示器针脚定义" in document.page_content


def test_loader_collects_invalid_rows_without_stopping(tmp_path: Path) -> None:
    csv_path = write_csv(
        tmp_path / "circuit.csv",
        (
            "ID,层级路径,关联文件名称\n"
            "bad-id,电路图->ECU电路图,错误记录\n"
            "2,电路图->整车电路图,有效记录\n"
        ),
    )

    result = load_circuit_documents(csv_path)

    assert result.total_rows == 2
    assert result.valid_rows == 1
    assert result.invalid_rows == 1
    assert result.documents[0].metadata["doc_id"] == 2
    assert result.issues[0].row_number == 2


def test_loader_rejects_missing_required_headers(tmp_path: Path) -> None:
    csv_path = write_csv(
        tmp_path / "circuit.csv",
        "ID,层级路径,文件名称\n1,电路图->ECU电路图,错误表头\n",
    )

    with pytest.raises(ValueError, match="关联文件名称"):
        load_circuit_documents(csv_path)
