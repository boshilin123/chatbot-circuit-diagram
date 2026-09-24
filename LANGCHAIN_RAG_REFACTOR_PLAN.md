# LangChain + LangGraph + Hybrid RAG 重构实施方案

> 项目：chatbot-circuit-diagram  
> 目标：将现有 Java/Spring Boot + DeepSeek 的车辆电路图资料导航系统，重构为一个可在本地完整运行、可学习、可测试、可用于 AI/Agent 项目展示的 LangChain 1.x + LangGraph + Hybrid RAG 项目。  
> 当前阶段要求：**只要求本地运行，不部署公网。**

## 当前实施状态（2026-09-24）

当前仓库已经正式进入重构阶段：

- ✅ 已创建 `legacy-java` 分支保存旧版 Java / Spring Boot 实现；
- ✅ `main` 已清理 `.idea/`、`target/` 和重复 CSV；
- ✅ `main` 已移除旧 Java 后端与 Maven / Railway 构建文件；
- ✅ 当前分支和 `legacy-java` 顶端均已移除硬编码 DeepSeek Key；
- ✅ 已新增 `.env.example`，真实 Key 只允许保存在本地 `.env`；
- ✅ 已初始化 Python 3.11/3.12 + FastAPI + Pydantic 项目骨架；
- ✅ 已通过 `langchain-deepseek` 接入 `ChatDeepSeek`；
- ✅ 已建立 `GET /api/health` 和基础 `POST /api/chat`；
- ✅ 旧 HTML / CSS / JavaScript 前端已迁移至 `frontend/`；
- ✅ 原始 CSV 与 `keywords.txt` 已迁移至 `data/`；
- ✅ 已建立基础 pytest 健康检查测试；
- ⏳ **待用户手动完成：在 DeepSeek 控制台吊销历史泄露的旧 Key，并生成新 Key；**
- ✅ Phase 2 已完成代码实现：CSV Loader、Document Schema、导入脚本与 Loader 测试；
- ⏳ **所有运行验证统一放到最终验证阶段，不在各 Phase 中间打断开发；**
- ✅ Phase 3 已完成代码实现：本地 Embedding、Milvus Standalone、Dense VectorStore 与 Dense Retriever；
- ✅ Phase 4 已完成代码实现：Milvus BM25、Sparse Search、Dense + Sparse Hybrid Search、RRF；
- ✅ Phase 5 已完成代码实现：Qwen3 Cross-Encoder Reranker 与完整 Retrieval Pipeline；
- ✅ Phase 6 已完成代码实现：Structured Output、Query Rewrite、Metadata Filter 与 Query Plan；
- ✅ Phase 7 已完成代码实现：@tool、search_knowledge_base、get_document_by_id、create_agent 与 API 接入；
- ✅ Phase 8 已完成代码实现：StateGraph、多轮候选收敛、interrupt/Command、Checkpointer、thread_id、返回上一步；
- ✅ Phase 9 已完成代码实现：Benchmark、Hit@K / Recall / MRR、Latency、业务指标与报告生成；
- ▶️ **下一阶段：统一运行验证与问题修复。**

当前关键提交：

```text
5030b2b  chore: complete phase 0 repository cleanup
cb8d54f  refactor: start Python FastAPI LangChain migration
db5f92c  feat: add LangChain document ingestion pipeline
1bfa1f2  feat: add Milvus dense retrieval layer
eefb0fa  refactor: align code style with LangChain course examples
acfdaec  feat: add BM25 and hybrid RRF retrieval
5b74729  feat: add Cross-Encoder reranking pipeline
f0b7960  feat: add structured intent and query rewrite pipeline
00af3aa  feat: add LangChain agent and retrieval tools
3c9c6ae  feat: add LangGraph multi-turn clarification workflow
695e455  feat: add retrieval evaluation framework
```

---

## 验证策略

从当前阶段开始，采用 **先完成模块开发，最后统一验证** 的方式：

- 各 Phase 正常编写测试代码、dry-run、health check 等验证工具；
- 中间阶段不要求反复安装、启动和人工验证；
- 不为临时验证破坏正式目录结构，也不把测试逻辑塞进业务代码；
- 等核心链路实现完成后，再统一执行 pytest、FastAPI、Milvus、RAG、Agent、LangGraph 与浏览器端到端验证；
- 最终验证发现问题时，按模块边界回到对应组件修复。

---

## 代码写作规范（以学习 PDF 示例为模板）

后续代码除了遵守项目的模块化目录，还要尽量保持第 2～4 章示例代码的命名和书写习惯。

### 1. 工程目录保持模块化

课程示例通常为了教学集中写在 Notebook / 单文件中；本项目不照搬这种目录形式，而是继续按职责拆分：

```text
app/core/       配置、模型初始化、公共基础能力
app/rag/        Loader、Embedding、Milvus、Retriever、Fusion、Reranker
app/tools/      Agent Tool
app/agents/     Agent 创建与调用
app/graph/      LangGraph State / Node / Workflow
app/schemas/    Pydantic / TypedDict 数据结构
scripts/        索引、检索、评估等命令行入口
tests/          独立测试
```

原则：**目录按工程化拆分，单个文件内部按课程示例风格书写。**

### 2. 命名优先参考课程示例

