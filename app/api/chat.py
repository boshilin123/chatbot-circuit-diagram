import logging

from fastapi import APIRouter

from app.agents.circuit_agent import run_circuit_agent
from app.schemas.chat import ApiResult, ChatRequest, ChatResponseData, SelectRequest

logger = logging.getLogger(__name__)
router = APIRouter()


@router.post("/chat", response_model=ApiResult)
async def chat(request: ChatRequest) -> ApiResult:
    message = request.message.strip()
    if not message:
        return ApiResult.error("消息内容不能为空")

    try:
        # Phase 7：由 LangChain Agent 决定是否调用检索工具
        content = await run_circuit_agent(message)

        if not content:
            return ApiResult.error("Agent 未返回有效内容")

        return ApiResult.success(
            ChatResponseData(
                type="text",
                content=content,
            )
        )
    except RuntimeError as exc:
        logger.warning("Agent configuration error: %s", exc)
        return ApiResult.error(str(exc))
    except Exception:
        logger.exception("LangChain agent invocation failed")
        return ApiResult.error("Agent 调用失败，请检查本地配置和依赖服务")


@router.post("/select", response_model=ApiResult)
async def select(_: SelectRequest) -> ApiResult:
    return ApiResult.error("多轮选择题将在 LangGraph 阶段启用")
