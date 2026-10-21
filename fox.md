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

## 17:20 追加：默认接入 DeepSeek 后全链路验证（d494299）

> 上一轮验证后继续拉取，新提交 `d494299 feat: 默认接入 DeepSeek 并适配工具探测` 把模型默认地址改为 `https://api.deepseek.com`、默认模型 `deepseek-flash`，同时支持 `DEEPSEEK_API_KEY` 环境变量回退，并修正了 Gateway 缺少 key 时的错误信息。

### 环境

- 后端 HEAD = `d494299`（1a08936 → d494299）
- 本地 `.env` 追加 `DEEPSEEK_API_KEY`（真实密钥；已被 `.gitignore` 忽略，不上传）
- `application.yml`：
  - `base-url: ${MODEL_BASE_URL:https://api.deepseek.com}`
  - `api-key: ${MODEL_API_KEY:${DEEPSEEK_API_KEY:}}`
  - `name: ${MODEL_NAME:deepseek-flash}`

### `mvn test`

11 项全绿，无回归。

### 后端启动

`mvn -B spring-boot:run`：3.9s 启动。Flyway `Schema staranaly is up to date. No migration necessary.` Tomcat 8080。

### 任务 API

创建 `POST /api/tasks` → `201 Created`，`Location: /api/tasks/3`。

分页 `GET /api/tasks?page=0&size=5` 三条：id=1 `FAILED`（历史调用未配 key）、id=2 `PENDING`、id=3 `PENDING`。

### 对话接口（首次配 key，真模型调用）

```
POST /api/tasks/3/messages
Content-Type: application/json

{"content":"用一句话自我介绍"}
```

响应：`200 OK`

```json
{"id":2,"role":"ASSISTANT","content":"我是星析项目的任务助手，负责协助你完成验证与任务支持。","createdAt":"2026-10-20T09:25:22.093631158Z"}
```

任务状态机符合契约：

- `GET /api/tasks/3` → `"status":"SUCCEEDED","lastError":null`
- `GET /api/tasks/1` → `"status":"FAILED","lastError":"模型服务尚未配置 MODEL_API_KEY 和 MODEL_NAME。"`（保留历史）

消息持久化：`GET /api/tasks/3/messages` 返回 USER + ASSISTANT 两条，时间戳连续。

### probe_model_tools.py（工具调用探测）

后端日志已确认工具调用路径走通，现在独立跑脚本（脚本**不读 `.env`**，需要 shell 环境变量）：

```bash
set -a; . .env; set +a
python3 scripts/probe_model_tools.py
```

输出：

```
[通过] 模型返回 1 个有效工具调用
[通过] 工具结果回传后得到文本回复
模型工具调用探测完成；脚本未执行任何外部工具或写入操作
```

DeepSeek 对 `tool_choice` + `tools` 格式的响应、以及回传工具结果后的二次回复均符合 OpenAI Chat Completions 契约。脚本内部禁用了思考模式（`thinking: {type: disabled}`），避免 `deepseek-*` 默认思考态消耗 token 后才返回工具调用。

### 本轮确认的差异点

| 项 | 行为 |
| --- | --- |
| `.env` 被 Spring Boot 读取 | 是：`spring.config.import: optional:file:./.env[.properties]`，后端自动拿到 key |
| Python 脚本读 `.env` | **否**：`probe_model_tools.py` / `verify_baseline.py` 只认 shell 环境变量（README 与脚本注释均已说明） |
| Gateway 错误信息变更 | 从 `模型服务尚未配置 MODEL_API_KEY 和 MODEL_NAME。` 拆成两条，分别提示 key 与 name；key 路径新增 `DEEPSEEK_API_KEY` |
| 任务状态机跨重启持久化 | 是：id=1 的 `FAILED` 在后续 `mvn spring-boot:run` 后仍保持 |
| `.env` 被 Git 忽略 | 是：`git check-ignore -v .env` 命中 `.gitignore:35:.env` |

## 结论