优先采用课程中已经反复出现、语义清楚的命名：

```python
model
agent
embeddings
vectorstore
retriever
client
collection_name
system_prompt
checkpointer
runtime
state
docs
retrieved_docs
```

核心函数优先采用：

```python
create_collection()
dense_search()
sparse_search()
hybrid_search()
reciprocal_rank_fusion()
cross_encoder_rerank()
retrieve_docs()
search_knowledge_base()
```

数据结构使用 PascalCase：

```python
SearchIntent
CircuitSearchState
DenseSearchResult
```

不为了“工程感”创建没有必要的 Manager / Service / Handler 类。

### 3. LangChain 写法优先与课程保持一致

模型初始化优先：

```python
from langchain.chat_models import init_chat_model

model = init_chat_model(...)
```

Agent：

```python
from langchain.agents import create_agent

agent = create_agent(
    model=model,
    tools=[...],
    system_prompt=system_prompt,
)
```

Tool：

```python
@tool
def search_knowledge_base(query: str):
    """搜索车辆电路图资料库。"""
    ...
```

Structured Output：

```python
class SearchIntent(BaseModel):
    ...

structured_model = model.with_structured_output(SearchIntent)
```

LangGraph / Runtime 阶段优先参考课程中的：

```python
AgentState
ToolRuntime
InMemorySaver
Command
```

### 4. 代码内部采用课程式分步骤注释

对于有明确流程的函数，采用课程示例中的编号方式：

```python
def hybrid_search(query: str):
    # 1. 构建稠密检索请求
    ...

    # 2. 构建稀疏检索请求
    ...

    # 3. 使用 RRF 融合
    ...

    # 4. 返回结果
    ...
```

注释说明“为什么做”，避免把每一行 Python 都翻译成中文。

### 5. 不机械照抄课程

以下情况允许与课程不同：

- 课程 Notebook 中的全局变量，在正式项目中可放入独立配置模块；
- 课程为了演示集中在一个文件的代码，本项目必须拆分模块；
- 与本项目数据结构不符的 Text Splitter、PDF Loader 不强行加入；
- 技术版本发生变化时，以当前官方 API 为准，但命名和组织尽量保持课程风格；
- 测试注入、异常处理和类型约束可以比教学示例更完整。

最终目标：

> 看代码时能明显对应学习 PDF 中的知识和示例，同时又保持一个真实项目应有的目录结构与可维护性。

---

## 1. 重构结论

现有项目的核心业务价值需要保留：

1. 用户使用自然语言描述要查找的车辆电路图；
2. 系统理解品牌、车型、ECU、部件、电路图类型等搜索意图；
3. 从 `资料清单.csv` 中检索相关文档；
4. 当候选结果超过 5 条时，通过 3～5 个选择项继续缩小范围；
5. 支持多轮选择、重新表达需求、返回上一步；
6. 最终返回不超过 5 个结果，每个结果包含文档 ID 和标题。

但技术架构不再继续扩展现有 Java 代码，而是整体迁移到：

```text
Python + FastAPI
        ↓
LangChain
        ↓
LangGraph
        ↓
Agent / Tools / Structured Output
        ↓
Query Rewrite
        ↓
Hybrid RAG
(Dense Embedding + BM25)
        ↓
Milvus Standalone
        ↓
Fusion / Rerank
        ↓
多轮候选集收敛
        ↓
最终返回 <= 5 个文档
```

本项目的目标不是为了“堆技术”，而是让每一个组件都对应真实业务需求。

---

## 2. 当前项目现状

### 2.1 旧版（`legacy-java`）

旧版主要技术栈：

- Java 21
- Spring Boot
- Maven
- DeepSeek API
- HTML / CSS / 原生 JavaScript
- CSV 内存加载
- 自定义关键词提取
- 自定义 SearchIndex
- 交集 / 并集 / 全文搜索
- 自定义 SimilarityScorer
- 自定义 ConversationManager
- JVM 内存缓存
- JVM 内存限流

### 2.2 当前主分支（`main`）

当前已经迁移为：

- Python 3.11 / 3.12
- FastAPI
- Pydantic / pydantic-settings
- LangChain 1.x
- `langchain-deepseek`
- `ChatDeepSeek`
- HTML / CSS / 原生 JavaScript（暂时保留）
- `data/circuit-data.csv`
- `data/keywords.txt`
- pytest 基础测试

当前尚未接入 Milvus / Hybrid RAG / LangGraph，这些按后续 Phase 顺序逐步加入。

现有项目中值得保留的设计思想：

- 简单查询优先本地处理；
- AI 失败时有降级逻辑；
- 检索结果分为 1 条、2～5 条、5 条以上三种情况；
- 结果过多时通过分类逐步缩小范围；
- 支持会话状态；
- 有缓存、限流、监控意识。

现有项目中需要解决的问题：

- Java 代码与 LangChain / LangGraph 学习路线不一致；
- `ChatController.java` 过大，业务编排和 API 混在一起；
- `ConversationManager`、缓存、限流均依赖 JVM 内存；
- 自定义规则越来越多，维护成本高；
- 语义检索能力弱；
- 无真正的向量检索与 RAG；
- 测试基本只有 `contextLoads()`；
- `target/`、`.idea/` 已被提交进 Git；
- `circuit-data.csv` 与 `资料清单.csv` 内容重复；
- 当前公开仓库中曾出现 DeepSeek API Key，必须先废弃并更换。

