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
- Phase 2：CSV -> LangChain Document
- 独立 CSV Loader / Document Schema
- `scripts/ingest.py --dry-run`
- Loader 单元测试
- Milvus Dense Retrieval
- BGE-M3 本地 Embedding
- HNSW + COSINE Dense Index
- Dense Retriever / 索引脚本 / 搜索脚本
- Phase 4：BM25 + Hybrid Retrieval
- Milvus BM25BuiltInFunction
- sparse_search / hybrid_search
- AnnSearchRequest + RRFRanker(k=60)
- 独立 reciprocal_rank_fusion
- Phase 5：Cross-Encoder Reranker
- Qwen/Qwen3-Reranker-0.6B
- cross_encoder_rerank()
- retrieve_docs() 完整检索精排管线
- Phase 6：Structured Output + Query Rewrite
- SearchIntent + with_structured_output()
- rewrite_query()
- Metadata Filter
- SearchQueryPlan
- Phase 7：Agent + Tools
- @tool search_knowledge_base()
- @tool get_document_by_id()
- create_agent()
- /api/chat Agent 接入
- Phase 8：LangGraph 多轮澄清
- StateGraph / Conditional Edge
- InMemorySaver / thread_id
- interrupt() / Command(resume=...)
- 3～5 个真实 Facet 选项
- 返回上一步
- 最终结果 <= 5
- Phase 9：Retrieval Evaluation
- Hit@1 / Hit@5 / Recall@5 / MRR@5
- P50 / P95 Latency
- Benchmark JSONL + Markdown Report

完整实施路线见 [`docs/LANGCHAIN_RAG_REFACTOR_PLAN.md`](./docs/LANGCHAIN_RAG_REFACTOR_PLAN.md)。

## 本地启动

### 已配置过的 Windows 环境

在项目根目录执行：

```powershell
.\scripts\start.ps1
```

默认地址为 `http://127.0.0.1:8011`。如需指定其他端口：

```powershell
.\scripts\start.ps1 -Port 8000
```

该脚本会先启动 Docker 中的 Milvus / MinIO / etcd，再在前台启动
FastAPI。按 `Ctrl+C` 停止 FastAPI；Docker 数据保存在 `runtime/docker/`。

### 首次安装

#### 1. Python 环境

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

#### 2. 安装依赖

```bash
pip install -e ".[dev]"
```

#### 3. 配置 DeepSeek

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

#### 4. 配置 LangSmith（可选）

需要在 LangSmith 查看 LangGraph 节点、LLM 调用、耗时和错误时，在 `.env`
中配置：

```dotenv
LANGSMITH_TRACING="true"
LANGSMITH_API_KEY="your-langsmith-api-key"
LANGSMITH_PROJECT="chatbot-circuit-diagram"
```

双引号可保留，`start.ps1` 会在启动 Uvicorn 前导入 `.env` 并去掉外层引号。
如果不需要上报 Trace，设置 `LANGSMITH_TRACING=false`。

当前 Web 主流程由 LangGraph 编排，`app/agents/` 中的 `create_agent()` 实现作为
Phase 7 的独立 Agent 能力保留，但 `/api/chat` 实际调用 `app/graph/`。

#### 5. 启动 Docker 依赖

```bash
docker compose up -d
```

#### 6. 首次建立索引

仅首次启动或确认需要重建数据时执行：

```bash
python scripts/index_dense.py --recreate
python scripts/index_hybrid.py --recreate
```

#### 7. 启动 FastAPI

在 Windows 上使用启动脚本，它会同时加载 `.env`：

```powershell
.\scripts\start.ps1
```

Linux / macOS 先导出 `.env` 再启动：

```bash
set -a
source .env
set +a
uvicorn app.main:app --host 127.0.0.1 --port 8011 --reload
```

#### 8. 启动 LangGraph Studio（可选）

LangSmith Tracing 用于查看已经发生的调用记录；LangGraph Studio 用于以图形方式
交互运行、调试节点和处理中断。Studio 使用独立的本地 Agent Server，不替代
FastAPI Web 服务。

首次安装开发依赖：

```powershell
.\.venv\Scripts\python.exe -m pip install -e ".[dev]"
```

启动 Studio（默认端口 `2024`，会自动打开浏览器）：

```powershell
.\scripts\start-studio.ps1
```

也可以只启动服务，再手动打开终端输出的 Studio URL：

```powershell
.\scripts\start-studio.ps1 -NoBrowser
```

Studio 中选择 `circuit_search` 图，输入字段使用 `original_query`。Milvus / MinIO /
etcd 仍需提前通过 `start.ps1` 或 `docker compose up -d` 启动。

