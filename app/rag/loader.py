from __future__ import annotations

import csv
from dataclasses import dataclass, field
from pathlib import Path
from typing import Mapping

from langchain_core.documents import Document

from app.core.paths import DEFAULT_CIRCUIT_DATA_PATH
from app.schemas.document import CircuitDocumentRecord


ID_COLUMN = "ID"
HIERARCHY_COLUMN = "层级路径"
TITLE_COLUMN = "关联文件名称"
REQUIRED_COLUMNS = {ID_COLUMN, HIERARCHY_COLUMN, TITLE_COLUMN}


@dataclass(frozen=True)
class LoadIssue:
    row_number: int
    message: str
    raw: Mapping[str, str | None]


@dataclass
class CircuitDocumentLoadResult:
    documents: list[Document] = field(default_factory=list)
    issues: list[LoadIssue] = field(default_factory=list)
    total_rows: int = 0

    @property
    def valid_rows(self) -> int:
        return len(self.documents)

    @property
    def invalid_rows(self) -> int:
        return len(self.issues)


def build_search_text(record: CircuitDocumentRecord) -> str:
    """Build the canonical text used by dense and sparse retrieval."""

    normalized_path = " ".join(record.hierarchy_segments)
    return (
        f"层级路径：{normalized_path}\n"
        f"关联文件名称：{record.title}"
    )


def record_to_document(record: CircuitDocumentRecord) -> Document:
    """Convert a normalized CSV record into one LangChain Document."""

    return Document(
        page_content=build_search_text(record),
        metadata={
            "doc_id": record.doc_id,
            "title": record.title,
            "hierarchy_path": record.hierarchy_path,
            "hierarchy_segments": record.hierarchy_segments,
            "hierarchy_depth": len(record.hierarchy_segments),
        },
    )


def _validate_headers(fieldnames: list[str] | None) -> None:
    if not fieldnames:
        raise ValueError("CSV 文件缺少表头")

    missing = REQUIRED_COLUMNS.difference(fieldnames)
    if missing:
        missing_text = ", ".join(sorted(missing))
        raise ValueError(f"CSV 缺少必要字段: {missing_text}")


def _parse_row(
    row: Mapping[str, str | None],
    row_number: int,
) -> tuple[Document | None, LoadIssue | None]:
    raw_id = (row.get(ID_COLUMN) or "").strip()
    hierarchy_path = (row.get(HIERARCHY_COLUMN) or "").strip()
    title = (row.get(TITLE_COLUMN) or "").strip()

    if not raw_id or not hierarchy_path or not title:
        return None, LoadIssue(
            row_number=row_number,
            message="ID、层级路径、关联文件名称均不能为空",
            raw=dict(row),
        )

    try:
        doc_id = int(raw_id)
    except ValueError:
        return None, LoadIssue(
            row_number=row_number,
            message=f"ID 不是有效整数: {raw_id}",
            raw=dict(row),
        )

    try:
        record = CircuitDocumentRecord(
            doc_id=doc_id,
            hierarchy_path=hierarchy_path,
            title=title,
        )
    except ValueError as exc:
        return None, LoadIssue(
            row_number=row_number,
            message=f"字段校验失败: {exc}",
            raw=dict(row),
        )

    return record_to_document(record), None


def load_circuit_documents(
    csv_path: str | Path = DEFAULT_CIRCUIT_DATA_PATH,
) -> CircuitDocumentLoadResult:
    """Load circuit-document rows as LangChain Documents.

    One CSV row maps to exactly one Document. Invalid rows are collected in
    the issues list instead of terminating the entire import.
    """

    path = Path(csv_path)
    if not path.is_file():
        raise FileNotFoundError(f"CSV 文件不存在: {path}")

    result = CircuitDocumentLoadResult()

    with path.open("r", encoding="utf-8-sig", newline="") as file:
        reader = csv.DictReader(file)
        _validate_headers(reader.fieldnames)

        for row_number, row in enumerate(reader, start=2):
            if not row or not any((value or "").strip() for value in row.values()):
                continue

            result.total_rows += 1
            document, issue = _parse_row(row, row_number)

            if issue is not None:
                result.issues.append(issue)
            elif document is not None:
                result.documents.append(document)

    return result
