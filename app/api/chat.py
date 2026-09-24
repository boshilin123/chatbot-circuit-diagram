import logging

from fastapi import APIRouter

from app.graph.service import resume_search_workflow, run_search_workflow
from app.schemas.chat import ApiResult, ChatRequest, ChatResponseData, SelectRequest

logger = logging.getLogger(__name__)
router = APIRouter()


def _to_chat_response(response) -> ChatResponseData:
    documents = response.documents or []

    return ChatResponseData(
        type=response.type,
        content=response.content,
        options=response.options,
        document=documents[0] if documents else None,
        documents=documents or None,
    )


@router.post("/chat", response_model=ApiResult)
async def chat(request: ChatRequest) -> ApiResult:
    message = request.message.strip()
    if not message:
        return ApiResult.error("消息内容不能为空")

    try:
        # Phase 8：Web 主流程由 LangGraph 接管
        response = await run_search_workflow(
            request.sessionId,
            message,
        )
        return ApiResult.success(
            _to_chat_response(response)
        )
    except RuntimeError as exc:
        logger.warning("Graph configuration error: %s", exc)
        return ApiResult.error(str(exc))
    except Exception:
        logger.exception("LangGraph invocation failed")
        return ApiResult.error("检索工作流执行失败，请检查本地配置和依赖服务")


@router.post("/select", response_model=ApiResult)
async def select(request: SelectRequest) -> ApiResult:
    try:
        # Command(resume=optionValue) 恢复 interrupt
        response = await resume_search_workflow(
            request.sessionId,
            request.optionValue,
        )
        return ApiResult.success(
            _to_chat_response(response)
        )
    except ValueError as exc:
        return ApiResult.error(str(exc))
    except Exception:
        logger.exception("LangGraph resume failed")
        return ApiResult.error("选择处理失败，请重新发起查询")