---

## 3. 本次重构目标

### 3.1 必须实现

#### LangChain

- Model
- Prompt
- Structured Output
- Tool
- Agent
- Runtime Context
- Middleware

#### LangGraph

- State
- Node
- Edge
- Conditional Edge
- Checkpointer
- 多轮会话状态
- 返回上一步
- 候选集持续收敛

#### RAG

- CSV -> LangChain Document
- Dense Embedding
- BM25
- Hybrid Retrieval
- Milvus
- Query Rewrite
- Fusion
- Rerank
- Metadata Filter
- 检索评估

#### Web

- FastAPI
- 保留基本聊天页面
- 支持选择题按钮
- 本地浏览器访问

### 3.2 第一版暂时不做

- MCP
- Multi-Agent
- HyDE
- Multi-Hop RAG
- Kubernetes
- Redis
- 公网部署
- 云数据库
- 用户登录系统
- 微服务拆分

原则：

> 单 Agent 能解决的问题，不使用 Multi-Agent；内部函数能解决的问题，不为了技术展示强行 MCP 化。

---

## 4. 最终技术栈

| 层级 | 技术 |
|---|---|
| 编程语言 | Python 3.11 / 3.12 |
| API | FastAPI |
| 数据模型 | Pydantic |
| LLM 框架 | LangChain 1.x |
| 工作流 | LangGraph |
| LLM | DeepSeek（通过 LangChain 接入） |
| Dense Embedding | 中文/多语言 Embedding 模型，默认优先 BGE-M3 |
| Sparse Retrieval | BM25 |
| Vector DB | Milvus Standalone |
| Hybrid Search | Dense + BM25 |
| Fusion | RRF 或 Milvus Hybrid Ranker |
| Reranker | Cross-Encoder，默认优先 BGE Reranker |
| 会话状态 | LangGraph Checkpointer |
| Web 前端 | 第一阶段继续使用现有 HTML / CSS / JS |
| 测试 | pytest |
| 配置 | `.env` + `pydantic-settings` |
| 本地基础设施 | Docker Compose |
| 可观测性 | 日志优先；LangSmith 后续作为增强项 |

---

## 5. 为什么后端从 Java 改为 Python

本项目目标已经从普通 Web 后端项目变成：

- LangChain 学习项目；
- Agent 项目；
- RAG 项目；
- LangGraph 状态工作流项目。

继续保留 Spring Boot 会导致：

1. 学习资料示例与项目实现割裂；
2. LangChain / LangGraph Python 生态无法直接复用；
3. RAG、Embedding、Reranker、Milvus 示例接入成本增加；
4. 项目代码中同时维护 Java AI 逻辑和 Python AI 逻辑没有必要。

因此：

```text
Java / Spring Boot 后端
        ↓
整体迁移
        ↓
Python / FastAPI
```

前端第一阶段不需要重写。

---

## 6. 本地运行架构

最终本地环境：

```text
浏览器
  │
  ▼
localhost:8000
FastAPI
  │
  ▼
LangGraph
  │
  ├── LLM / Agent
  ├── Query Rewrite
  ├── Retriever Tool
  └── Conversation State
  │
  ▼
Milvus Standalone
localhost:19530
  │
  ├── Dense Vector
  ├── BM25 Sparse
  └── Metadata
```

Milvus 使用 Docker Compose 在本机启动。

不部署 Railway，不需要公网 URL。

---

## 7. 数据设计

### 7.1 原始 CSV

现有字段：

```text
ID
层级路径
关联文件名称
```

示例：

```text
12345
电路图->整车电路图->商用车->东风->天龙->KL
东风天龙KL整车仪表电路图
```

### 7.2 不使用 Text Splitter

本项目与普通 PDF RAG 不同。

每一行 CSV 本身就是一个完整的检索单元，因此：

```text
1 CSV Row = 1 LangChain Document
```

不要为了展示 RAG 技术而使用：

```text
RecursiveCharacterTextSplitter
```

去切几十个字的标题。

### 7.3 Document 结构

```python
Document(
    page_content=(
        "层级路径：电路图 整车电路图 商用车 东风 天龙 KL\n"
        "关联文件名称：东风天龙KL整车仪表电路图"
    ),
    metadata={
        "doc_id": 12345,
        "title": "东风天龙KL整车仪表电路图",
        "hierarchy_path": "电路图->整车电路图->商用车->东风->天龙->KL"
    }
)
```

Embedding 主要处理：

- 层级语义；
- 文件标题语义；
- 用户口语和标准文档名称之间的近义关系。

Metadata 保留：

- 文档 ID；
- 原始标题；
- 原始路径；
- 后续可解析出的品牌、车型、部件等字段。

---

## 8. 为什么必须使用 Hybrid Retrieval

车辆电路图检索同时包含两种需求。

### 8.1 语义检索

```text
用户：电脑板插头怎么接
文档：EDC17C53 ECU针脚定义
```

两者词面不同，但语义相关。

Dense Embedding 负责这种搜索。

### 8.2 精确检索

```text
EDC17C53
4K22
WP10
D13
KL
KC
```

