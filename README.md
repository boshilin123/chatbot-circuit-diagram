# 智能车辆电路图资料导航系统

## 项目简介

这是一个基于Spring Boot和DeepSeek AI的智能车辆电路图资料导航系统，帮助用户快速查找和定位电路图资料。

## 主要功能

- 🔍 智能搜索：支持自然语言查询
- 🤖 AI理解：使用DeepSeek AI理解用户意图
- 📊 智能分类：自动分类和筛选结果
- 💬 多轮对话：通过对话逐步缩小范围
- 📝 会话管理：支持多会话和历史记录
- ⚡ 性能优化：缓存机制和请求限流

## 技术栈

- **后端**：Spring Boot 3.x, Java 21
- **AI**：DeepSeek API
- **前端**：原生JavaScript, HTML5, CSS3
- **数据**：CSV文件（4000+条电路图资料）

## 在线访问

部署地址：[待填写]

## 本地运行

```bash
mvn spring-boot:run
```

访问：http://localhost:8080

## 环境变量

```
DEEPSEEK_API_KEY=你的API密钥
DEEPSEEK_API_URL=https://api.deepseek.com/v1/chat/completions
```

## 项目结构

```
chatbot/
├── src/
│   ├── main/
│   │   ├── java/com/bo/chatbot/
│   │   │   ├── controller/      # 控制器
│   │   │   ├── service/         # 业务逻辑
│   │   │   └── model/           # 数据模型
│   │   └── resources/
│   │       ├── static/          # 前端资源
│   │       ├── application.yml  # 配置文件
│   │       └── 资料清单.csv      # 数据文件
│   └── test/                    # 测试代码
├── pom.xml                      # Maven配置
└── Procfile                     # 部署配置
```

## 开发者

开发时间：2025年1月1日 - 1月6日