后端基础能力（任务 CRUD + 分页）、Flyway V1 + V2 迁移、Hibernate DDL 校验、对话路由 503 语义、状态机、前端 TypeScript 与 Vite 构建均符合 README 与第 1 周基线文档的契约。唯一未验证路径为「已配模型的对话调用」，这需要模型供应商账号。

**本轮追加结论（d494299）**：配置 `DEEPSEEK_API_KEY` 后，`POST /tasks/{id}/messages` 成功拿到模型回复，任务状态正确转移为 `SUCCEEDED`，对话消息持久化；`probe_model_tools.py` 通过工具调用往返探测。默认接入 DeepSeek 的改动闭环完成。

---

# 第 2 周验证报告（2026-10-21，代码 c32ec07）

> 依据 [docs/week-2-execution.md](docs/week-2-execution.md)：异步执行记录 + 步骤持久化。

## 环境

| 项 | 值 |
| --- | --- |
| 代码版本 | `c32ec07 新增任务执行记录与异步查询接口` |
| 数据库 | MySQL 8.0，容器 `staranaly-mysql`，`healthy`，端口 3307 |
| 模型 | DeepSeek（`DEEPSEEK_API_KEY` 由本地 `.env` 注入，未入库） |
| 应用 | `mvn -B spring-boot:run`，Tomcat 8080，启动 4.0s |

启动前 Docker 引擎未运行，先拉起 Docker Desktop，等 `docker info` 可用后容器自动恢复为 healthy。

## 1. Flyway V3 迁移

```
Migrating schema `staranaly` to version "3 - task runs"
Successfully applied 1 migration to schema `staranaly`, now at version v3 (execution time 00:00.086s)
```

`task_runs`、`task_run_steps` 两张表创建成功，启动后 `Tomcat started on port 8080`、`Started StarAnalyApplication`。

## 2. `mvn test`

