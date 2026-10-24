# 星析 Agent · StarAnaly

一个将客户需求整理为可发布宣传内容的工作台。可收集文字、图片、PDF 和 Word，生成与修改 1～4 张海报，挑选图片制作小红书或抖音图文草稿，最后跟踪发布结果。原有任务对话、数据分析和资料检索接口仍保留在后端。

## 当前实现

- Java 21、Spring Boot 3.5、Maven 后端。
- MySQL 持久化；应用启动时由 Flyway 执行数据库迁移。
- Docker Compose 提供 MySQL 和 Redis。当前任务 API 只使用 MySQL，Redis 尚未接入应用。
- 任务创建、单条查询、按状态分页浏览和任务名容错搜索，以及按任务保存的对话消息。
- 需求详情集中保存客户原始要求、参考图片与文档、个人补充说明，并展示关联作品；创作阶段由后续流程推进。
- 每次对话对应一条执行记录；支持异步提交、状态查询和步骤记录。
- 模型可调用受控的只读整数加法工具，工具请求、结果与最终回答均有步骤记录。
- 支持上传 CSV、XLS、XLSX 表格，预览和筛选数据、分组求和，并通过模型工具保存结构化分析结果。
- 支持上传 TXT、Markdown、PDF、DOC、DOCX 资料，检索提取的文字片段，记录执行中的来源引用，并删除资料。
- 兼容 OpenAI Chat Completions 的模型调用；任务状态包含 `PENDING`、`RUNNING`、`SUCCEEDED`、`FAILED`。
- `frontend/` 中的 Vue 3 + TypeScript 页面用于收集需求、生成海报和制作小红书、抖音图文；原有任务对话与分析接口仍保留在后端。

## 本地运行

需要 Java 21、Maven 3.6.3 或更新版本、Docker Compose，以及用于前端的 Node.js 22.18.x 或 24.12 及以上版本。

如果本机未安装 Linux 版 Java、Maven 和 Node，可使用项目中的 Docker 本地环境。复制 `.env.example` 为 `.env` 并设置 MySQL 密码，然后从仓库根目录执行：

```bash
bash scripts/local.sh up       # 启动 MySQL、后端和前端
bash scripts/local.sh restart  # 修改后端代码或 .env 后重启应用
bash scripts/local.sh status   # 查看状态
bash scripts/local.sh test     # Java 测试、前端构建、浏览器扩展测试
bash scripts/local.sh down     # 停止服务，保留数据库和依赖缓存
```

本地环境使用 `docker-compose.local.yml`，固定 Java 21、Maven 3.9.9 和 Node 24。前端的 Linux 依赖保存在独立 Docker 卷中，不会覆盖宿主机已有的 `frontend/node_modules`。页面地址是 `http://localhost:5173`。AI 生成功能还需要在 `.env` 中设置 `MODEL_API_KEY` 或 `DEEPSEEK_API_KEY`；其他页面与自动化测试不需要模型密钥。

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
| `POSTER_API_KEY` | 千问AI平台图像模型密钥，仅存本机 `.env` |
| `POSTER_API_BASE_URL` | 千问AI平台多模态接口基础地址，北京地域为 `https://cn-beijing.maas.qianwenaiapi.com/api/v1` |
| `POSTER_MODEL` | 图像模型，默认 `qwen-image-3.0-pro` |

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
   npm ci
   npm run dev
   ```

浏览器打开 `http://localhost:5173`。前端开发服务器会将 `/api` 请求转发至 `http://localhost:8080`；如果后端位于其他地址，启动前端时可设置 `BACKEND_URL` 环境变量。数据库和模型密钥都只由后端读取。

应用默认连接 `localhost:3307` 上的 `staranaly` 数据库，并使用 `MYSQL_USER`、`MYSQL_PASSWORD`。如果数据库不在本机，可以设置 `DB_HOST`、`DB_PORT` 和 `DB_NAME`。首次启动时 Flyway 会创建任务、对话、执行记录、CSV 和资料相关表。

MySQL 官方镜像只在首次初始化数据目录时创建 `MYSQL_USER`。如果已有旧的 `mysql-data` 卷，需在现有数据库中手动创建该用户并授予 `staranaly` 数据库权限，或使用全新数据卷；不要删除仍需保留数据的卷。

## 工作空间