这些车型、发动机、ECU 编号必须精确匹配。

BM25 负责这类关键词检索。

### 8.3 最终 Retriever

```text
                 User Query
                      │
           ┌──────────┴──────────┐
           ▼                     ▼
     Dense Retrieval         BM25 Retrieval
           │                     │
           └──────────┬──────────┘
                      ▼
                 RRF / Fusion
                      │
                      ▼
                  Top 20
                      │
                      ▼
             Cross-Encoder Rerank
                      │
                      ▼
                  Top Candidates
```

原则：

> Dense 负责“语义召回”，BM25 负责“精确召回”，Reranker 负责“精排序”。

---

## 9. Milvus 方案

第一版使用：

```text
Milvus Standalone + Docker Compose
```

原因：

- 本地即可运行；
- 支持 Dense Vector；
- 支持 BM25 全文检索；
- 支持 Hybrid Search；
- 与 LangChain 有直接集成；
- 可以真实学习 Vector DB，而不是只调用 FAISS。

注意：

> 不选择 Milvus Lite 作为最终 Hybrid RAG 方案，因为项目需要 Milvus 内置 BM25 全文检索。

---

## 10. Query Rewrite

真实用户输入往往不是标准关键词。

例如：

```text
有没有红岩那个车电脑板插头的那个图
```

Query Rewrite 后：

```text
红岩 ECU 电脑板 针脚 接口 电路图
```

Query Rewrite 的目标：

- 去掉无意义口语；
- 保留车型和编号；
- 补充同义词；
- 不虚构不存在的 ECU / 车型；
- 输出适合 Retriever 的 query。

禁止让 Query Rewrite 随意“猜”型号。

---

## 11. Structured Output

定义：

```python
class SearchIntent(BaseModel):
    brand: str | None = None
    model: str | None = None
    component: str | None = None
    ecu_type: str | None = None
    document_type: str | None = None
    keywords: list[str] = []
```

例如：

```text
用户：
我想找东风天龙发动机 ECU 针脚图
```

得到：

```json
{
  "brand": "东风",
  "model": "天龙",
  "component": "发动机",
  "ecu_type": "ECU",
  "document_type": "针脚图",
  "keywords": ["发动机", "ECU", "针脚"]
}
```

结构化结果主要用于：

- Metadata Filter；
- Query Rewrite；
- Facet 生成；
- 日志与 Debug。

---

## 12. Agent Tools

第一版 Agent 只保留少量高价值 Tool。

### Tool 1：search_circuit_documents

输入：

```text
query
filters
top_k
```

输出：

```text
候选文档
相关性分数
metadata
```

### Tool 2：get_document_by_id

输入：

```text
document_id
```

输出：

```text
ID
标题
层级路径
```

第一版不要把每个内部函数都包装成 Tool。

---

## 13. LangGraph State 设计

核心 State：

```python
class CircuitSearchState(TypedDict):
    messages: list
    original_query: str | None
    rewritten_query: str | None
    search_intent: dict | None

    candidate_ids: list[int]
    candidate_documents: list[dict]

    selected_filters: dict
    used_facets: list[str]

    clarification_round: int
    current_options: list[dict]

    final_results: list[dict]
    status: str
```

重点：

> 多轮对话真正保存的是“候选集和筛选状态”，而不是只保存聊天文本。

---

## 14. 多轮选择题工作流

业务规则：

```text
候选结果 <= 5
    ↓
直接返回最终结果

候选结果 > 5
    ↓
分析当前候选集合
    ↓
选择一个最有区分度的 Facet
    ↓
生成 3～5 个选项
    ↓
等待用户选择
    ↓
过滤候选集合
    ↓
再次判断数量
```

LangGraph：

```text
START
  │
  ▼
understand_query
  │
  ▼
rewrite_query
  │
  ▼
retrieve
  │
  ▼
rerank
  │
  ▼
check_result_count
  │
  ├── <= 5 ───────────────► final_answer
  │
  └── > 5
         │
         ▼
     build_facets
         │
         ▼
      clarify
         │
      用户选择
         │
         ▼
    filter_candidates
         │
         └────────► check_result_count
```

---

## 15. Facet 生成

候选结果过多时，优先从实际候选文档中找可以区分结果的属性：

1. 品牌；
2. 车型 / 系列；
3. 文档类型；
4. ECU；
5. 部件；
6. 其他路径节点。

要求：

- 选项必须来自当前候选文档；
- 单轮 3～5 个；
- 不允许 LLM 编造不存在的车型；
- 避免重复询问已经确认过的维度。

LLM 可以帮助“表达问题”，但候选值应尽量由代码从数据中统计得出。

---

## 16. Checkpointer 与多轮记忆

使用 LangGraph Checkpointer 保存：

- messages；
- candidate_ids；
- selected_filters；
- used_facets；
- clarification_round；
- 当前候选列表。

每个浏览器会话维护：

```text
thread_id
```

第一版本地运行可直接使用内存型 Checkpointer。

后续如果需要真正持久化，再切换 SQLite / PostgreSQL。

第一版不引入 Redis。

---

## 17. Middleware

第一版只加入真正有用的 Middleware。

### 必做

#### Query Rewrite

统一处理口语化 query。

#### Model Fallback

