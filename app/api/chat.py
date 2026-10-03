# HTTP 接口适配层：校验请求、调用工作流，再转换为前端约定的响应。

import logging

from fastapi import APIRouter

from app.graph.service import resume_search_workflow, run_search_workflow
from app.schemas.chat import ApiResult, ChatRequest, ChatResponseData, SelectRequest

logger = logging.getLogger(__name__)
router = APIRouter()


# 把 GraphResponse 转为前端聊天数据，保留旧版单文档字段兼容性。
def _to_chat_response(response) -> ChatResponseData:
    documents = response.documents or []

    return ChatResponseData(
        type=response.type,
        content=response.content,
        options=response.options,
        document=documents[0] if documents else None,
        documents=documents or None,
    )


# 处理 /api/chat，用 sessionId 标识会话并启动一轮新问题。
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


# 处理 /api/select，将用户点选的 optionValue 交给工作流恢复执行。
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
