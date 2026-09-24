from pydantic import BaseModel, Field


class DenseSearchResult(BaseModel):
    doc_id: int
    title: str
    hierarchy_path: str
    content: str
    score: float = Field(description="Raw cosine similarity score returned by Milvus.")