DeepSeek 调用失败时允许进入本地检索逻辑，而不是整个系统直接报错。

#### Logging / Timing

记录：

- LLM 耗时；
- Retrieval 耗时；
- Rerank 耗时；
- 总请求耗时；
- 候选数量变化。

### 暂不做

- PII Middleware；
- Human-in-the-loop 审批；
- 复杂动态模型路由；
- Multi-Agent Middleware。

---

## 18. 新目录结构

```text
chatbot-circuit-diagram/
│
├── app/
│   ├── main.py
│   │
│   ├── api/
│   │   └── chat.py
│   │
│   ├── agents/
│   │   └── circuit_agent.py
│   │
│   ├── graph/
│   │   ├── state.py
│   │   ├── workflow.py
│   │   └── nodes/
│   │       ├── understand.py
│   │       ├── rewrite.py
│   │       ├── retrieve.py
│   │       ├── rerank.py
│   │       ├── clarify.py
│   │       ├── filter.py
│   │       └── answer.py
│   │
│   ├── rag/
│   │   ├── loader.py
│   │   ├── embeddings.py
│   │   ├── vectorstore.py
│   │   ├── retriever.py
│   │   ├── query_rewriter.py
│   │   └── reranker.py
│   │
│   ├── tools/
│   │   ├── search_documents.py
│   │   └── get_document.py
│   │
│   ├── middleware/
│   │   ├── fallback.py
│   │   └── logging.py
│   │
│   ├── schemas/
│   │   ├── chat.py
│   │   ├── search.py
│   │   └── document.py
│   │
│   └── core/
│       ├── config.py
│       └── logging.py
│
├── data/
│   ├── circuit-data.csv
│   └── keywords.txt
│
├── frontend/
│   ├── index.html
│   ├── css/
│   └── js/
│
├── scripts/
│   ├── ingest.py
│   └── build_eval_set.py
│
├── eval/
│   ├── benchmark.jsonl
│   └── evaluate.py
│
├── tests/
│   ├── test_loader.py
│   ├── test_retrieval.py
│   ├── test_reranker.py
│   ├── test_graph.py
│   └── test_api.py
│
├── docker-compose.yml
├── pyproject.toml
├── .env.example
├── .gitignore
└── README.md
```

---

## 19. 实施阶段

### Phase 0：仓库清理与安全处理

**状态：仓库侧已完成；DeepSeek Key 轮换需用户手动完成。**

#### 工作

- [ ] **用户操作**：在 DeepSeek 控制台废弃已经暴露的旧 API Key；
- [ ] **用户操作**：生成新的 DeepSeek API Key；
- [x] 新 Key 设计为只保存在本地 `.env`；
- [x] 新增 `.env.example`；
- [x] 确保 `.env` 加入 `.gitignore`；
- [x] 创建 `legacy-java` 分支保留旧实现；
- [x] 从主分支移除 `target/`；
- [x] 从主分支移除 `.idea/`；
- [x] 删除重复 CSV，仅保留 `data/circuit-data.csv`；
- [x] 旧 Java 后端与 Maven / Railway 构建文件退出 `main`；
- [x] 当前 `main` 与 `legacy-java` 顶端均不再包含真实 API Key；
- [x] README 已更新为新架构迁移说明。

#### 验收

主分支中不能再出现：

```text
API Key
target/
.idea/
重复 CSV
```

### Phase 1：Python + FastAPI + LangChain 基础骨架

**状态：代码骨架已完成；运行验证统一放到最终验证阶段。**

#### 工作

- [x] 初始化 Python 项目与 `pyproject.toml`；
- [x] 创建 FastAPI；
- [x] 创建 `GET /api/health`；
- [x] 创建基础 `POST /api/chat`；
- [x] 使用 `.env` / `pydantic-settings` 加载 DeepSeek 配置；
- [x] 使用 LangChain `ChatDeepSeek`，不再手写 DeepSeek HTTP 请求；
- [x] 建立 Pydantic Request / Response；
- [x] 将现有前端迁移到 `frontend/` 并由 FastAPI 提供静态资源；
- [x] 新增基础 `tests/test_health.py`；
- [ ] **最终验证阶段执行**：pytest、真实 DeepSeek 调用和浏览器端到端测试。

#### 验收

```bash
uvicorn app.main:app --reload
```

浏览器访问：

```text
http://127.0.0.1:8000
```

完成以下验证后 Phase 1 才正式关闭：

- `/api/health` 返回 `status=ok`；
- pytest 通过；
- 浏览器可打开现有聊天页面；
- 配置新 DeepSeek Key 后，`/api/chat` 能通过 LangChain 返回真实模型回复。

### Phase 2：CSV -> LangChain Document

**状态：代码实现已完成；运行验证统一放到最终验证阶段。**

实际目录：

```text
app/core/paths.py
app/schemas/document.py
app/rag/loader.py
scripts/ingest.py
tests/test_loader.py
```

