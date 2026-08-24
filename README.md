<div align="center">

# 行迹 AI 旅行助手

### 让每一次出发，都有一位懂目的地的 AI 向导

面向旅行场景的智能助手与运营管理平台：在微信小程序中对话、规划行程；在管理台查看会话与 Agent 调用记录。

[![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0-6DB33F?logo=spring&logoColor=white)](https://spring.io/projects/spring-ai)
[![React](https://img.shields.io/badge/React-18-61DAFB?logo=react&logoColor=20232A)](https://react.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5-3178C6?logo=typescript&logoColor=white)](https://www.typescriptlang.org/)
[![MySQL](https://img.shields.io/badge/MySQL-4479A1?logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Redis](https://img.shields.io/badge/Redis-DC382D?logo=redis&logoColor=white)](https://redis.io/)
[![Kafka](https://img.shields.io/badge/Apache%20Kafka-231F20?logo=apachekafka&logoColor=white)](https://kafka.apache.org/)
[![Vite](https://img.shields.io/badge/Vite-7-646CFF?logo=vite&logoColor=white)](https://vite.dev/)
[![WeChat](https://img.shields.io/badge/WeChat%20Mini%20Program-07C160?logo=wechat&logoColor=white)](https://developers.weixin.qq.com/miniprogram/dev/framework/)
[![License](https://img.shields.io/badge/license-MIT-2ea44f)](LICENSE)

<a href="https://openjdk.org/"><img src="https://skillicons.dev/icons?i=java,spring,react,ts,mysql,redis" alt="行迹 AI 旅行助手技术栈" /></a>

**Java 21** · **Spring Boot 4** · **Spring AI Alibaba** · **LangGraph4j** · **React 18** · **微信小程序**

[快速开始](#快速开始) · [系统架构](#系统架构) · [API 文档](#api-文档) · [项目结构](#项目结构)

</div>

## 项目简介

行迹 AI 旅行助手将旅行问答、路线规划和运营管理放进同一套系统：

| 使用者 | 能做什么 |
| --- | --- |
| 旅行者 | 微信授权登录、AI 旅行问答、个性化行程规划、会话历史 |
| 运营人员 | 管理微信用户、查看会话与 Agent 调用观测日志 |

## 核心能力

- **多智能体旅行对话**：LangGraph4j 编排意图识别、需求收集、路线规划、审核与最终答复；路线规划师和普通服务者均可按需调用旅行知识专家，路线规划师还可调用路线与预算专家。
- **Redis 语义缓存**：路线需求确认后，首次进入规划师前查询 Redis Stack 的 HNSW 向量索引；缓存默认保留 24 小时，命中后仍交给路线审核师复核。Redis 负责低延迟、短 TTL 的路线结果缓存，Qdrant 负责长期的 RAG 知识检索。
- **对话状态持久化**：LangGraph4j Checkpoint 使用 RedisSaver 保存 Human-in-the-loop 状态，Checkpoint 自动保留 7 天。
- **旅行知识 RAG**：采用“Qdrant 向量召回 + Qwen Rerank 重排”的两阶段检索链路，筛选高相关知识片段后注入旅行知识专家上下文。
- **RAG 知识库管理**：管理台支持多文件异步导入、文章级查看和 Chunk 级查看；文档与 Chunk 均支持启用/禁用，状态变化会同步删除或恢复 Qdrant 向量，禁用内容不会参与检索。
- **运营管理台**：React + TypeScript 管理微信用户、会话及 Agent 可观测日志。
- **微信小程序入口**：原生 WXML / WXSS / JavaScript，完成登录、聊天和历史会话。
- **安全认证**：微信登录与管理端登录统一使用 JWT，刷新令牌存储在 Redis。
- **Agent 可观测**：节点与专家调用的输入输出、Token、耗时和错误经 Kafka 异步写入 MySQL，仅在管理端会话详情展示。

## 系统架构

```mermaid
flowchart LR
    mini["微信小程序"]
    admin["React 管理台"]
    api["行迹 API（Spring Boot）"]
    workflow["LangGraph4j 多智能体编排"]
    rag["RAG 知识库模块"]
    mysql[("MySQL")]
    redis[("Redis")]
    kafka[("Kafka")]
    qdrant[("Qdrant 向量库")]
    model["DashScope 模型服务"]

    mini --> api
    admin --> api
    api --> workflow
    api --> rag
    workflow --> model
    workflow --> rag
    workflow --> kafka
    rag --> qdrant
    rag --> mysql
    rag --> model
    kafka --> rag
    kafka --> mysql
    api --> mysql
    api --> redis
```

## 多智能体协作

```mermaid
flowchart TD
    message(["用户消息和当前位置"])
    supervisor["总控：识别意图"]
    requirements["需求询问师"]
    subgraph routePlanning["旅游路线规划子图"]
        direction TB
        planner["路线规划师"]
        parallel["专家并行执行"]
        knowledge["旅行知识专家"]
        routeExpert["路线规划专家"]
        budget["预算专家"]
        reviewer["路线审核师"]
        planner -->|一次返回多个任务| parallel
        parallel -.-> knowledge
        parallel -.-> routeExpert
        parallel -.-> budget
        parallel -->|汇总结果| planner
        planner -->|生成方案| reviewer
        reviewer -->|需要修改且次数不超过 2 次| planner
    end
    normal["普通服务者"]
    finalize["最终答复编辑"]
    wait["awaitUserInput：等待用户输入"]
    response(["最终回复"])

    message -->|新会话| supervisor
    message -->|恢复 Checkpoint| requirements
    supervisor -->|路线规划| requirements
    supervisor -->|普通服务| normal
    requirements -->|已确认| planner
    requirements -->|待补充| finalize
    reviewer -->|审核通过| finalize
    reviewer -->|达到修改上限| finalize
    normal --> finalize
    finalize -->|需求未确认| wait
    finalize -->|结果已完成| response
    wait -->|下一轮消息和新位置| message
```

| 角色 | 工具与职责 | 启用条件 |
| --- | --- | --- |
| 总控 | 判断路线规划或普通服务 | 始终启用 |
| 路线需求询问师 | 收集并确认路线规划需求 | 路线规划 |
| 路线规划师 | 制定行程，一次返回多个专家任务 | 路线规划 |
| 路线审核师 | 审核行程；最多要求修改两次 | 路线规划 |
| 普通服务者 | 直接处理非路线规划问题 | 普通服务 |
| 最终答复编辑 | 将已完成结果整理为面向用户的自然回复 | 始终启用 |
| 旅行知识规划专员 | 提供旅行知识与目的地建议 | 并行按需调用 |
| 路线规划专员 | 提供路线与行程安排建议 | 并行按需调用 |
| 预算专员 | 汇总已知门票、住宿、餐饮与交通费用；缺失价格标记待确认 | 并行按需调用 |

三位专家统一返回结构化 JSON，由路线规划子图的并行执行节点按需调度；未被选中的专家不会执行。提示词位于 `src/main/resources/prompt/`，统一使用“角色、输入、输出、约束”结构。

## RAG 知识库

RAG 模块独立于 LangGraph 编排层，负责知识文档的导入、加工、向量化、检索和运营管理。路线规划师与普通服务者通过 Spring AI Tool 按需调用旅行知识专家；专家先从 Qdrant 召回候选 Chunk，再使用 Qwen Rerank 重排并筛选最终结果，最后将结构化知识上下文交给上层 Agent。

Redis 语义缓存与 Qdrant RAG 分工明确：Redis 只保存已审核的路线方案，利用内存查询和 TTL 快速复用相似需求；Qdrant 保存知识库 Chunk，支持较大规模的向量召回、过滤和持久化。两者不互相替代。

### RAG 检索链路

```mermaid
flowchart LR
    agent["路线规划师或普通服务者"]
    tool["Spring AI Tool"]
    rag["旅行知识专家"]
    qdrant[("Qdrant")]
    rerank["Qwen Rerank"]
    context["最终知识上下文"]

    agent --> tool
    tool --> rag
    rag -->|召回候选 Top-K| qdrant
    qdrant --> rerank
    rag --> rerank
    rerank -->|重排并筛选 Top-N| context
    context --> agent
```

RAG 检索使用两阶段参数：

- `TRAVEL_AGENT_RAG_RECALL_TOP_K`：Qdrant 初始召回数量，默认 `20`。
- `TRAVEL_AGENT_RAG_TOP_K`：Rerank 后最终注入数量，默认 `5`。
- `TRAVEL_AGENT_RAG_SIMILARITY_THRESHOLD`：Qdrant 初始相似度阈值。
- `TRAVEL_AGENT_RAG_RERANK_MODEL`：Rerank 模型，默认 `qwen3-rerank`。
- `TRAVEL_AGENT_RAG_RERANK_TOP_N`：Rerank 返回数量，默认 `5`。

向量中的正文、文档元数据和 Chunk 元数据用于帮助专家生成更准确的旅行知识结果。Rerank 是当前检索链路的必经步骤，调用失败会直接返回错误，不回退到未经重排的向量召回结果。

### RAG 文档导入流水线

导入接口只负责接收文件、保存任务和发送 Kafka 消息，不等待完整处理。Kafka 消费者使用固定顺序的节点流水线执行：

```mermaid
flowchart LR
    parser["PARSING - Apache Tika"]
    parser --> doc["DOC_ENRICHING - 文档元数据"]
    doc --> chunk["CHUNKING - 文本分块"]
    chunk --> chunkMeta["CHUNK_ENRICHING - Chunk 元数据"]
    chunkMeta --> vector["VECTORIZING - Embedding + Qdrant"]
```


## 技术栈

| 层次 | 技术 |
| --- | --- |
| Backend | Java 21、Spring Boot 4、Spring AI Alibaba、LangGraph4j、Spring Security、MyBatis |
| Data | MySQL、Redis、Kafka |
| RAG | Qdrant、DashScope Embedding、Qwen Rerank |
| Admin console | React 18、TypeScript、Vite |
| Mini program | 原生 JavaScript、WXML、WXSS |
| API docs | Springdoc OpenAPI、NextDoc4j |

## 快速开始

### 环境要求

- JDK 21+
- Node.js 18+
- MySQL、Redis
- Kafka
- Qdrant
- 阿里云百炼 DashScope API Key

### 1. 配置并启动后端

复制示例配置：

```powershell
Copy-Item src/main/resources/application.example.yml src/main/resources/application.yml
```

在 `application.yml` 中填写数据库、Redis、模型服务、微信、JWT 和 Kafka 配置。Kafka 是 Agent 可观测链路的必需依赖：

```env
SPRING_KAFKA_BOOTSTRAP_SERVERS=<Kafka 地址>:9092
```

启动 API：

```powershell
.\mvnw.cmd spring-boot:run
```

Linux / macOS：

```bash
./mvnw spring-boot:run
```

默认地址：`http://localhost:8080`

### 2. 启动管理台

```powershell
cd fe
Copy-Item .env.example .env
npm install
npm run dev
```

在 `fe/.env` 中设置 `VITE_API_BASE_URL=http://localhost:8080`。管理台默认地址：`http://localhost:5173`。

### 3. 导入微信小程序

1. 复制 `miniprogram/utils/config.example.js` 为 `config.js` 并填写后端地址。
2. 复制 `miniprogram/project.config.example.json` 为 `project.config.json` 并填写 AppID。
3. 使用微信开发者工具导入 `miniprogram/` 目录。

真机调试需要 HTTPS 地址，并在微信公众平台配置合法域名。

## LangGraph4j Studio

Studio 用于本地可视化、运行和调试多 Agent 工作流。设置后启动应用：

```env
TRAVEL_AGENT_STUDIO_ENABLED=true
```

访问 `http://localhost:8080/?instance=travel-agent`。输入框填写对话历史 JSON，例如：

```json
[{"role":"user","content":"帮我规划南京一日游"}]
```

Studio 会触发真实模型调用，仅建议本地开启。

## API 文档

后端启动后访问：

- OpenAPI UI：`http://localhost:8080/doc.html`
- OpenAPI JSON：`http://localhost:8080/v3/api-docs`

主要接口分组：

| 分组 | 示例 |
| --- | --- |
| 认证 | `POST /api/auth/wx/login`、`POST /api/auth/admin/login`、`POST /api/auth/refresh` |
| AI 助手 | `POST /api/agent/chat`、`POST /api/agent/sessions`、`GET /api/agent/sessions` |
| 运营管理 | `/api/admin/wx-users`、`/api/admin/sessions` |
| RAG 管理 | `POST /api/admin/rag/documents/import`、`GET /api/admin/rag/documents/import/tasks`、`GET /api/admin/rag/documents`、`GET /api/admin/rag/chunks` |
| RAG 状态管理 | `PATCH /api/admin/rag/documents/{id}/enable`、`PATCH /api/admin/rag/chunks/{id}/enable`、`PATCH /api/admin/rag/chunks/batch-enable` |

## 项目结构

```text
travel-agent/
├── src/                 Spring Boot API：认证、Agent、管理端与 RAG
│   ├── .../application/      用例、应用服务、DTO 与端口；按 agent/admin/auth/rag/planning 划分
│   ├── .../domain/           规划规则、RAG 分块规则、会话与账户领域模型
│   └── .../infrastructure/   LangGraph、Spring AI、JWT、MyBatis、Redis Stack、Kafka、Qdrant 与 Web 适配器
├── fe/                  React + TypeScript 管理台
├── miniprogram/         原生微信小程序
├── scripts/             部署与辅助脚本
├── pom.xml              Maven 构建配置
└── LICENSE              MIT License
```

## Docker 启动与部署

Docker Compose 会启动管理台、API、MySQL、Redis、Qdrant 和单节点 Kafka（KRaft）。API 在 Compose 网络内通过 `kafka:9092` 连接 Kafka，宿主机调试客户端可使用 `localhost:29092`。

```bash
git clone <仓库地址> travel-agent
cd travel-agent
cp .env.example .env
# 编辑 .env，填入数据库密码、模型 API Key、JWT 密钥和微信配置
docker compose up -d --build
```

如需连接外部 Kafka，将 `SPRING_KAFKA_BOOTSTRAP_SERVERS` 改为外部集群地址；Compose 内 Kafka 仍会启动，但 API 将使用外部地址。

查看状态和日志：

```bash
docker compose ps
docker compose logs -f
```

外网访问 `http://<服务器IP>/`，API 文档为 `http://<服务器IP>/doc.html`。安全组放行 `80` 端口。更新部署：

```bash
git pull
docker compose up -d --build
```

数据由 Docker volumes 持久化；不要执行 `docker compose down -v`，否则会删除 MySQL、Kafka、Qdrant 和 Redis 数据。
应用启动时会自动补充 RAG 状态字段；其他表结构仍由 `schema.sql` 在初始化阶段创建。

## 开发与测试

运行后端测试：

```powershell
.\mvnw.cmd test
```

检查并构建管理台：

```powershell
cd fe
npm run check
npm run build
```

## Agent 可观测

LangGraph4j 节点与专家的调用日志会由 Hook 直接发送至 Kafka `agent-observation`，消费者异步写入 `agent_observation_log`。记录通过 `message_id` 关联用户消息，包含 LLM 输入输出、模型、Token、耗时和错误信息；仅在管理端会话详情中展示，不会返回给用户端。Kafka 不可用时，应用无法正常启动或观测消息无法发送。

```env
SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092
```

## 开源协议

[MIT](LICENSE) · 联系方式：`ndakjin@qq.com`
