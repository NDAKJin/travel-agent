# Java 开发手册逐条审计

依据《阿里巴巴 Java 开发手册（终极版）v1.3.0》对当前项目进行审计。

## 已处理

| 章节 | 条目 | 处理结果 |
| --- | --- | --- |
| 编程规约-命名 | 常量使用全大写下划线命名，避免魔法值 | Qdrant、稀疏向量编码相关参数已集中为语义化常量 |
| 编程规约-代码格式 | 条件分支使用大括号 | `KnowledgeRagService` 空单行分支已补充大括号 |
| 编程规约-OOP | 单构造器类不使用多余的 `@Autowired` | `KnowledgeRagService` 已移除多余注解 |
| 异常处理 | 捕获异常必须处理，不能静默丢弃 | RAG JSON 解析、缓存降级、Qdrant 初始化失败均增加上下文日志 |
| 异常处理 | 使用 try-with-resources 管理资源 | Redis 连接访问保持 try-with-resources |
| 日志规约 | 使用 SLF4J 占位符和合适日志级别 | 新增日志均使用 SLF4J，降级场景使用 debug |
| 单元测试 | 测试放在 `src/test/java` 且使用断言 | 稀疏编码器增加正常输入、空输入测试 |
| 并发处理 | 线程池使用有意义的线程名称 | 路由专家、缓存线程池均已有明确前缀 |
| 并发处理 | 避免共享可变对象 | Jieba 分词器改为 `ThreadLocal` |
| POJO | 提供可诊断的对象表示 | 对不含凭据的配置/展示对象保留 Lombok 生成能力；敏感对象避免直接输出完整内容 |

## 需要单独迁移评审

| 章节 | 条目 | 当前情况 |
| --- | --- | --- |
| 数据库建表 | 禁止外键和级联 | 当前 schema 使用外键与 `ON DELETE CASCADE`，需要数据库迁移脚本和应用层补偿逻辑，未在本次格式优化中直接删除 |
| 数据库建表 | `is_xxx` 字段、unsigned 类型、`gmt_create/gmt_modified` | 当前表使用 `enabled`、`created_at/updated_at`，涉及 ORM、接口和历史数据兼容，需要单独版本迁移 |
| 数据库建表 | 小数使用 decimal | 当前业务金额主要以文本/JSON 形式传递，需先明确持久化金额字段再迁移 |
| 安全规约 | 敏感数据脱敏 | 管理端展示链路需结合产品字段确认脱敏范围，不能仅通过格式化自动修改 |
| 注释规约 | 所有类/方法补齐创建者和日期 | 这是历史元数据补录，批量添加会制造噪声，建议在新增或修改的公开 API 中逐步补齐 |

## 不适用或已满足

- 项目未发现 `System.out`、`printStackTrace` 或 `SimpleDateFormat` 的业务使用。
- 数组声明统一使用 `String[]` 风格。
- 线程池均通过显式配置的 `ThreadPoolTaskExecutor` 创建，没有使用 `Executors` 工厂。
- 生产代码未发现通过字符串拼接构造 SQL 的调用；MyBatis/JdbcTemplate 使用参数绑定。
- 前端模板不使用 Velocity，相关条目不适用。

## 验证

```text
./mvnw.cmd -q -DskipTests compile
```

编译通过。数据库结构和安全字段迁移需在独立变更中完成并配套回滚方案。
