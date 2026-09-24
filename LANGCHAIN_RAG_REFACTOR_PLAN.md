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
- ▶️ **下一开发阶段：Phase 5 — Cross-Encoder Reranker。**

当前关键提交：

```text
5030b2b  chore: complete phase 0 repository cleanup
cb8d54f  refactor: start Python FastAPI LangChain migration
db5f92c  feat: add LangChain document ingestion pipeline
1bfa1f2  feat: add Milvus dense retrieval layer
eefb0fa  refactor: align code style with LangChain course examples
acfdaec  feat: add BM25 and hybrid RRF retrieval
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

- [ ] Hybrid 召回 Top20～30；
- [ ] Cross-Encoder 重新打分；
- [ ] 输出重新排序后的 TopK；
- [ ] 记录耗时；
- [ ] 对比 Hybrid 与 Hybrid + Rerank。

### Phase 6：Structured Output + Query Rewrite

- [ ] 定义 SearchIntent；
- [ ] LLM Structured Output；
- [ ] Query Rewrite；
- [ ] 保留原 Query；
- [ ] 不允许重写阶段虚构未知型号；
- [ ] 可确认字段用于 Metadata Filter。

### Phase 7：Agent + Tools

- [ ] Retriever 包装为 `search_circuit_documents` Tool；
- [ ] 实现 `get_document_by_id` Tool；
- [ ] 创建 LangChain Agent；
- [ ] 问候不触发搜索；
- [ ] 资料查询触发 Retriever。

### Phase 8：LangGraph 多轮澄清

- [ ] 定义 CircuitSearchState；
- [ ] 建立 Graph Node；
- [ ] Conditional Edge；
- [ ] <= 5 直接结束；
- [ ] > 5 进入 Facet；
- [ ] 生成 3～5 个选项；
- [ ] 用户选择后过滤；
- [ ] 支持返回上一步；
- [ ] Checkpointer 保存状态。

### Phase 9：评估系统

使用 `keywords.txt` 建立 benchmark：

```json
{"query": "三菱4K22电脑板", "expected_ids": [123, 456]}
```

比较：

```text
旧 Java Rule Search
Dense
BM25
Hybrid
Hybrid + Rerank
```

指标：

- Hit@1
- Hit@5
- Recall@5
- MRR@5
- 最终结果 <= 5 比例
- 平均澄清轮数
- P50 Latency
- P95 Latency
- 单次请求 LLM 调用次数

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