```
Tests run: 5, Failures: 0, Errors: 0 -- TaskControllerTest
Tests run: 3, Failures: 0, Errors: 0 -- TaskRunControllerTest
Tests run: 4, Failures: 0, Errors: 0 -- TaskConversationControllerTest
Tests run: 2, Failures: 0, Errors: 0 -- TaskConversationServiceTest
Tests run: 4, Failures: 0, Errors: 0 -- TaskConversationStateTest
Tests run: 18, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

共 18 项，含新增的 3 项 `TaskRunControllerTest`。

## 3. `scripts/verify_runs.py`

```bash
cd scripts && python3 verify_runs.py
```

```
[通过] 创建任务 #4 与执行 #1
[通过] 执行状态转为 SUCCEEDED
[通过] 模型调用与最终回复步骤已保存
[通过] 执行列表、任务状态与对话记录一致
exit=0
```

覆盖：`202` 创建、轮询至 `SUCCEEDED`、`finishedAt` 非空、步骤 `[MODEL_CALL, FINAL_ANSWER]` 顺序与状态、执行列表可检索、任务状态与对话记录同步。

## 4. 旧同步接口 + 并发 409

新建任务 #5，依次验证：

| 场景 | 请求 | 结果 |
| --- | --- | --- |
| 同步对话 | `POST /api/tasks/5/messages` | `200`，`role=ASSISTANT`，内容「同步接口正常。」 |
| 并发第 1 次 | `POST /api/tasks/5/runs` | `202`，`{"id":3,"taskId":5,"status":"RUNNING",...}` |
| 并发第 2 次 | 同上（间隔 0.2s） | `409 Conflict`，`path=/api/tasks/5/runs` |
| 空白内容 | `POST /api/tasks/5/runs`，`content="   "` | `400` |
| 任务不存在 | `POST /api/tasks/99999/runs` | `404` |

异步执行 #3 完成后详情与步骤：

```json
{
  "id": 3, "taskId": 5, "status": "SUCCEEDED",
  "requestSummary": "用户消息，27 字符", "lastError": null,
  "startedAt": "2026-10-21T01:35:11.005128Z",
  "finishedAt": "2026-10-21T01:35:16.340713Z"
}
```

| stepNumber | kind | status | inputSummary | outputSummary |
| --- | --- | --- | --- | --- |
| 1 | MODEL_CALL | SUCCEEDED | 请求模型生成回复 | 模型返回 468 字符 |
| 2 | FINAL_ANSWER | SUCCEEDED | 保存最终回复 | 回复已保存 |

执行列表 `GET /api/tasks/5/runs?page=0&size=20` 返回 2 条（#3 本轮异步、#2 同步接口产生的记录），均 `SUCCEEDED`，按编号降序。

## 未覆盖项

- **重启恢复**：文档描述「重启时遗留 RUNNING 记录转为 FAILED」，本轮未构造该场景，未验证。
- **步骤详情中的工具字段**：第 2 周仅 `MODEL_CALL`/`FINAL_ANSWER`，`toolName` 均为 null，符合本阶段范围；工具步骤留待第 3 周。

## 第 2 周结论

`docs/week-2-execution.md` 列出的验收项全部通过：V3 迁移成功、18 项测试全绿、`verify_runs.py` 四项断言通过、同步接口未回归、同一任务并发提交返回 `409`、无效内容 `400`、任务不存在 `404`。异步执行记录与步骤持久化符合 API 契约。

## 环境备注

- 验证结束已停止应用（8080 释放），MySQL / Redis 容器保持运行，数据保留。
- 本轮未提交代码改动，仅追加本报告。

---

# 第 3 周验证报告（2026-10-21，代码 dba7efa）

> 依据 [docs/week-3-tool-loop.md](docs/week-3-tool-loop.md)：只读工具调用闭环。

## 环境

| 项 | 值 |
| --- | --- |
| 代码版本 | `dba7efa 接入只读工具调用并记录执行步骤` |
| 数据库 | MySQL 8.0，容器 `staranaly-mysql`，`healthy` |
| 迁移 | 无新增：`Schema staranaly is up to date. No migration necessary.`（V3 已含工具字段） |
| 应用 | 启动 3.5s，Tomcat 8080 |

## 1. `mvn test`（发现并修复 1 项失败）

首次运行 24 项测试，1 项失败：

```
TaskConversationServiceTest.recordsToolRequestResultAndFinalReply:87
```

失败原因：测试第 87 行断言工具执行后的第二次模型请求 `allowTools=false`，而实现中 `allowTools = round < MAX_MODEL_ROUNDS && toolCount < MAX_TOOL_CALLS` 在 round=2、toolCount=1 时为 `true`，同一断言匹配到 2 次调用。

判断依据：文档的步骤序列为 `MODEL_CALL → TOOL_CALL → TOOL_RESULT → MODEL_CALL → FINAL_ANSWER`，且测试断言明确要求工具结果回传后不再放行新工具，因此以测试与文档为准调整实现：

```java
// TaskConversationService.java:90
- boolean allowTools = round < MAX_MODEL_ROUNDS && toolCount < MAX_TOOL_CALLS;
+ boolean allowTools = round == 1;
```

修改后重跑：

```
Tests run: 24, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

分布：`TaskStartupRecoveryTest` 1、`ReadOnlyToolRegistryTest` 2、`TaskControllerTest` 5、`TaskRunControllerTest` 3、`TaskConversationControllerTest` 4、`TaskConversationServiceTest` 5、`TaskConversationStateTest` 4。

## 2. `scripts/verify_tool_loop.py`

```bash
cd scripts && python3 verify_tool_loop.py
```

```
[通过] 创建任务 #6 与执行 #4
[通过] 模型调用、工具请求、工具结果及最终回答步骤已保存
[通过] 工具结果已回传模型并形成最终回答
exit=0
```

执行 #4 步骤（`GET /api/tasks/6/runs/4/steps`）：