在“工作空间 → 新建需求”录入客户要求，详情页集中补充原始文字、参考图片、PDF、Word 和个人补充说明。需求图片最多 6 张，单张不超过 20 MB，总计不超过 30 MB。扫描版 PDF 暂不支持文字提取。需求列表只显示名称、创作阶段和创建时间；旧任务的对话、表格分析及执行记录保存在后端，但不占用需求单的主界面。

进入“工作台 → 宣传创作”，选择需求、参考图片、海报主题及 1～4 张生成数量。配置 `POSTER_API_KEY` 和 `POSTER_API_BASE_URL` 后，后端会把需求文字、个人补充说明、文档摘录及所选参考图发给千问图像模型，并把模型返回的临时图片链接立即下载为图片内容交给浏览器。未配置时界面明确标注“排版示意”，用本地模板验证流程。首次生成后可以预览、挑选或填写下一版修改方向，生成新版本并切回前一版；AI 模式下填写修改方向时会把当前选中图片作为下一版参考图。

选中的海报通过浏览器本地 IndexedDB 交给小红书或抖音创作页，最多 4 张。保存文案时图片会进入 MySQL，之后在创作记录中仍可打开、修改和发布。草稿会关联需求；标记发布后，关联需求的创作阶段会更新为“已发布”。旧的 `/game` 地址会跳转到通用工作台。

## 抖音图文创作

在“分享空间 → 抖音 → 图文创作”上传 1～4 张图片，或从工作台带入选中的海报，即可生成抖音图文的标题、正文和话题。可以选用抖音专属素材，编辑预览并保存草稿；“创作记录”可重新打开、修改或删除草稿，并查看已发布作品。抖音的素材和草稿分别保存在独立表中，不会混入小红书列表。

电脑端安装或重新加载仓库中的 `browser-extension/xiaohongshu` 扩展（0.7.0 或更新版本）后，点击“去发布（自动填入）”会先保存草稿，再打开抖音图文发布页，将图片、标题、正文和话题交给页面。请在抖音页核对图片上传状态与文案，并亲自点击发布；扩展不会代点发布按钮。发布后返回草稿，点击“已在抖音发布”更新记录和关联需求的阶段。没有扩展时仍可使用设备系统分享，或复制文案并打开抖音创作者中心手动发布；抖音发布成功目前需手动标记。

## 小红书图文创作

