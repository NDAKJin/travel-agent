# Travel Agent

基于 Spring AI 与 LangGraph4j 的多 Agent 编排示例。项目聚焦意图识别、需求收集、路线规划、审核、最终回复，以及 Agent 调用观测。

## 架构

```mermaid
flowchart LR
    client[客户端] --> api[Spring Boot API]
    api --> graph[LangGraph4j 编排]
    graph --> llm[LLM]
    graph --> kafka[Kafka]
    kafka --> mysql[(MySQL)]
    api --> redis[(Redis)]
```

流程：总控识别意图；路线规划请求依次经过需求询问、规划、审核和最终回复；普通请求由普通服务 Agent 处理后输出。规划师与普通服务 Agent 可调用旅行知识、路线、地点建议、预算四个纯 LLM 专家。

## 技术栈

- Java 21、Spring Boot、Spring AI Alibaba、LangGraph4j
- MyBatis、MySQL、Redis、Kafka
- React、TypeScript、Vite

项目不依赖 Elasticsearch 或 Neo4j。

## 配置

复制示例配置：

```powershell
Copy-Item src/main/resources/application.example.yml src/main/resources/application.yml
```

至少配置 MySQL、Redis、DashScope API Key 与 JWT 密钥。启用可观测时还需要 Kafka：

```env
SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092
TRAVEL_AGENT_OBSERVABILITY_ENABLED=true
```

## 运行

```powershell
.\mvnw.cmd spring-boot:run
```

管理端：

```powershell
cd fe
npm install
npm run dev
```

Docker 部署：

```bash
cp .env.example .env
docker compose up -d --build
```

不要执行 `docker compose down -v`，它会删除 MySQL 和 Redis 数据卷。

## 可观测

开启后，每次真实 LLM 调用都会经 Hook 发送到 Kafka，再异步写入 MySQL。管理端会话详情可查看 Agent 名称、输入、输出、Token、耗时和下一步决策。
