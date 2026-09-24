from pydantic import BaseModel, Field


class SearchResult(BaseModel):
    doc_id: int
    title: str
    hierarchy_path: str
    content: str
    score: float


class DenseSearchResult(SearchResult):
    score: float = Field(
        description="Raw cosine similarity score returned by Milvus."
    )


class SparseSearchResult(SearchResult):
    score: float = Field(
        description="Raw BM25 score returned by Milvus."
    )


class HybridSearchResult(SearchResult):
    score: float = Field(
        description="Fusion score returned by the Milvus ranker."
    )