| stepNumber | kind | toolName | status | inputSummary | outputSummary |
| --- | --- | --- | --- | --- | --- |
| 1 | MODEL_CALL | - | SUCCEEDED | 请求模型生成回复 | 模型请求 1 个工具 |
| 2 | TOOL_CALL | sum_two_numbers | SUCCEEDED | 模型请求工具 sum_two_numbers | 参数校验通过 |
| 3 | TOOL_RESULT | sum_two_numbers | SUCCEEDED | 整数加法：a=2，b=3 | 计算结果：5 |
| 4 | MODEL_CALL | - | SUCCEEDED | 提交工具结果并请求模型回复 | 模型返回 37 字符 |
| 5 | FINAL_ANSWER | - | SUCCEEDED | 保存最终回复 | 回复已保存 |

执行详情：`SUCCEEDED`，耗时约 1.9 秒。最终回复：「已调用 sum_two_numbers 计算 a=2、b=3，结果为 5。」

## 3. 旧接口回归

| 脚本 | 结果 |
| --- | --- |
| `python3 scripts/verify_runs.py` | 4 项全部通过（任务 #7 / 执行 #5），exit=0 |
| `python3 scripts/verify_baseline.py` | 3 项全部通过（任务 #8），exit=0 |

两个脚本需 shell 环境变量（不读 `.env`），运行时用 `set -a; . .env; set +a` 注入。

## 4. 服务重启恢复实测

在本地测试库构造遗留执行：

1. `POST /api/tasks` 创建任务 #9（PENDING）。
2. SQL 直接写入 `task_runs`（`RUNNING`）与 `task_run_steps`（`MODEL_CALL`/`RUNNING`），并把 `tasks.status` 置为 `RUNNING`。
3. 确认构造后 API 返回：任务 `RUNNING`、执行 #7 `RUNNING`。
4. 停止并重启应用。

重启后结果：

| 对象 | 状态 | 结束时间 | lastError |
| --- | --- | --- | --- |
| 任务 #9 | `FAILED` | - | 服务重启，任务执行中断。请重新发送消息。 |
| 执行 #7 | `FAILED` | 2026-10-21T02:23:12.123308Z | 服务重启，任务执行中断。请重新发送消息。 |
| 步骤 #1 | `FAILED` | 2026-10-21T02:23:12.123217Z | - |

重启日志：`Schema staranaly is up to date. No migration necessary.`、`Started StarAnalyApplication in 3.451 seconds`。第 2 周报告中未覆盖的重启恢复场景本轮已实测通过。

## 本轮代码改动

