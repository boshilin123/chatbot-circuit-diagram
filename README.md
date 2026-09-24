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

- CSV -> LangChain Document
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
│   ├── schemas/
│   └── main.py
├── data/
│   ├── circuit-data.csv
│   └── keywords.txt
├── frontend/
│   ├── css/
│   ├── js/
│   └── index.html
├── tests/
├── .env.example
├── pyproject.toml
└── LANGCHAIN_RAG_REFACTOR_PLAN.md
```

## 旧版

Java / Spring Boot 版本保存在：

```text
legacy-java
```

后续主分支只继续维护 Python / LangChain / LangGraph / RAG 新架构。
