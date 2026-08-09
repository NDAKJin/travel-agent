# Travel Agent

一个面向旅行场景的 AI 助手项目，包含 Spring Boot 后端、React 管理前端和原生微信小程序。项目支持微信登录、AI 对话、会话历史、附近地点搜索、景区知识库检索以及景区和用户管理。

## 功能概览

- 微信小程序登录与用户会话管理
- 基于 Spring AI 的旅行问答与行程建议
- 会话创建、历史记录和上下文保存
- 基于用户当前位置的附近景点及文旅服务搜索
- 景区知识库 RAG 检索
- Elasticsearch 景区地理索引与知识向量索引
- React 管理后台
  - 微信用户查询
  - 对话记录查看
  - 景区信息维护
  - 景区知识文档录入
  - 高德地图地点搜索与选点

## 项目结构

```text
travel-agent/
├── src/                         # Spring Boot 后端
│   ├── main/java/.../auth/      # 登录、JWT、权限认证
│   ├── main/java/.../agent/     # AI 对话、工具调用、会话管理
│   ├── main/java/.../admin/     # 管理后台接口
│   ├── main/java/.../rag/       # 景区知识库与 RAG
│   └── main/resources/          # 配置和 MyBatis 映射文件
├── fe/                          # React + TypeScript 管理前端
├── miniprogram/                 # 原生微信小程序
├── scripts/start.sh             # Linux 生产环境启动脚本
├── pom.xml
└── mvnw / mvnw.cmd
```

## 技术栈

### 后端

- Java 21
- Spring Boot 4.1
- Spring AI 2.0
- Spring Security + JWT
- MyBatis
- MySQL / H2
- Redis（刷新令牌存储）
- Elasticsearch（景区地理检索与向量知识库）
- Maven Wrapper

### 前端

- React 18
- TypeScript
- Vite
- 高德地图 JS API

### 小程序

- 原生微信小程序 JavaScript / WXML / WXSS
- 微信 `wx.login()` 登录流程

## 环境要求

- JDK 21+
- Maven 3.9+（也可以使用项目自带的 Maven Wrapper）
- Node.js 18+
- MySQL
- Redis
- Elasticsearch
- 可用的 OpenAI 兼容模型服务
- 微信小程序 AppID 和 AppSecret
- 高德地图 Web 服务 Key；管理前端地图还需要 JS API Key

## 配置

项目不会在仓库中提交本地配置和密钥。请将 [`application.example.yml`](src/main/resources/application.example.yml) 复制为 `src/main/resources/application.yml`，再填写数据库、Redis、模型服务、微信和高德地图配置。`application.yml` 已被 `.gitignore` 忽略，不应提交到 Git。也可以通过环境变量覆盖示例文件中的配置。

后端常用配置可使用 Spring Boot 的环境变量命名方式：

| 配置项 | 环境变量示例 | 用途 |
| --- | --- | --- |
| 数据库 | `SPRING_DATASOURCE_URL` | MySQL JDBC 连接地址 |
| 数据库用户名 | `SPRING_DATASOURCE_USERNAME` | MySQL 用户名 |
| 数据库密码 | `SPRING_DATASOURCE_PASSWORD` | MySQL 密码 |
| Redis | `SPRING_DATA_REDIS_HOST` / `SPRING_DATA_REDIS_PORT` | 刷新令牌存储 |
| JWT 密钥 | `TRAVEL_AGENT_AUTH_JWT_SECRET` | JWT 签名密钥 |
| 微信 AppID | `TRAVEL_AGENT_AUTH_WX_APP_ID` | 微信登录 |
| 微信 Secret | `TRAVEL_AGENT_AUTH_WX_SECRET` | 微信登录 |
| 大模型 API Key | `TRAVEL_AGENT_AGENT_QWEN_API_KEY` 或 Spring AI 对应配置 | AI 对话与向量嵌入 |
| 高德 Web 服务 Key | `TRAVEL_AGENT_AMAP_WEB_SERVICE_KEY` | 后端附近地点搜索 |
| Elasticsearch | `TRAVEL_AGENT_AGENT_RAG_ELASTICSEARCH_HOST` / `..._PORT` | RAG 和地理索引 |

RAG 默认开启。如果本地暂时没有 Elasticsearch，可设置：

```bash
TRAVEL_AGENT_AGENT_RAG_ENABLED=false
```

管理前端配置位于 `fe/.env`，可参考 [`fe/.env.example`](fe/.env.example)：

```dotenv
VITE_API_BASE_URL=http://localhost:8080
VITE_AMAP_KEY=your_amap_web_js_key
VITE_AMAP_SECURITY_JS_CODE=your_amap_security_js_code
```

微信小程序后端地址配置在 [`miniprogram/utils/config.js`](miniprogram/utils/config.js)。真机调试时需要使用可访问的 HTTPS 地址，并在微信公众平台配置合法域名。

## 启动后端

先准备 MySQL、Redis、Elasticsearch 以及模型服务，并完成环境变量配置。

Windows：

```powershell
.\mvnw.cmd spring-boot:run
```

Linux / macOS：

```bash
./mvnw spring-boot:run
```

也可以先构建 JAR：

```bash
./mvnw clean package
java -jar target/travel-agent-0.0.1-SNAPSHOT.jar
```

生产环境可使用 [`scripts/start.sh`](scripts/start.sh)。脚本默认从 `/opt/travel-agent/app.jar` 启动，并读取同目录下的 `.env` 文件。

## 启动管理前端

```bash
cd fe
npm install
npm run dev
```

默认开发地址为 `http://localhost:5173`。

常用命令：

```bash
npm run check   # TypeScript 类型检查
npm run build   # 构建生产文件
npm run preview # 预览生产构建
```

## 启动微信小程序

1. 安装并打开微信开发者工具。
2. 导入项目目录 `miniprogram/`。
3. 将 `miniprogram/utils/config.js` 中的后端地址改为实际可访问地址。
4. 使用与 `miniprogram/project.config.json` 一致的 AppID，或替换为自己的 AppID。
5. 在后端配置微信 AppID 和 AppSecret。

小程序当前仅保留微信授权登录入口，登录成功后进入 AI 助手聊天页面。

## 测试

运行后端测试：

```bash
./mvnw test
```

运行前端类型检查和构建：

```bash
cd fe
npm run check
npm run build
```

## License

本项目采用 [MIT License](LICENSE) 开源。
