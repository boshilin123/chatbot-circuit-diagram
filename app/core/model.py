from functools import lru_cache

from langchain.chat_models import init_chat_model
from langchain_core.language_models.chat_models import BaseChatModel

from app.core.config import get_settings


@lru_cache
def get_chat_model() -> BaseChatModel:
    """初始化项目使用的大语言模型。"""

    settings = get_settings()

    if settings.deepseek_api_key is None:
        raise RuntimeError(
            "DEEPSEEK_API_KEY is not configured. Copy .env.example to .env "
            "and set a newly generated key."
        )

    # 1. 使用课程中的 init_chat_model 统一初始化模型
    model = init_chat_model(
        model=settings.deepseek_model,
        model_provider="deepseek",
        api_key=settings.deepseek_api_key.get_secret_value(),
        temperature=0.1,
        timeout=30,
        max_retries=2,
    )

    # 2. 返回 LangChain 标准 ChatModel
    return model
