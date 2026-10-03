# 标准 CSV 记录模型：集中校验文档 ID、层级路径和标题，供 Loader 使用。

from pydantic import BaseModel, Field


class CircuitDocumentRecord(BaseModel):
    """Normalized representation of one circuit-document CSV row."""

    doc_id: int = Field(gt=0)
    hierarchy_path: str = Field(min_length=1)
    title: str = Field(min_length=1)

    # 把目录路径按 -> 拆分、去空白，供检索文本及层级深度计算复用。
    @property
    def hierarchy_segments(self) -> list[str]:
        return [
            segment.strip()
            for segment in self.hierarchy_path.split("->")
            if segment.strip()
        ]
