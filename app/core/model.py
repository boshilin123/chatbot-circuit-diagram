from functools import lru_cache

from langchain_deepseek import ChatDeepSeek

from app.core.config import get_settings


@lru_cache
def get_chat_model() -> ChatDeepSeek:
    settings = get_settings()

    if settings.deepseek_api_key is None:
        raise RuntimeError(
            "DEEPSEEK_API_KEY is not configured. Copy .env.example to .env "
            "and set a newly generated key."
        )

    return ChatDeepSeek(
        model=settings.deepseek_model,
        api_key=settings.deepseek_api_key.get_secret_value(),
        api_base=settings.deepseek_api_base,
        temperature=0.1,
        timeout=30,
        max_retries=2,
    )