- [x] 读取 `data/circuit-data.csv`；
- [x] 每行生成一个 LangChain Document；
- [x] metadata 保存 `doc_id`、`title`、`hierarchy_path`；
- [x] 额外保存 `hierarchy_segments` 和 `hierarchy_depth`，供后续 Facet / Metadata Filter 使用；
- [x] 生成统一 `search_text`；
- [x] 真实表头按 `ID / 层级路径 / 关联文件名称` 处理；
- [x] 支持 UTF-8 BOM；
- [x] 支持缺字段 / 非法 ID 等坏行记录，不因单行错误中断全部导入；
- [x] 新增 `scripts/ingest.py --dry-run`；
- [x] 编写独立 Loader 单元测试；
- [ ] **最终验证阶段执行**：确认真实 CSV 总行数、有效 Document 数和异常行统计。

#### 验收

```bash
python scripts/ingest.py --dry-run
```

能输出文档总数、示例 Document、Metadata，并正确报告坏数据。

### Phase 3：Milvus + Dense Retrieval

**状态：代码实现已完成；运行验证统一放到最终验证阶段。**

实际目录：

```text
app/rag/embeddings.py
app/rag/vectorstore.py
app/rag/retriever.py
app/schemas/search.py
scripts/index_dense.py
scripts/search_dense.py
tests/test_retrieval.py
docker-compose.yml
```

- [x] 添加 `docker-compose.yml`；
- [x] 使用 Milvus Standalone，本地服务地址设计为 `http://localhost:19530`；
- [x] 接入 `langchain-huggingface`；
- [x] Dense Embedding 默认使用 `BAAI/bge-m3`；
- [x] Embedding、VectorStore、Retriever 分模块实现；
- [x] 创建独立 Dense Collection 配置；
- [x] Dense 索引使用 HNSW + COSINE；
- [x] 支持按 `doc_id` 作为稳定主键写入；
- [x] 实现 Dense TopK 检索；
- [x] 实现 `scripts/index_dense.py` 索引入口；
- [x] 实现 `scripts/search_dense.py` 检索入口；
- [x] 编写 Dense Retriever 单元测试；
- [x] Docker 持久化数据 `volumes/` 加入 `.gitignore`；
- [ ] **最终验证阶段执行**：Docker Compose 启动、BGE-M3 下载、全量向量写入、Dense Search 和 pytest。

### Phase 4：BM25 + Hybrid Retrieval

**状态：代码实现已完成；运行验证统一放到最终验证阶段。**

实际目录：

```text
app/rag/hybrid_vectorstore.py
app/rag/hybrid_search.py
app/rag/fusion.py
app/schemas/search.py
scripts/index_hybrid.py
scripts/search_sparse.py
scripts/search_hybrid.py
tests/test_hybrid_search.py
tests/test_fusion.py
```

实现风格对齐课程第 4 章 Milvus 示例：

```python
dense_search(query)
sparse_search(query)
hybrid_search(query, ranker)
reciprocal_rank_fusion(ranked_lists, k=60)

ranker = RRFRanker(k=60)
```

- [x] 使用 `BM25BuiltInFunction` 启用 Milvus 内置 BM25；
- [x] Analyzer 使用课程示例中的 `{"type": "chinese"}`；
- [x] Hybrid Collection 同时建立 `dense / sparse` 字段；
- [x] Dense 使用 COSINE；
- [x] Sparse 使用 BM25；
- [x] 实现 `sparse_search()`；
- [x] 使用 `AnnSearchRequest` 构建 Dense / Sparse 双路请求；
- [x] 实现 `hybrid_search(query, ranker)`；
- [x] 默认使用 `RRFRanker(k=60)`；
- [x] 独立实现课程第 2 节中的 `reciprocal_rank_fusion()`，方便后续 Benchmark 对比；
- [x] 支持 `filter_query`，为后续 Metadata Filter / LangGraph 多轮筛选预留；
- [x] 新增 Hybrid 索引、Sparse 检索、Hybrid 检索命令行脚本；
- [x] 新增 Sparse / RRF 单元测试；
- [ ] **最终验证阶段执行**：全量 Hybrid Collection 写入、BM25 精确型号召回、RRF Hybrid Search 与 pytest。

重点验证：

```text
EDC17C53
4K22
WP10
KL
KC
```

### Phase 5：Reranker

**状态：代码实现已完成；运行验证统一放到最终验证阶段。**

实际目录：

```text
app/rag/reranker.py
app/rag/pipeline.py
app/schemas/search.py
scripts/search_rerank.py
tests/test_reranker.py
```

实现风格对齐课程第 4 章 Cross-Encoder 示例：

```python
model = CrossEncoder(
    "Qwen/Qwen3-Reranker-0.6B",
    device="cuda" if torch.cuda.is_available() else "cpu",
)

scores = model.predict(
    [(query, doc.content) for doc in docs]
)

final_docs = cross_encoder_rerank(
    query,
    docs,
    top_k,
)
```

- [x] Reranker 使用课程示例中的 `Qwen/Qwen3-Reranker-0.6B`；
- [x] 使用 `sentence_transformers.CrossEncoder`；
- [x] 自动选择 CUDA / CPU；
- [x] 实现 `cross_encoder_rerank(query, docs, top_k)`；
- [x] Query 与 Candidate Document 组成 Pair 进行精确打分；
- [x] 保存 Hybrid 阶段原始 `retrieval_score`；
- [x] Cross-Encoder 得分写入最终 `score`；
- [x] 按课程示例过滤非正相关结果；
- [x] 新增独立 `retrieve_docs()`，完成 Dense + BM25 + RRF + Cross-Encoder 编排；
- [x] 新增 `scripts/search_rerank.py`；
- [x] 新增 Reranker 单元测试；
- [ ] **最终验证阶段执行**：模型下载、GPU/CPU 设备选择、Hybrid Top20 精排、耗时统计与 pytest。

