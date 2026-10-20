# 星析 Agent · StarAnaly

一个面向智能任务执行与数据分析的 AI Agent 项目。当前已实现任务 API 的第一步：创建任务、保存到 MySQL、按 ID 查询和分页列出任务。模型调用、工具调用、知识检索、数据分析和前端界面仍在规划中。

## 当前实现

- Java 21、Spring Boot 3.5、Maven 后端。
- MySQL 持久化；应用启动时由 Flyway 执行数据库迁移。
- Docker Compose 提供 MySQL 和 Redis。当前任务 API 只使用 MySQL，Redis 尚未接入应用。
- `POST /api/tasks`、`GET /api/tasks/{id}` 和 `GET /api/tasks`。

## 本地运行

需要 Java 21、Maven 3.6.3 或更新版本，以及 Docker Compose。

1. 复制 `.env.example` 为 `.env`，修改三个密码。`.env` 已被 Git 忽略。
2. 启动 MySQL：

   ```bash
   docker compose up -d mysql
   ```

3. 从仓库根目录启动应用。应用会读取当前目录的 `.env`：

   ```bash
   mvn spring-boot:run
   ```

应用默认连接 `localhost:3307` 上的 `staranaly` 数据库，并使用 `.env` 中的 `MYSQL_USER`、`MYSQL_PASSWORD`。也可以通过环境变量提供这些配置。如果数据库不在本机，可以设置 `DB_HOST`、`DB_PORT` 和 `DB_NAME`。首次启动时 Flyway 会创建 `tasks` 表。

MySQL 官方镜像只在首次初始化数据目录时创建 `MYSQL_USER`。如果已有旧的 `mysql-data` 卷，需在现有数据库中手动创建该用户并授予 `staranaly` 数据库权限，或使用全新数据卷；不要删除仍需保留数据的卷。

## API 示例

创建任务：

```bash
curl -i -X POST http://localhost:8080/api/tasks \
  -H 'Content-Type: application/json' \
  -d '{"title":"分析销售额","description":"按月汇总"}'
```

成功时返回 `201 Created`，`Location` 指向新任务。新任务状态为 `PENDING`，`createdAt` 使用 UTC 时间。

查询任务：

```bash
curl http://localhost:8080/api/tasks/1
curl 'http://localhost:8080/api/tasks?page=0&size=20'
```

任务不存在时返回 `404`；标题为空或超过 200 字符、描述超过 5000 字符时返回 `400`。

## 测试

```bash
mvn test
```

当前接口测试使用 MockMvc 和模拟的服务，不需要启动 MySQL。应用实际启动仍需要 MySQL，以便执行 Flyway 迁移和校验表结构。

## 后续规划

1. 接入模型服务，让任务从 `PENDING` 进入实际执行流程，并保存执行结果。
2. 增加工具调用、知识检索与数据分析能力。
3. 建立 Vue 3 + TypeScript 工作空间界面。

本项目采用 [MIT License](LICENSE)。
