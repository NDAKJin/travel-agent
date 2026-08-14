# Docker 部署

默认配置为低并发部署：运行 API、管理台、MySQL、Redis、Elasticsearch 和 Neo4j。建议至少 4GB 内存；同时运行两个搜索/图数据库不适合 2GB 服务器。外网仅开放管理台和 API 共用的 HTTP 端口。

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

不要运行 `docker compose down -v`，它会删除 MySQL、Elasticsearch 和 Neo4j 的持久化数据。

ES 已按低负载设为 256MB 堆，Neo4j 使用 256MB 堆和 256MB 页缓存。启动前请设置 Elasticsearch 的系统参数：

```bash
sudo sysctl -w vm.max_map_count=262144
docker compose up -d --build
```

Neo4j 的 Browser/Bolt 仅绑定在服务器本机的 `7474`/`7687` 端口；远程管理请使用 SSH 隧道。Redis 在此轻量配置中不持久化，容器重启会使已登录用户需要重新登录。

小程序生产环境必须使用 HTTPS 域名：在 `miniprogram/utils/config.js` 填入该域名，并在微信公众平台登记为合法请求域名。上线前请轮换本机 `application.yml` 中已经使用过的模型、数据库、微信、高德和 JWT 密钥。
