import logging

from fastapi import APIRouter
from langchain_core.messages import HumanMessage, SystemMessage

from app.core.model import get_chat_model
from app.schemas.chat import ApiResult, ChatRequest, ChatResponseData, SelectRequest

logger = logging.getLogger(__name__)
router = APIRouter()

SYSTEM_PROMPT = """你是车辆电路图资料导航助手。
当前重构阶段只负责基础对话能力。后续检索必须通过专用 RAG Retriever 完成。
不要虚构电路图 ID、车型、ECU 型号或不存在的检索结果。
"""


@router.post("/chat", response_model=ApiResult)
async def chat(request: ChatRequest) -> ApiResult:
    message = request.message.strip()
    if not message:
        return ApiResult.error("消息内容不能为空")

    try:
        model = get_chat_model()
        response = await model.ainvoke(
            [
                SystemMessage(content=SYSTEM_PROMPT),
                HumanMessage(content=message),
            ]
        )
        content = response.content if isinstance(response.content, str) else str(response.content)
        return ApiResult.success(ChatResponseData(type="text", content=content))
    except RuntimeError as exc:
        logger.warning("Model configuration error: %s", exc)
        return ApiResult.error(str(exc))
    except Exception:
        logger.exception("LangChain model invocation failed")
        return ApiResult.error("模型调用失败，请检查本地配置和网络连接")


@router.post("/select", response_model=ApiResult)
async def select(_: SelectRequest) -> ApiResult:
    return ApiResult.error("多轮选择题将在 LangGraph 阶段启用")
