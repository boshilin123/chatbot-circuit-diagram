# 后端应用入口：创建 FastAPI、注册 API 路由，并托管聊天页面的静态资源。

from pathlib import Path

from fastapi import FastAPI
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles

from app.api.chat import router as chat_router
from app.core.config import get_settings

# 应用启动时读取配置并创建 FastAPI；耗时的模型加载仍在各工厂首次调用时发生。
settings = get_settings()
app = FastAPI(title=settings.app_name, version=settings.app_version)

# 统一加 /api 前缀：router 内的 /chat 和 /select 对外成为 /api/chat 和 /api/select。
app.include_router(chat_router, prefix="/api")

ROOT_DIR = Path(__file__).resolve().parent.parent
FRONTEND_DIR = ROOT_DIR / "frontend"

# 静态目录存在才挂载 CSS/JS，让同一 FastAPI 服务同时提供页面与聊天接口。
if FRONTEND_DIR.exists():
    css_dir = FRONTEND_DIR / "css"
    js_dir = FRONTEND_DIR / "js"

    if css_dir.exists():
        app.mount("/css", StaticFiles(directory=css_dir), name="css")
    if js_dir.exists():
        app.mount("/js", StaticFiles(directory=js_dir), name="js")


# 返回服务存活状态及版本，供前端或测试快速确认 FastAPI 已启动。
@app.get("/api/health")
async def health() -> dict[str, str]:
    return {"status": "ok", "version": settings.app_version}


# 响应首页请求，将 frontend/index.html 作为文件发送给浏览器。
@app.get("/", include_in_schema=False)
async def index() -> FileResponse:
    return FileResponse(FRONTEND_DIR / "index.html")
