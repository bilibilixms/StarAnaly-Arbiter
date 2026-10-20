# StarAnaly 第 1 周基线验证结果（2026-10-20）

> 本地运行验证，覆盖 README 中所有后端能力与前端构建；对话接口因未配模型密钥按预期返回 503。

## 环境

| 项 | 值 |
| --- | --- |
| 操作系统 | WSL2（Ubuntu，内核 6.18.33.2-microsoft-standard-WSL2） |
| Java | OpenJDK 21.0.12.1（Eclipse Adoptium Temurin） |
| Maven | 3.9.16 |
| Node.js | v24.22.0 |
| Docker Desktop | 29.8.0 |
| Docker Compose | v5.5.1 |
| MySQL 容器镜像 | `docker.m.daocloud.io/library/mysql:8.0`（8.0.46） |
| 后端分支 | `main` 与 `origin/main` 同步，HEAD = `d02e0fc` |

本次拉取引入的 4 个新提交（184e56f..d02e0fc）：

- `580399f` feat: 搭建前端任务工作台
- `3b1681a` feat: 接入模型对话与任务执行状态
- `d7f8ff0` docs: 制定八周开发规划说明书
- `d02e0fc` chore: 准备首周基线验证与模型能力探测

## 数据库

MySQL 容器 `staranaly-mysql` 端口映射 `3307:3306`，容器内执行：

- MySQL 连接健康：`healthy`
- Flyway V1 `create_tasks`：已在上一轮执行
- Flyway V2 `task_conversation`：本次启动时应用执行，日志 `Successfully applied 1 migration to schema staranaly, now at version v2`
- 最终表：`flyway_schema_history`、`tasks`、`task_messages`

`task_messages` 表结构：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `bigint PK auto_increment` | 主键 |
| `task_id` | `bigint` | 外键（索引） |
| `role` | `varchar(20)` | USER / ASSISTANT |
| `content` | `varchar(10000)` | 消息正文 |
| `created_at` | `datetime(6)` | 创建时间 |

## 后端测试

`mvn -B test`：**BUILD SUCCESS**，11 个测试全部通过。

| 测试类 | 测试数 | 备注 |
| --- | --- | --- |
| `TaskControllerTest` | 5 | 任务创建 / 查询 / 分页 / 校验 |
| `TaskConversationControllerTest` | 4 | 对话发送 / 并发 409 / 校验 |
| `TaskConversationStateTest` | 2 | `PENDING → RUNNING → SUCCEEDED / FAILED` 状态机 |

## 后端启动

`mvn -B spring-boot:run`：3.895s 启动完成，Tomcat 监听 `8080`，Flyway 执行 V2 迁移。无 Model 相关异常（MODEL_API_KEY / MODEL_NAME 未配置）。

## API 验证

所有请求 `Host: localhost:8080`。

### 创建任务

```
POST /api/tasks
Content-Type: application/json

{"title":"周验证任务","description":"后端+数据库通路验证"}
```

响应：`201 Created`，`Location: /api/tasks/2`

```json
{"id":2,"title":"周验证任务","description":"后端+数据库通路验证","status":"PENDING","createdAt":"2026-10-20T08:20:17.264623527Z","lastError":null}
```

### 查询任务详情

```
GET /api/tasks/1    → 200
GET /api/tasks/2    → 200
```

### 分页列表

```
GET /api/tasks?page=0&size=10   → 200，totalElements=2
```

（id=1 的任务来自上一轮验证；两轮重启后均在，MySQL 持久化正常）

### 对话接口（未配模型，预期 503）

```
POST /api/tasks/1/messages
Content-Type: application/json

{"content":"请总结一下"}
```

响应：`503 Service Unavailable`，符合 README 契约：

```json
{"type":"about:blank","title":"Service Unavailable","status":503,"detail":"模型服务尚未配置 MODEL_API_KEY 和 MODEL_NAME.","instance":"/api/tasks/1/messages"}
```

## 前端构建

`frontend/` 目录下：`npm install && npm run build` 全部通过。

- `vue-tsc --noEmit`：0 错误
- `vite build`：产物生成至 `frontend/dist/`，主包 92 KB（gzip 36 KB）

未启动 `npm run dev`，无浏览器访问验证。

## 未完成项

| 项 | 原因 |
| --- | --- |
| `scripts/verify_baseline.py` | 依赖后端与模型；未配 `MODEL_API_KEY` / `MODEL_NAME` 会在对话阶段失败 |
| `scripts/probe_model_tools.py` | 需要模型密钥；脚本会探测目标模型是否支持 Chat Completions `tool_calls` 格式 |
| 前端页面浏览（`npm run dev` + 浏览器） | 本轮仅验证类型检查与构建；待后端长期运行后再完整走一遍创建 / 详情 / 对话 / 刷新流程 |

## 结论

后端基础能力（任务 CRUD + 分页）、Flyway V1 + V2 迁移、Hibernate DDL 校验、对话路由 503 语义、状态机、前端 TypeScript 与 Vite 构建均符合 README 与第 1 周基线文档的契约。唯一未验证路径为「已配模型的对话调用」，这需要模型供应商账号。
