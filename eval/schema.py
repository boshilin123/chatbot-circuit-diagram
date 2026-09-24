from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field


class EvalCase(BaseModel):
    """一条离线检索评估样本。"""

    query: str = Field(min_length=1)
    expected_ids: list[int] = Field(default_factory=list)
    source: str = "manual"
    label_status: Literal["pending", "verified"] = "pending"
    notes: str | None = None

    @property
    def is_labeled(self) -> bool:
        return (
            self.label_status == "verified"
            and bool(self.expected_ids)
        )


class RetrievalEvalRecord(BaseModel):
    """单个 Retriever 在一条 Query 上的结果。"""

    method: str
    query: str
    expected_ids: list[int]
    retrieved_ids: list[int]
    latency_ms: float
    hit_at_1: float
    hit_at_5: float
    recall_at_5: float
    mrr_at_5: float


class RetrievalSummary(BaseModel):
    """一个检索方法的汇总指标。"""

    method: str
    query_count: int
    hit_at_1: float
    hit_at_5: float
    recall_at_5: float
    mrr_at_5: float
    p50_latency_ms: float
    p95_latency_ms: float


class BusinessEvalSample(BaseModel):
    """LangGraph 业务规则评估样本。"""

    final_result_count: int = Field(ge=0)
    clarification_turns: int = Field(ge=0)
    llm_call_count: int = Field(ge=0)


class BusinessSummary(BaseModel):
    sample_count: int
    final_le_5_rate: float
    average_clarification_turns: float
    average_llm_calls: float