打开：

```text
http://127.0.0.1:8011
```

健康检查：

```text
http://127.0.0.1:8011/api/health
```

### 运行期文件

BGE-M3 默认加载原始权重，并关闭 Transformers 自动下载转换权重的后台行为，
避免同时缓存 `.bin` 与转换后的 `.safetensors`。如需启用转换，可显式设置
`DISABLE_SAFETENSORS_CONVERSION=false`。修改代码后需重启 FastAPI 才能生效。

所有可再生的运行数据统一放在：

```text
runtime/
├── docker/   # Milvus / MinIO / etcd 持久化数据
├── logs/     # 需要保留的本地日志
└── reports/  # 冒烟测试等生成报告
```

`runtime/`、`.venv/`、`.env` 和各类缓存均不会提交到 Git。

## 当前目录

```text
.
├── app/
│   ├── agents/      # LangChain Agent
│   ├── api/         # FastAPI 路由
│   ├── core/        # 配置与模型
│   ├── graph/       # LangGraph 多轮工作流
│   ├── rag/         # 检索、精排与索引
│   ├── schemas/     # Pydantic 数据模型
│   └── tools/       # Agent Tools
├── data/            # CSV 资料与原始查询
├── docs/            # 重构计划与测试方案
├── eval/            # 离线检索评估
├── frontend/        # 聊天页面
├── scripts/         # 启动、建索引与验证脚本
├── tests/           # 自动化测试
├── runtime/         # 本地运行数据（Git 忽略）
├── .env.example
├── docker-compose.yml
├── README.md
├── pyproject.toml
└── .gitignore
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

## Dense Retrieval

Phase 3 已建立独立 Dense 检索层：

```text
LangChain Document
        ↓
BAAI/bge-m3
        ↓
HNSW / COSINE
        ↓
Milvus Standalone
        ↓
DenseRetriever
```

职责拆分：

- `app/rag/embeddings.py`：Embedding Model；
- `app/rag/vectorstore.py`：Milvus Collection / Index / 数据写入；
- `app/rag/retriever.py`：Dense Search；
- `scripts/index_dense.py`：全量 Dense 索引入口；
- `scripts/search_dense.py`：命令行检索入口；
- `docker-compose.yml`：本地 Milvus Standalone。

当前只实现 Dense 路径。BM25 与 Hybrid Fusion 在 Phase 4 实现。

所有运行验证继续统一放在最终验证阶段。

## Hybrid Retrieval

Phase 4 按课程第 4 章 Milvus 示例实现：

```text
                  Query
                    │
          ┌─────────┴─────────┐
          ▼                   ▼
      dense_search       sparse_search
       COSINE                 BM25
          │                   │
          └─────────┬─────────┘
                    ▼
          hybrid_search(query, ranker)
                    │
                    ▼
             RRFRanker(k=60)
                    │
                    ▼
              Hybrid TopK
```

核心文件：

- `app/rag/hybrid_vectorstore.py`：Dense + Sparse Collection；
- `app/rag/hybrid_search.py`：`sparse_search()` / `hybrid_search()`；
- `app/rag/fusion.py`：手写 `reciprocal_rank_fusion()`；
- `scripts/index_hybrid.py`：Hybrid Collection 导入；
- `scripts/search_sparse.py`：BM25 检索；
- `scripts/search_hybrid.py`：RRF 混合检索。

BM25 使用 Milvus 内置函数：

```python
BM25BuiltInFunction(
    analyzer_params={"type": "chinese"},
)
```

运行验证统一留到最终验证阶段。

## Cross-Encoder Reranker

Phase 5 对齐课程第 4 章示例，使用：

```python
from sentence_transformers import CrossEncoder

model = CrossEncoder(
    "Qwen/Qwen3-Reranker-0.6B",
    device="cuda" if torch.cuda.is_available() else "cpu",
)
```

完整检索链路现在为：

```text
Query
  ↓
Dense + BM25
  ↓
RRF Hybrid TopK
  ↓
cross_encoder_rerank()
  ↓
Final TopK
```

职责拆分：

- `app/rag/hybrid_search.py`：Dense + BM25 + RRF 召回；
- `app/rag/reranker.py`：Cross-Encoder 精排；
- `app/rag/pipeline.py`：完整检索流程编排；
- `scripts/search_rerank.py`：最终检索精排入口；
- `tests/test_reranker.py`：Reranker 测试。

运行验证继续统一放到最终验证阶段。

## Query Understanding

Phase 6 对齐课程中的 Structured Output 与 Query Rewrite 示例：

```text
User Query
    ↓
