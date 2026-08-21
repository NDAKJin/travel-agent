# Docker 部署

```bash
cp .env.example .env
docker compose up -d --build
docker compose ps
```

必填：MySQL 密码、DashScope API Key、JWT 密钥、Kafka 地址。

在 `.env` 配置 Kafka：

```env
SPRING_KAFKA_BOOTSTRAP_SERVERS=<Kafka地址>:9092
```

更新部署：

```bash
git pull
docker compose up -d --build
```

不要执行 `docker compose down -v`，它会删除 MySQL 和 Redis 数据卷。
