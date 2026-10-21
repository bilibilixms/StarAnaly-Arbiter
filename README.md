# 星析 Agent · StarAnaly

一个面向智能任务执行与数据分析的 AI Agent 项目。当前已实现任务创建、MySQL 持久化、模型对话、受控工具、CSV 分析和基础资料检索；前端任务页面仍待接入文件与结果展示。

## 当前实现

- Java 21、Spring Boot 3.5、Maven 后端。
- MySQL 持久化；应用启动时由 Flyway 执行数据库迁移。
- Docker Compose 提供 MySQL 和 Redis。当前任务 API 只使用 MySQL，Redis 尚未接入应用。
- 任务创建、单条查询、分页列表，以及按任务保存的对话消息。
- 每次对话对应一条执行记录；支持异步提交、状态查询和步骤记录。
- 模型可调用受控的只读整数加法工具，工具请求、结果与最终回答均有步骤记录。
- 支持上传 CSV、预览和筛选数据、分组求和，并通过模型工具保存结构化分析结果。
- 支持上传 TXT/Markdown 资料，检索原文片段，记录执行中的来源引用，并删除资料。
- 兼容 OpenAI Chat Completions 的模型调用；任务状态包含 `PENDING`、`RUNNING`、`SUCCEEDED`、`FAILED`。
- `frontend/` 中的 Vue 3 + TypeScript 工作台，可创建任务、查看任务和继续对话。

## 本地运行

需要 Java 21、Maven 3.6.3 或更新版本、Docker Compose，以及用于前端的 Node.js 22.18.x 或 24.12 及以上版本。

运行前让 `docker compose` 和 Maven 进程能读取以下环境变量：

| 变量 | 用途 |
| --- | --- |
| `MYSQL_ROOT_PASSWORD` | MySQL 初始化时的 root 密码 |
| `MYSQL_PASSWORD` | 应用使用的 MySQL 账号密码 |
| `MYSQL_USER` | 应用使用的 MySQL 用户，默认 `staranaly` |
| `REDIS_PASSWORD` | 启动 Redis 时使用；只启动 MySQL 时暂不需要 |
| `MODEL_NAME` | 模型名称，默认 `deepseek-flash` |
| `MODEL_API_KEY` | 模型服务密钥；设置后优先使用 |
| `DEEPSEEK_API_KEY` | DeepSeek 密钥；未设置 `MODEL_API_KEY` 时使用 |
| `MODEL_BASE_URL` | 模型 API 基础地址，默认 `https://api.deepseek.com` |

如果密码已配置在机器的环境变量中，无需创建 `.env`。本地开发也可复制 `.env.example` 为 `.env` 并填写密码；该文件已被 Git 忽略。两者同时存在时，环境变量优先。

项目默认使用 [DeepSeek 官方 Chat Completions 接口](https://api-docs.deepseek.com/guides/search_fetch)：在运行后端的环境中设置 `MODEL_API_KEY` 或 `DEEPSEEK_API_KEY` 即可。若使用其他 DeepSeek 模型或兼容服务，再覆盖 `MODEL_NAME`、`MODEL_BASE_URL`。密钥不要提交到仓库。

1. 启动 MySQL：

   ```bash
   docker compose up -d mysql
   ```

2. 等待 MySQL 健康检查通过，然后从仓库根目录启动应用：

   ```bash
   mvn spring-boot:run
   ```

3. 在另一个终端启动前端：

   ```bash
   cd frontend
   npm install
   npm run dev
   ```

浏览器打开 `http://localhost:5173`。前端开发服务器会将 `/api` 请求转发至 `http://localhost:8080`；如果后端位于其他地址，启动前端时可设置 `BACKEND_URL` 环境变量。数据库和模型密钥都只由后端读取。

应用默认连接 `localhost:3307` 上的 `staranaly` 数据库，并使用 `MYSQL_USER`、`MYSQL_PASSWORD`。如果数据库不在本机，可以设置 `DB_HOST`、`DB_PORT` 和 `DB_NAME`。首次启动时 Flyway 会创建任务、对话消息和执行记录表。

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

向任务发送消息并获取模型回复：

```bash
curl -X POST http://localhost:8080/api/tasks/1/messages \
  -H 'Content-Type: application/json' \
  -d '{"content":"请分析这项任务"}'
curl http://localhost:8080/api/tasks/1/messages
```

第一次发送消息时，任务标题和说明会作为上下文传给模型。后续请求会带上最近 20 条对话消息。调用期间任务为 `RUNNING`，成功后为 `SUCCEEDED`；失败时为 `FAILED`，任务详情中的 `lastError` 会记录原因。同一任务正在执行时再次发送消息返回 `409`。同步发送接口最多等待约 120 秒，单次模型请求最多 90 秒。模型服务采用 [Chat Completions 请求格式](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create)。

需要立即获得执行编号时，使用异步接口：

```bash
curl -i -X POST http://localhost:8080/api/tasks/1/runs \
  -H 'Content-Type: application/json' \
  -d '{"content":"请继续分析"}'
curl http://localhost:8080/api/tasks/1/runs
curl http://localhost:8080/api/tasks/1/runs/执行编号
curl http://localhost:8080/api/tasks/1/runs/执行编号/steps
```

异步创建返回 `202 Accepted` 和执行详情地址；将示例中的“执行编号”换成响应中的 `id`。执行结果通过详情及原有消息接口查看。完整契约和验证命令见 [第 2 周执行记录说明](docs/week-2-execution.md)。

第 3 周只读工具闭环可用 `python3 scripts/verify_tool_loop.py` 验证，限制与步骤说明见 [第 3 周工具闭环说明](docs/week-3-tool-loop.md)。

CSV 上传与按月汇总可用 `python3 scripts/verify_csv.py` 验证，接口和文件限制见 [第 4 周 CSV 说明](docs/week-4-csv.md)。

资料检索与来源引用可用 `python3 scripts/verify_knowledge.py` 验证，接口和限制见 [第 5 周资料检索说明](docs/week-5-knowledge.md)。

## 测试

```bash
mvn test
```

当前接口测试使用 MockMvc 和模拟的服务，不需要启动 MySQL 或连接模型。应用实际启动仍需要 MySQL，以便执行 Flyway 迁移和校验表结构；使用对话功能还需配置 `MODEL_API_KEY` 或 `DEEPSEEK_API_KEY`。

前端可在 `frontend/` 中运行 `npm run build`，该命令会检查 TypeScript 并生成静态文件。部署静态文件时，需要将 `/api` 转发到后端，并将前端路由回退到 `index.html`。

## 后续规划

7～9 周的阶段目标、每周交付和验收口径见 [开发规划说明书](docs/development-plan.md)。
第 1 周的验证命令、固定演示数据和执行方案见 [基线核对与执行方案](docs/week-1-baseline.md)。

1. 在用户测试机验收第 5 周资料检索与来源引用。
2. 扩展工作空间界面，展示上传资料、执行步骤、分析结果与引用。

本项目采用 [MIT License](LICENSE)。
