from pydantic import SecretStr

from app.core import model
from app.core.config import Settings


def test_get_chat_model_rejects_empty_api_key(
    monkeypatch,
) -> None:
    settings = Settings(deepseek_api_key=SecretStr(""))
    monkeypatch.setattr(model, "get_settings", lambda: settings)
    model.get_chat_model.cache_clear()

    try:
        try:
            model.get_chat_model()
        except RuntimeError as exc:
            assert "DEEPSEEK_API_KEY is not configured" in str(exc)
        else:
            raise AssertionError("Expected an empty API key to be rejected")
    finally:
        model.get_chat_model.cache_clear()
