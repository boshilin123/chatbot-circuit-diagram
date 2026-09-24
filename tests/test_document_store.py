from app.rag.document_store import get_document_index


def test_document_index_uses_doc_id_as_key() -> None:
    index = get_document_index()

    if not index:
        return

    document_id, doc = next(iter(index.items()))

    assert document_id == int(doc.metadata["doc_id"])
    assert "title" in doc.metadata
    assert "hierarchy_path" in doc.metadata