### Phase 6：Structured Output + Query Rewrite

**状态：代码实现已完成；运行验证统一放到最终验证阶段。**

实际目录：

```text
app/schemas/intent.py
app/schemas/query.py
app/rag/intent_parser.py
app/rag/query_rewriter.py
app/rag/filters.py
app/rag/query_pipeline.py
scripts/prepare_query.py
tests/test_intent_parser.py
tests/test_query_rewriter.py
tests/test_filters.py
```

实现风格对齐课程示例：

```python
structured_model = model.with_structured_output(SearchIntent)
intent = structured_model.invoke(...)

rewrite_prompt = f"""...
问题: {query}
关键词:
"""

response = model.invoke(rewrite_prompt)
rewritten_query = response.content
```

- [x] 定义 Pydantic `SearchIntent`；
- [x] 使用 `model.with_structured_output(SearchIntent)`；
- [x] 直接返回 Pydantic 结构化对象；
- [x] 实现课程风格 `rewrite_query()`；
- [x] Query Rewrite 使用“核心概念 + 空格分隔 + 只输出关键词”提示词；
- [x] 明确保留品牌、车型、发动机、ECU、数字字母型号；
- [x] 明确禁止补充用户没有提供的具体型号；
- [x] 保留 `original_query` 与 `rewritten_query`；
- [x] 实现 `build_metadata_filter()`；
- [x] 仅使用高置信度结构化字段构造 Milvus Filter；
- [x] 普通 `keywords` 不直接硬过滤，避免召回率下降；
- [x] 实现统一 `SearchQueryPlan`；
- [x] 新增 `scripts/prepare_query.py`；
- [x] 新增 Structured Output / Rewrite / Filter 测试；
- [ ] **最终验证阶段执行**：真实 DeepSeek Structured Output、Query Rewrite、车型编码保留、Metadata Filter 与 Retrieval 联调。

### Phase 7：Agent + Tools

**状态：代码实现已完成；运行验证统一放到最终验证阶段。**

实际目录：

```text
app/tools/search_knowledge_base.py
app/tools/get_document.py
app/agents/circuit_agent.py
app/rag/document_store.py
app/schemas/tool.py
scripts/run_agent.py
tests/test_agent.py
tests/test_document_store.py
```

实现风格对齐课程第 2 章 Agent / Tool 示例：

```python
from langchain.tools import tool
from langchain.agents import create_agent

@tool
def search_knowledge_base(query: str):
    """搜索车辆电路图资料库。"""
    ...

agent = create_agent(
    model=model,
    tools=[search_knowledge_base, get_document_by_id],
    system_prompt=system_prompt,
)
```

- [x] 使用 `@tool` 创建 `search_knowledge_base()`；
- [x] Tool 内部调用 `prepare_search_query()`；
- [x] Tool 内部调用 `retrieve_docs()`；
- [x] Tool 只返回资料库真实 ID / title / hierarchy_path；
- [x] 实现 `get_document_by_id()`；
- [x] 建立本地 `doc_id -> Document` 精确索引；
- [x] Tool 参数使用独立 Pydantic Schema；
- [x] 使用 `create_agent()` 创建车辆电路图 Agent；
- [x] Agent System Prompt 明确 Tool 调用边界；
- [x] 普通问候允许直接回答；
- [x] 具体资料检索要求必须调用 `search_knowledge_base`；
- [x] 明确禁止 Agent 编造车型、ECU、文档 ID 和检索结果；
- [x] `/api/chat` 已切换到 Agent；
- [x] 新增 `scripts/run_agent.py`；
- [x] 新增 Agent / Document Store 测试；
- [x] Agent 不承担“候选 >5 必须澄清”的硬规则，该规则保留给 Phase 8 LangGraph；
- [ ] **最终验证阶段执行**：问候不调用 Tool、资料查询触发 Tool、Tool Calling 循环、Agent 最终回复与 FastAPI 联调。

### Phase 8：LangGraph 多轮澄清

**状态：代码实现已完成；运行验证统一放到最终验证阶段。**

实际目录：

```text
app/graph/state.py
app/graph/workflow.py
app/graph/service.py
app/graph/nodes/understand.py
app/graph/nodes/chat.py
app/graph/nodes/rewrite.py
app/graph/nodes/retrieve.py
app/graph/nodes/facets.py
app/graph/nodes/clarify.py
app/graph/nodes/answer.py
app/schemas/graph.py
tests/test_facets.py
tests/test_graph_selection.py
tests/test_graph_routes.py
```

实现风格对齐课程 LangGraph / Runtime 示例：

```python
builder = StateGraph(CircuitSearchState)

builder.add_node(...)
builder.add_edge(START, ...)
builder.add_conditional_edges(...)

checkpointer = InMemorySaver()

graph = builder.compile(
    checkpointer=checkpointer,
)
```

用户选择使用课程 Human-in-the-loop 风格：

```python
selection = interrupt({...})

graph.ainvoke(
    Command(resume=option_value),
    config={"configurable": {"thread_id": session_id}},
)
```

