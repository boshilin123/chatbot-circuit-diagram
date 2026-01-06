# 智能车辆电路图资料导航系统 🚗

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.1-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Railway](https://img.shields.io/badge/Deployed%20on-Railway-blueviolet.svg)](https://railway.app)

> 基于Spring Boot和DeepSeek AI的智能车辆电路图资料导航系统，帮助用户快速查找和定位电路图资料。

## 📋 目录

- [项目简介](#项目简介)
- [在线演示](#在线演示)
- [主要功能](#主要功能)
- [技术栈](#技术栈)
- [快速开始](#快速开始)
- [项目结构](#项目结构)
- [核心功能说明](#核心功能说明)
- [部署指南](#部署指南)
- [开发文档](#开发文档)
- [常见问题](#常见问题)
- [更新日志](#更新日志)
- [开发者](#开发者)

---

## 项目简介

这是一个智能化的车辆电路图资料导航系统，通过AI技术理解用户查询意图，提供精准的资料检索服务。系统内置4000+条电路图资料，支持自然语言查询、智能分类、多轮对话等功能。

### 核心特点

- 🎯 **智能理解**：使用DeepSeek AI理解用户查询意图
- ⚡ **快速响应**：简单查询秒级响应，复杂查询智能处理
- 📊 **智能分类**：自动按品牌、型号、部件、ECU类型分类
- 💬 **多轮对话**：通过对话逐步缩小搜索范围
- 📝 **会话管理**：支持多会话切换和历史记录
- 🚀 **性能优化**：缓存机制、请求限流、并发控制

---

## 在线演示

🌐 **线上地址**: [https://web-production-c033b.up.railway.app](https://web-production-c033b.up.railway.app)

📦 **GitHub仓库**: [https://github.com/boshilin123/chatbot-circuit-diagram](https://github.com/boshilin123/chatbot-circuit-diagram)

### 推荐搜索示例

- 三菱4K22
- 红岩杰狮保险丝
- 东风天龙仪表
- EDC17C53诊断指导

---

## 主要功能

### 1. 智能搜索 🔍

- 支持自然语言查询
- 自动提取品牌、型号、部件、ECU类型
- 多维度索引（品牌、型号、ECU、部件、全文）
- 交集搜索 + 并集搜索 + 全文搜索三级降级策略

### 2. AI查询理解 🤖

- 使用DeepSeek AI分析用户意图
- 查询复杂度评估，简单查询本地处理
- AI结果缓存（TTL 2小时）
- 超时机制（30秒）

### 3. 智能分类导航 📊

- 自动按品牌、型号、部件、ECU类型分类
- 多轮对话逐步缩小范围
- 避免重复分类
- 支持返回上一步

### 4. 会话管理 💬

- 多会话支持
- 会话历史记录
- 会话切换
- 清空会话

### 5. 分页功能 📄

- 结果超过5条自动分页
- 每页5条记录
- 支持查看下一页
- 筛选后分页

### 6. 性能优化 ⚡

- **缓存机制**：搜索结果缓存（30分钟）、AI结果缓存（2小时）
- **请求限流**：IP限制30次/分钟、会话限制20次/分钟
- **并发控制**：最大并发50
- **慢查询监控**：阈值3秒

---

## 技术栈

### 后端

- **框架**：Spring Boot 4.0.1
- **语言**：Java 21
- **构建工具**：Maven 3.9.12
- **AI服务**：DeepSeek API

### 前端

- **HTML5** + **CSS3** + **原生JavaScript**
- 响应式设计，支持移动端
- 无第三方框架依赖

### 数据

- **格式**：CSV文件
- **数量**：4233条电路图资料
- **字段**：ID、层级路径、文件名

### 部署

- **平台**：Railway
- **容器**：Docker
- **CI/CD**：GitHub自动部署

---

## 快速开始

### 前置要求

- Java 21+
- Maven 3.6+
- DeepSeek API Key

### 1. 克隆项目

```bash
git clone https://github.com/boshilin123/chatbot-circuit-diagram.git
cd chatbot-circuit-diagram
```

### 2. 配置环境变量

编辑 `src/main/resources/application.yml`:

```yaml
deepseek:
  api:
    key: ${DEEPSEEK_API_KEY:你的API密钥}
    url: ${DEEPSEEK_API_URL:https://api.deepseek.com/v1/chat/completions}
```

或设置环境变量:

```bash
# Windows
set DEEPSEEK_API_KEY=sk-your-api-key
set DEEPSEEK_API_URL=https://api.deepseek.com/v1/chat/completions

# Linux/Mac
export DEEPSEEK_API_KEY=sk-your-api-key
export DEEPSEEK_API_URL=https://api.deepseek.com/v1/chat/completions
```

### 3. 运行项目

```bash
mvn spring-boot:run
```

### 4. 访问应用

打开浏览器访问: [http://localhost:8080](http://localhost:8080)

---

## 项目结构

```
chatbot/
├── src/
│   ├── main/
│   │   ├── java/com/bo/chatbot/
│   │   │   ├── controller/           # 控制器层
│   │   │   │   └── ChatController.java
│   │   │   ├── service/              # 业务逻辑层
│   │   │   │   ├── DataLoaderService.java      # 数据加载
│   │   │   │   ├── DeepSeekService.java        # AI服务
│   │   │   │   ├── SearchIndex.java            # 搜索索引
│   │   │   │   ├── SmartSearchEngine.java      # 智能搜索
│   │   │   │   ├── QueryUnderstandingService.java  # 查询理解
│   │   │   │   ├── ResultCategorizer.java      # 结果分类
│   │   │   │   ├── ConversationManager.java    # 会话管理
│   │   │   │   ├── CacheService.java           # 缓存服务
│   │   │   │   ├── RateLimitService.java       # 限流服务
│   │   │   │   └── MonitoringService.java      # 监控服务
│   │   │   ├── model/                # 数据模型
│   │   │   │   ├── CircuitDocument.java
│   │   │   │   ├── QueryInfo.java
│   │   │   │   └── ConversationState.java
│   │   │   ├── config/               # 配置类
│   │   │   └── util/                 # 工具类
│   │   └── resources/
│   │       ├── static/               # 前端资源
│   │       │   ├── css/
│   │       │   │   └── style.css
│   │       │   ├── js/
│   │       │   │   └── chat.js
│   │       │   └── index.html
│   │       ├── application.yml       # 配置文件
│   │       ├── circuit-data.csv      # 数据文件
│   │       └── keywords.txt          # 关键词库
│   └── test/                         # 测试代码
├── pom.xml                           # Maven配置
├── Procfile                          # Railway部署配置
├── nixpacks.toml                     # Nixpacks构建配置
├── README.md                         # 项目说明
├── Git更新代码指南.md                 # Git操作指南
└── chatbot部署指南-Railway.md        # 部署指南
```

---

## 核心功能说明

### 智能搜索引擎

**三级搜索策略**:

1. **交集搜索**：品牌 AND 型号 AND 部件 AND ECU
2. **并集搜索**：品牌 OR 型号 OR 部件 OR ECU
3. **全文搜索**：关键词匹配

**相似度评分**:
- 完全匹配：100分
- 包含匹配：50分
- 部分匹配：25分

### AI查询理解

**查询复杂度评估**:
- 有明确品牌/ECU：-30分
- 包含数字：-20分
- 关键词丰富：-15分/个
- 明确部件：-20分
- 明确品牌型号组合：-30分

**阈值**: -50分以下本地处理，否则调用AI

### 结果分类

**分类优先级**:
1. 品牌（Brand）
2. 型号（Model）
3. 部件（Component）
4. ECU类型（ECU Type）

**分类策略**:
- 避免重复分类
- 自动选择最优分类维度
- 支持多轮分类

### 缓存机制

**搜索结果缓存**:
- TTL: 30分钟
- 最大容量: 1000条
- 淘汰策略: LRU

**AI结果缓存**:
- TTL: 2小时
- 最大容量: 1000条
- 淘汰策略: LRU

### 限流策略

- **IP限流**: 30次/分钟
- **会话限流**: 20次/分钟
- **最大并发**: 50

---

## 部署指南

### Railway部署（推荐）

详细步骤请参考: [chatbot部署指南-Railway.md](chatbot部署指南-Railway.md)

**快速步骤**:

1. 推送代码到GitHub
2. 在Railway创建项目
3. 连接GitHub仓库
4. 配置环境变量
5. 自动部署

**环境变量**:
```
DEEPSEEK_API_KEY=sk-your-api-key
DEEPSEEK_API_URL=https://api.deepseek.com/v1/chat/completions
```

### 本地部署

```bash
# 构建
mvn clean package -DskipTests

# 运行
java -jar target/chatbot-0.0.1-SNAPSHOT.jar
```

---

## 开发文档

### Git操作指南

详细步骤请参考: [Git更新代码指南.md](Git更新代码指南.md)

**快速命令**:
```bash
git add -A
git commit -m "修改说明"
git push origin main
```

### API接口

**聊天接口**:
```
POST /api/chat
Content-Type: application/json

{
  "sessionId": "session_xxx",
  "message": "三菱4K22"
}
```

**限流统计**:
```
GET /api/ratelimit/stats
```

### 测试工具

访问 `/test-ratelimit.html` 进行限流测试

---

## 常见问题

### Q1: 如何获取DeepSeek API Key?

访问 [DeepSeek官网](https://platform.deepseek.com/) 注册并获取API Key。

### Q2: 为什么首次访问很慢?

Railway免费服务有冷启动机制，首次访问需要10-30秒唤醒应用。

### Q3: 如何添加新的电路图资料?

编辑 `src/main/resources/circuit-data.csv` 文件，按格式添加新记录。

### Q4: 如何修改限流配置?

编辑 `src/main/resources/application.yml` 中的 `ratelimit` 配置。

### Q5: 浏览器显示旧版本怎么办?

按 `Ctrl + F5` 强制刷新，或清除浏览器缓存。

---

## 更新日志

### v1.0.0 (2025-01-06)

**新增功能**:
- ✅ 智能搜索引擎
- ✅ AI查询理解
- ✅ 智能分类导航
- ✅ 多轮对话
- ✅ 会话管理
- ✅ 分页功能
- ✅ 缓存机制
- ✅ 请求限流
- ✅ 性能监控

**优化**:
- ✅ UI界面优化
- ✅ 移动端适配
- ✅ 会话历史UI改进
- ✅ 删除按钮位置优化

**修复**:
- ✅ CSV文件加载问题（中文文件名）
- ✅ 端口配置问题
- ✅ 资源打包问题

---

## 开发者

**开发时间**: 2025年1月1日 - 1月6日

**技术栈**: Spring Boot + DeepSeek AI + JavaScript

**部署平台**: Railway

---

## 许可证

MIT License

---

## 联系方式

- **GitHub**: [boshilin123](https://github.com/boshilin123)
- **项目地址**: [chatbot-circuit-diagram](https://github.com/boshilin123/chatbot-circuit-diagram)

---

## 致谢

- [Spring Boot](https://spring.io/projects/spring-boot) - 后端框架
- [DeepSeek](https://www.deepseek.com/) - AI服务
- [Railway](https://railway.app/) - 部署平台

---

**⭐ 如果这个项目对你有帮助，请给个Star支持一下！**
