# Intelligent Circuit Diagram Retrieval Agent

> 基于 LangChain、LangGraph 与 Hybrid RAG 的车辆电路图智能检索 Agent。

当前仓库正在从旧版 Java / Spring Boot 实现迁移到 Python AI Agent 架构。旧版代码已冻结在 `legacy-java` 分支。

## 当前阶段

已完成：

- Phase 0：旧版分支冻结与主分支清理
- Python 项目骨架
- FastAPI
- LangChain DeepSeek 模型接入
- 旧聊天前端迁移到 `frontend/`
- 原始数据迁移到 `data/`
- `/api/health`
- `/api/chat` 基础模型调用

尚未实现：

- Milvus
- Dense Retrieval
- BM25
- Hybrid Retrieval
- Reranker
- Structured Output / Query Rewrite
- Agent Tools
- LangGraph 多轮澄清
- Retrieval Benchmark

完整实施路线见 [`LANGCHAIN_RAG_REFACTOR_PLAN.md`](./LANGCHAIN_RAG_REFACTOR_PLAN.md)。

## 本地启动

### 1. Python 环境

推荐 Python 3.11 或 3.12。

```bash
python -m venv .venv
```

Windows：

```bash
.venv\Scripts\activate
```

Linux / macOS：

```bash
source .venv/bin/activate
```

### 2. 安装依赖

```bash
pip install -e ".[dev]"
```

### 3. 配置 DeepSeek

Windows：

```bash
copy .env.example .env
```

Linux / macOS：

```bash
cp .env.example .env
```

然后在 `.env` 中填写你新生成的 DeepSeek Key。

> 不要把 `.env` 或任何真实 API Key 提交到 Git。

### 4. 启动 FastAPI

```bash
uvicorn app.main:app --reload
```

打开：

```text
http://127.0.0.1:8000
```

健康检查：

```text
http://127.0.0.1:8000/api/health
```

## 当前目录

```text
.
├── app/
│   ├── api/
│   ├── core/
│   ├── rag/
│   ├── schemas/
│   └── main.py
├── data/
│   ├── circuit-data.csv
│   └── keywords.txt
├── frontend/
│   ├── css/
│   ├── js/
│   └── index.html
├── scripts/
│   └── ingest.py
├── tests/
│   ├── test_health.py
│   └── test_loader.py
├── .env.example
├── pyproject.toml
└── LANGCHAIN_RAG_REFACTOR_PLAN.md
```

## 数据导入设计

当前采用：

```text
1 CSV Row = 1 LangChain Document
```

真实 CSV 表头为：

```text
ID / 层级路径 / 关联文件名称
```

Loader 位于 `app/rag/loader.py`，不会在 API、Milvus 或 Agent 模块中重复解析 CSV。

当前已准备：

```bash
python scripts/ingest.py --dry-run
```

运行验证会在核心模块完成后统一执行。

## 旧版

Java / Spring Boot 版本保存在：

```text
legacy-java
```

后续主分支只继续维护 Python / LangChain / LangGraph / RAG 新架构。