- [x] 定义 `CircuitSearchState`；
- [x] 查询理解、Rewrite、Retrieve、Facet、Clarify、Answer 全部拆成独立 Node；
- [x] 使用 `StateGraph`；
- [x] 使用 `START / END`；
- [x] 使用 `add_conditional_edges()`；
- [x] 使用 `InMemorySaver` 作为本地 Checkpointer；
- [x] 使用浏览器 `sessionId` 作为 `thread_id`；
- [x] 普通问候不进入 RAG；
- [x] 搜索请求进入 Query Rewrite + Hybrid Retrieval；
- [x] 候选为 0 时进入确定性 no-results；
- [x] 候选 <=5 时直接进入 `final_answer`；
- [x] 候选 >5 时硬编码进入 `build_facets`；
- [x] Facet 选项优先来源于 CSV 真实 `层级路径`，不由 LLM 编造；
- [x] 单轮选项总数控制在 3～5；
- [x] 使用 `interrupt()` 暂停 Graph 等待用户选择；
- [x] `/api/select` 使用 `Command(resume=...)` 恢复；
- [x] 用户选择后只过滤当前候选集合，不重新生成虚假结果；
- [x] 已使用 Facet 写入 `used_facets`，避免重复询问同一层级；
- [x] 保存 Candidate Snapshot，支持“返回上一步”；
- [x] 最终结果通过 Graph 硬限制为最多 5 条；
- [x] 前端 `result` 已扩展支持 1～5 个 `documents[]`；
- [x] 继续保留单个 `document` 字段兼容旧前端数据结构；
- [x] Web 主流程从 Agent 直调切换为 LangGraph；
- [x] Phase 7 Agent / Tools 仍独立保留，可用于 Tool Calling 学习与单独运行；
- [x] 新增 Facet / Selection / Route 测试；
- [ ] **最终验证阶段执行**：Graph 编译、interrupt/resume、多轮筛选、返回上一步、thread_id、浏览器完整流程。

### Phase 9：评估系统

**状态：代码实现已完成；真实指标统一放到最终验证阶段执行。**

实际目录：

```text
eval/schema.py
eval/metrics.py
eval/benchmark.py
eval/runners.py
eval/report.py
eval/evaluate.py
eval/benchmark.jsonl
scripts/build_eval_set.py
scripts/suggest_eval_labels.py
scripts/evaluate_retrieval.py
tests/test_eval_metrics.py
tests/test_eval_benchmark.py
```

当前 `data/keywords.txt` 中的 22 条真实查询已经原样写入：

```text
eval/benchmark.jsonl
```

Benchmark 结构：

```json
{
  "query": "东风天龙仪表针脚图",
  "expected_ids": [],
  "source": "keywords.txt",
  "label_status": "pending"
}
```

注意：

> `keywords.txt` 只有 Query，没有 Ground Truth 文档 ID。不能为了让指标“看起来完整”而自动伪造 expected_ids。最终验证前必须先确认真实正确文档 ID，再将 `label_status` 改为 `verified`。

已经实现：

- [x] `build_eval_set.py`：从 `keywords.txt` 构建 Benchmark；
- [x] `suggest_eval_labels.py`：用轻量文本相似度辅助人工查找候选 ID；
- [x] `hit_at_k()`；
- [x] `recall_at_k()`；
- [x] `mrr_at_k()`；
- [x] P50 / P95 百分位；
- [x] `evaluate_retriever()`；
- [x] Dense 评估适配；
- [x] BM25 评估适配；
- [x] Hybrid RRF 评估适配；
- [x] Hybrid + Cross-Encoder 评估适配；
- [x] Markdown Report 生成；
- [x] 最终结果 <=5 比例；
- [x] 平均澄清轮数；
- [x] 平均 LLM 调用次数数据结构；
- [x] Metrics / Benchmark 单元测试；
- [ ] **最终验证阶段执行**：Ground Truth 标注、四种 Retriever 实测、P50/P95、业务指标和最终报告。

正式比较：

```text
Dense
BM25
Hybrid RRF
Hybrid + Cross-Encoder Rerank
```

正式指标：

- Hit@1
- Hit@5
- Recall@5
- MRR@5
- P50 Latency
- P95 Latency
- 最终结果 <= 5 比例
- 平均澄清轮数
- 平均 LLM 调用次数

### Phase 10：测试和文档整理

至少覆盖：

- Loader；
- Query Rewrite；
- Retriever；
- Hybrid Search；
- Reranker；
- Facet Filter；
- LangGraph State；
- API。

最终 README 包含：

1. 项目背景；
2. 架构图；
3. 技术栈；
4. Hybrid RAG 原理；
5. LangGraph 对话流程；
6. 本地部署步骤；
7. 示例对话；
8. Benchmark；
9. 项目目录；
10. 后续扩展方向。

---

## 20. 本地运行目标

### 1. 克隆项目

```bash
git clone https://github.com/boshilin123/chatbot-circuit-diagram.git
cd chatbot-circuit-diagram
```

### 2. 创建 Python 环境

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

### 3. 安装依赖

```bash
pip install -e ".[dev]"
```

### 4. 配置环境变量

`.env` 至少：

```text
DEEPSEEK_API_KEY=