with_structured_output(SearchIntent)
    ↓
SearchIntent
    ↓
rewrite_query()
    ↓
rewritten_query
    ↓
build_metadata_filter()
    ↓
SearchQueryPlan
```

职责拆分：

- `app/schemas/intent.py`：`SearchIntent`；
- `app/schemas/query.py`：`SearchQueryPlan`；
- `app/rag/intent_parser.py`：Structured Output 意图抽取；
- `app/rag/query_rewriter.py`：Query Rewrite；
- `app/rag/filters.py`：Milvus Metadata Filter；
- `app/rag/query_pipeline.py`：查询准备流程；
- `scripts/prepare_query.py`：查询优化预览入口。

Query Rewrite 保留原始车型、ECU 和字母数字编码，不猜测未知型号。

运行验证继续统一放到最终验证阶段。

## LangChain Agent

Phase 7 对齐课程中的 `@tool + create_agent` 示例：

```text
User
  ↓
create_agent()
  │
  ├── 普通问候 → Model 直接回答
  │
  └── 资料检索
          ↓
   search_knowledge_base()
          ↓
   prepare_search_query()
          ↓
      retrieve_docs()
          ↓
      Tool Result
          ↓
       Agent Reply
```

当前 Agent 工具只有两个：

- `search_knowledge_base()`：车辆电路图 RAG 检索；
- `get_document_by_id()`：按文档 ID 精确获取资料。

核心文件：

- `app/agents/circuit_agent.py`：`create_agent()` 与 System Prompt；
- `app/tools/search_knowledge_base.py`：检索 Tool；
- `app/tools/get_document.py`：文档 ID Tool；
- `app/rag/document_store.py`：本地 ID 索引；
- `scripts/run_agent.py`：Agent 命令行入口。

`/api/chat` 已经接入 Agent。

“候选结果 >5 必须继续澄清、最终 <=5”的硬业务规则不交给 Agent，由下一阶段 LangGraph 实现。

运行验证继续统一放到最终验证阶段。

## LangGraph Workflow

Web 主流程从 Phase 8 起由 LangGraph 接管：

```text
START
  ↓
understand_query
  │
  ├── 普通对话 → chat → END
  │
  └── 搜索
        ↓
   rewrite_query
        ↓
      retrieve
        ↓
  result_count
   ├── 0 → no_results → END
   ├── <=5 → final_answer → END
   └── >5
         ↓
    build_facets
         ↓
       clarify
         ↓
      interrupt
         ↓
    用户 /api/select
         ↓
 Command(resume=...)
         ↓
  filter candidates
         └────→ result_count
```

课程概念在项目中的对应：

- `CircuitSearchState`：State；
- `app/graph/nodes/*`：Nodes；
- `add_edge`：固定流程；
- `add_conditional_edges`：条件分支；
- `InMemorySaver`：本地 Checkpointer；
- `sessionId`：`thread_id`；
- `interrupt()`：等待用户选择；
- `Command(resume=...)`：恢复 Graph。

候选 >5 时不会由 Agent 自由决定，而是由 Graph 强制进入澄清；最终输出硬限制为 <=5。

Facet 优先从真实 `hierarchy_path` 中生成，不由 LLM 编造。

前端已支持一次渲染 1～5 条最终资料。

运行验证继续统一放到最终验证阶段。

## Retrieval Evaluation

Phase 9 已建立独立评估层：

```text
data/keywords.txt
      ↓
eval/benchmark.jsonl
      ↓
人工确认 expected_ids
      ↓
Dense / BM25 / Hybrid RRF / Hybrid + Rerank
      ↓
Hit@1 / Hit@5 / Recall@5 / MRR@5
      ↓
P50 / P95 Latency
      ↓
eval/retrieval_report.md
```

当前 22 条查询已写入 Benchmark，但 `expected_ids` 暂时为空，因为原始 `keywords.txt` 没有 Ground Truth。

辅助标注：

```bash
python scripts/suggest_eval_labels.py --top-k 10
```

确认真实 ID 后，将对应样本改为：

```json
{
  "query": "...",
  "expected_ids": [123],
  "label_status": "verified"
}
```

然后最终验证阶段执行：

```bash
python scripts/evaluate_retrieval.py
```

评估代码全部位于 `eval/`，不污染在线 `app/rag/`。

## 旧版

Java / Spring Boot 版本保存在：

```text
legacy-java
```

后续主分支只继续维护 Python / LangChain / LangGraph / RAG 新架构。
