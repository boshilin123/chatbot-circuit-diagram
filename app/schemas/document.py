from pydantic import BaseModel, Field


class CircuitDocumentRecord(BaseModel):
    """Normalized representation of one circuit-document CSV row."""

    doc_id: int = Field(gt=0)
    hierarchy_path: str = Field(min_length=1)
    title: str = Field(min_length=1)

    @property
    def hierarchy_segments(self) -> list[str]:
        return [
            segment.strip()
            for segment in self.hierarchy_path.split("->")
            if segment.strip()
        ]