| 文件 | 改动 |
| --- | --- |
| [TaskConversationService.java](src/main/java/com/staranaly/arbiter/task/TaskConversationService.java#L90) | `allowTools` 改为仅在首轮放行，修复新增测试与实现不一致 |

除该 1 行外无其他代码改动；测试文件未修改。

## 第 3 周结论

`docs/week-3-tool-loop.md` 的验收项全部通过：24 项测试全绿、`verify_tool_loop.py` 三阶段断言通过且步骤完整记录 `MODEL_CALL → TOOL_CALL → TOOL_RESULT → MODEL_CALL → FINAL_ANSWER`、两个旧脚本回归通过、重启后遗留执行/步骤/任务均正确转为 `FAILED`。附带修复了 `allowTools` 与测试契约不一致的问题。

## 环境备注

- 验证结束已停止应用（8080 释放），MySQL / Redis 容器保持运行。
- 测试库保留了任务 #6 ~ #9 的验证数据，均为本地开发数据。

---

# 第 4 周验证报告（2026-10-21，代码 ca3d51b）

> 依据 [docs/week-4-csv.md](docs/week-4-csv.md)：CSV 上传分析与模型工具联动。

## 环境

| 项 | 值 |
| --- | --- |
| 代码版本 | `ca3d51b 新增 CSV 上传分析与模型工具联动` |
| 数据库 | MySQL 8.0，容器 `staranaly-mysql`，`healthy` |
| 迁移 | V4 首次执行成功 |
| 应用 | 启动 4.0s，Tomcat 8080 |
| 样例 | `fixtures/monthly-sales.csv`（88 字节，4 行） |

V4 迁移日志：

```
Migrating schema `staranaly` to version "4 - csv files"
Successfully applied 1 migration to schema `staranaly`, now at version v4 (execution time 00:00.108s)
```

新建 `task_csv_files`、`task_csv_analysis_results` 两张表。

## 1. `mvn test`

```
Tests run: 30, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

分布：

| 测试类 | 数量 |
| --- | --- |
| TaskStartupRecoveryTest | 1 |
| ReadOnlyToolRegistryTest | 3 |
| TaskControllerTest | 5 |
| TaskRunControllerTest | 3 |
| TaskConversationControllerTest | 4 |
| TaskConversationServiceTest | 5 |
| TaskConversationStateTest | 4 |
| CsvFileControllerTest | 1 |
| CsvParserTest | 2 |
| CsvAnalyzerTest | 2 |

本周新增 CSV 相关 5 项测试，全部通过。

## 2. `scripts/verify_csv.py`

```bash
cd scripts && python3 verify_csv.py
```

```
[通过] 上传 CSV 文件 #1，4 行数据
[通过] CSV 预览与 category=A 筛选
[通过] 按月汇总：2026-01=300，2026-02=200，总计 500
[通过] 模型调用 CSV 工具，步骤与结构化结果均已保存
exit=0
```

任务 #10，文件 #1 `monthly-sales.csv`，列 `date/category/amount`，4 行。

筛选 `category=A` 返回 2 行；按月汇总 `2026-01=300`、`2026-02=200`、总计 `500`。

执行 #8 步骤：

| stepNumber | kind | toolName | status | inputSummary | outputSummary |
| --- | --- | --- | --- | --- | --- |
| 1 | MODEL_CALL | - | SUCCEEDED | 请求模型生成回复 | 模型请求 1 个工具 |
| 2 | TOOL_CALL | summarize_csv | SUCCEEDED | 模型请求工具 summarize_csv | 参数校验通过 |
| 3 | TOOL_RESULT | summarize_csv | SUCCEEDED | CSV 文件 1：按 date 汇总 amount | 得到 2 组，总计 500 |
| 4 | MODEL_CALL | - | SUCCEEDED | 提交工具结果并请求模型回复 | 模型返回 189 字符 |
| 5 | FINAL_ANSWER | - | SUCCEEDED | 保存最终回复 | 回复已保存 |

结构化分析结果（`GET /api/tasks/10/runs/8/analysis`）：

```json
{
  "fileId": 1, "groupBy": "date", "sumColumn": "amount", "byMonth": true,
  "matchedRows": 4, "total": 500,
  "groups": [
    {"key": "2026-01", "sum": 300, "rowCount": 2},
    {"key": "2026-02", "sum": 200, "rowCount": 2}
  ]
}
```

最终模型回复以表格给出 2026-01=300、2026-02=200、总计 500，与确定性计算结果一致。

## 3. 边界说明

本轮按文档验收范围执行了 `mvn test` 与 `verify_csv.py`，未额外构造上传大小、列数、行数、跨任务文件归属等边界场景；这些约束由 5 项 CSV 单元测试覆盖。

## 第 4 周结论

`docs/week-4-csv.md` 的验收项全部通过：V4 迁移成功、30 项测试全绿、CSV 上传/预览/筛选/按月汇总结果与固定样例一致（300 / 200 / 500）、模型成功调用 `summarize_csv`、步骤与结构化分析结果均已落库。

## 环境备注

- 验证结束已停止应用（8080 释放），MySQL / Redis 容器保持运行。
- 测试库保留任务 #10 及其文件 #1、执行 #8、分析结果 #1，均为本地开发数据。
- 本轮未修改代码。

---

# 第 5 周验证报告（2026-10-21，代码 c908b07）

> 依据 [docs/week-5-knowledge.md](docs/week-5-knowledge.md)：资料检索与来源引用。

## 环境

| 项 | 值 |
| --- | --- |
| 代码版本 | `c908b07 新增资料检索与来源引用能力` |
| 数据库 | MySQL 8.0，容器 `staranaly-mysql`，`healthy` |
| 迁移 | V5 首次执行成功 |
| 应用 | 启动 4.2s，Tomcat 8080 |
| 前端 | Vite 8.3.4，`http://localhost:5173`，代理 `/api` → 8080 |
| 样例 | `fixtures/knowledge-demo.md` |

V5 迁移日志：

```
Successfully applied 1 migration to schema `staranaly`, now at version v5 (execution time 00:00.155s)
```

## 1. `mvn test`

```
Tests run: 35, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

测试类明细：

| 测试类 | 数量 |
| --- | --- |
| KnowledgeTextProcessorTest | 2 |
| TaskStartupRecoveryTest | 1 |
| ReadOnlyToolRegistryTest | 5 |
| TaskControllerTest | 5 |
| TaskRunControllerTest | 3 |
| TaskConversationControllerTest | 4 |
| TaskConversationServiceTest | 5 |
| TaskConversationStateTest | 5 |
| CsvFileControllerTest | 1 |
| CsvParserTest | 2 |
| CsvAnalyzerTest | 2 |

本周新增知识检索相关测试（`KnowledgeTextProcessorTest` 2 项，`ReadOnlyToolRegistryTest` 由 3 项增至 5 项，`TaskConversationStateTest` 由 4 项增至 5 项），全部通过。

## 2. `scripts/verify_knowledge.py`

```bash
cd scripts && python3 verify_knowledge.py
```

```
[通过] 上传并列出资料 #1
[通过] 检索命中原文第 3 行，且无关问题无命中
[通过] 模型调用检索工具，回答与结构化来源引用均已保存
[通过] 删除资料后检索与引用均已清理
exit=0
```

任务 #11，资料 #1 `knowledge-demo.md`，执行 #9 步骤：

| stepNumber | kind | toolName | status | inputSummary | outputSummary |
| --- | --- | --- | --- | --- | --- |
| 1 | MODEL_CALL | - | SUCCEEDED | 请求模型生成回复 | 模型请求 1 个工具 |
| 2 | TOOL_CALL | search_knowledge | SUCCEEDED | 模型请求工具 search_knowledge | 参数校验通过 |
| 3 | TOOL_RESULT | search_knowledge | SUCCEEDED | 资料检索：普通订单退货期限 | 命中 2 个片段 |
| 4 | MODEL_CALL | - | SUCCEEDED | 提交工具结果并请求模型回复 | 模型返回 140 字符 |
| 5 | FINAL_ANSWER | - | SUCCEEDED | 保存最终回复 | 回复已保存 |

最终回复：

> 根据检索到的一处明确命中：
>
> **普通订单的退货期限：自签收日起 14 天内可以申请退货。**
>
> - 依据资料：`knowledge-demo.md`，第 3 行
> - 第 3 行还注明：超过 14 天的申请需要人工审核。

删除资料后复查：`GET /documents` 返回 `[]`，`GET /runs/9/citations` 返回 `[]`，检索无命中，级联清理生效。

## 3. 边界说明

脚本覆盖了文案要求的核心链路（上传、列表、命中行号、无关问题空命中、模型工具调用、结构化引用、删除后清理）。上传大小、编码、NUL 字符、分段上限、`limit` 越界等错误分支由 `KnowledgeTextProcessorTest` 与控制器测试覆盖，本轮未额外手工构造。

## 第 5 周结论

`docs/week-5-knowledge.md` 的验收项全部通过：V5 迁移成功、35 项测试全绿、检索命中原文第 3 行且无关问题无命中、模型成功调用 `search_knowledge` 并按资料名称与行号标注出处、结构化引用落库、删除资料后检索与引用均清空。

## 环境备注

- 验证结束时后端（8080）与前端（5173）均在运行，MySQL / Redis 容器保持运行。
- 测试库保留任务 #11、执行 #9 的记录；资料 #1 已按脚本流程删除，因此引用随之为空。
- 本轮未修改代码。