在“分享空间 → 小红书 → 图文创作”选择 1～4 张 JPEG、PNG、GIF 或 WebP 图片（每张最大 20 MB），即可让支持视觉输入的模型识别首图并生成标题、正文和话题。文字要求可留空；填写时最多 2000 字。默认的 `deepseek-flash` 支持[图文输入](https://api-docs.deepseek.com/guides/vision/)；若使用其他兼容模型，需要确认该模型也支持图片。生成的文案可在预览中修改和复制。点击“保存当前文案”会把图片、要求和文案存入数据库；在独立的“创作记录”页面可查看已保存与已发布文案，重新打开草稿并修改、发布或删除。

“素材管理”位于小红书侧边栏，可将直接输入的文字或 PDF、Word 文档提取出的文字命名保存，之后查看、修改、替换或删除。图文创作页可选用最多 3 份素材；生成时优先参考所选素材中的事实和表达语气。上传文档不超过 5 MiB，保存的素材内容不超过 12000 字；扫描版 PDF 暂不支持文字提取。

使用浏览器扩展点击“去发布（自动填入）”时，星析会先保存当前文案。你在小红书亲自点击发布后，扩展仅在识别到明确的“发布成功”提示时通知星析，将对应草稿自动移入“已发布”，并切回点击“去发布”前的星析标签页，保留该页状态；原标签页已关闭或离开小红书空间时，会新打开星析的小红书创作页并显示该文案。打开发布页、填入完成或仅点击发布按钮都不会改变状态。成功事件会在扩展中暂存，星析页面重新打开后仍可同步；如果小红书页面变化导致提示未被识别，可在草稿预览的“发布后没有自动同步？”中手动补记。系统分享和普通网页跳转无法读取小红书的发布结果，仍需手动标记。

电脑浏览器可安装项目自带的扩展，把原图、标题、正文和话题自动填入小红书创作页：

1. 在 Chrome 打开 `chrome://extensions`，或在 Edge 打开 `edge://extensions`，启用“开发者模式”。
2. 选择“加载已解压的扩展程序”，选中仓库里的 `browser-extension/xiaohongshu` 文件夹。已安装过扩展时，代码更新后在扩展管理页点击“重新加载”，再刷新星析页面；多图自动填入需要 0.6.0 或更新版本。
3. 用 `localhost` 或 `127.0.0.1` 打开星析前端并刷新页面。生成笔记后，点击“去发布（自动填入）”。扩展会打开小红书图文发布页，上传原图并填入文案；请登录、检查内容，再亲自点击发布。

更新扩展代码后，先在 Chrome 或 Edge 的扩展管理页点击该扩展的“重新加载”，再刷新星析页面。

扩展只在本机星析页面和小红书创作页运行；待填入的图片与文案临时保存在浏览器扩展中，填入成功、关闭目标标签页或 30 分钟后清除。关联草稿的发布跟踪记录不含图片，最多保留 24 小时或直到目标标签页关闭。小红书页面结构变化时，扩展会在发布页显示错误和重试入口。扩展不会点击发布按钮。

未安装扩展时，支持文件分享的设备会显示“去发布（系统分享）”，尝试把原图与文案交给所选 App；其他浏览器会显示“复制正文并打开发布页”，需要手动上传原图和粘贴文案。小红书的[网页分享 SDK](https://agora.xiaohongshu.com/doc/js) 已明确不再自动填充标题、文案和话题，因此电脑端自动填入使用的是本地浏览器扩展。

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
curl 'http://localhost:8080/api/tasks?page=0&size=20&status=SUCCEEDED&query=%E9%94%80%E5%94%AE%E9%A2%9D'
curl 'http://localhost:8080/api/tasks?page=0&size=20&sort=createdAt,asc&sort=id,asc'
```

列表可按 `PENDING`、`RUNNING`、`SUCCEEDED`、`FAILED` 筛选。前端可按创建时间选择“最新优先”或“最早优先”，并在分页前对全部匹配任务排序；时间相同时按任务 ID 稳定排序。API 可通过 `sort=createdAt,asc|desc` 指定时间顺序，不指定时维持原有默认顺序。`query` 搜索任务标题，支持部分名称、同拼音汉字和少量普通错字：例如输入“粉”可找到标题中的“分”（忽略声调）。1～2 字的普通错字不放宽，3～5 字允许 1 处，6 字及以上允许 2 处；未指定时间排序时优先显示原字匹配，再显示同音字匹配，然后显示普通错字匹配。同名任务按不同 ID 分别显示。搜索在全部任务中匹配后分页，当前面向单用户、小规模任务库。任务不存在时返回 `404`；标题为空或超过 200 字符、描述超过 5000 字符、搜索词超过 200 字符时返回 `400`。

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

前端浏览器验收步骤见 [第 6 周工作台说明](docs/week-6-frontend.md)。

文件边界、跨任务隔离和失败恢复检查见 [第 7 周质量回归说明](docs/week-7-quality.md)，后端运行时可执行 `python3 scripts/verify_quality.py`。新增格式和统一上传入口的边界见 [文件上传说明](docs/file-upload.md)。

最终验收、部署配置和 MySQL 备份恢复见 [第 8 周交付说明](docs/week-8-delivery.md)。

## 测试

```bash
mvn test
```

当前接口测试使用 MockMvc 和模拟的服务，不需要启动 MySQL 或连接模型。应用实际启动仍需要 MySQL，以便执行 Flyway 迁移和校验表结构；使用对话功能还需配置 `MODEL_API_KEY` 或 `DEEPSEEK_API_KEY`。

前端可在 `frontend/` 中运行 `npm run build`，该命令会检查 TypeScript 并生成静态文件。部署静态文件时，需要将 `/api` 转发到后端，并将前端路由回退到 `index.html`。

## 后续规划

7～9 周的阶段目标、每周交付和验收口径见 [开发规划说明书](docs/development-plan.md)。
第 1 周的验证命令、固定演示数据和执行方案见 [基线核对与执行方案](docs/week-1-baseline.md)。

1. 在用户测试机执行第 8 周最终回归和备份恢复演练，补测模型失败后的重新提交。
2. 处理验收阻断问题并记录结果后，标记交付版本。

本项目采用 [MIT License](LICENSE)。
