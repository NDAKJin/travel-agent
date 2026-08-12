# Docker 部署

默认配置适合 2GB 服务器：运行 API、管理台、MySQL 和 Redis，不启动 Elasticsearch，因此不提供 RAG 知识库检索。外网仅开放管理台和 API 共用的 HTTP 端口。

```bash
git clone <your-repository-url> travel-agent
cd travel-agent
cp .env.example .env
openssl rand -base64 48
# 将生成的随机值和实际服务密钥填写到 .env
docker compose up -d --build
docker compose ps
```

首次启动会自动导入 `schema.sql`。访问 `http://<服务器IP>/`，接口文档在 `http://<服务器IP>/doc.html`。安全组只需放行 HTTP 端口（默认 `80`）。

更新：

```bash
git pull
docker compose up -d --build
```

不要运行 `docker compose down -v`，它会删除 MySQL 和 Redis 的持久化数据。

ES 已按低负载设为 384MB 堆，可在 2GB 服务器上尝试 RAG。将 `.env` 的 `TRAVEL_AGENT_AGENT_RAG_ENABLED` 设为 `true`，再启动：

```bash
sudo sysctl -w vm.max_map_count=262144
docker compose --profile rag up -d --build
```

如果 `docker stats` 显示内存持续接近 2GB，停止 RAG：

```bash
docker compose --profile rag stop elasticsearch
```

小程序生产环境必须使用 HTTPS 域名：在 `miniprogram/utils/config.js` 填入该域名，并在微信公众平台登记为合法请求域名。上线前请轮换本机 `application.yml` 中已经使用过的模型、数据库、微信、高德和 JWT 密钥。
