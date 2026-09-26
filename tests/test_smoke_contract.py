from scripts.smoke_test_api import search_response_has_results


def test_search_response_rejects_business_error() -> None:
    payload = {"code": 0, "msg": "failed", "data": None}

    assert search_response_has_results(200, payload) is False


def test_search_response_rejects_empty_result() -> None:
    payload = {
        "code": 1,
        "msg": "success",
        "data": {"type": "result", "documents": []},
    }

    assert search_response_has_results(200, payload) is False


def test_search_response_accepts_nonempty_options_or_documents() -> None:
    options = {
        "code": 1,
        "data": {"type": "options", "options": [{"value": "brand:1"}]},
    }
    result = {
        "code": 1,
        "data": {"type": "result", "documents": [{"id": 8}]},
    }

    assert search_response_has_results(200, options) is True
    assert search_response_has_results(200, result) is True
