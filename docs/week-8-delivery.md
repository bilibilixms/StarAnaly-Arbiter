# 第 8 周：最终验收与交付

本项目当前面向单用户或受控内网试用。第 7 周测试机报告记录了 43 项后端测试、CSV/资料/质量脚本和前端构建通过；CSV 的 87/88 B 差异已定位为当次浏览器测试构造的文件少了末尾换行。报告发现后端断连时代理返回 502 被误写成“模型服务调用失败”，本轮已修正前端的默认提示。任务与执行列表也改为固定字段的分页响应，避免直接序列化 `PageImpl` 的稳定性警告；保留前端依赖的 `content`、`number`、`totalElements` 和 `totalPages`。模型服务失败后重新提交的浏览器场景仍需最终验收。

## 配置与启动顺序

1. 测试机需 Java 21、Maven、Docker Compose、Node.js；具体最低版本见 [README](../README.md)。MySQL 8.0 是持久化依赖，Redis 目前不被任务 API 使用。
2. 在运行 Docker Compose 和后端的环境中配置 `MYSQL_ROOT_PASSWORD`、`MYSQL_PASSWORD`，按需设置 `MYSQL_USER`、`DB_HOST`、`DB_PORT`、`DB_NAME`。模型使用 `MODEL_API_KEY` 或 `DEEPSEEK_API_KEY`，按需设置 `MODEL_NAME`、`MODEL_BASE_URL`。密钥仅保留在测试机环境变量或被 Git 忽略的 `.env` 中。
3. 运行 `docker compose up -d mysql`，确认 MySQL 健康后运行 `mvn spring-boot:run`。Flyway 在启动时检查迁移；当前数据库版本为 V5。后端 API 默认在 8080 端口。
4. 前端锁文件已提交，进入 `frontend/` 运行 `npm ci && npm run build`。开发联调用 `npm run dev`，默认代理 `/api` 到 `http://localhost:8080`；后端地址不同时设置 `BACKEND_URL`。

生产式静态托管需把 `frontend/dist/` 作为站点目录，把 `/api` 代理到后端，并让前端路由回退到 `index.html`。例如 Nginx 的关键配置为：

```nginx
location /api/ {
    proxy_pass http://127.0.0.1:8080;
}
location / {
    try_files $uri $uri/ /index.html;
}
```

这是内网试用配置示例。当前没有登录、租户隔离和对公网开放的访问控制。

## MySQL 备份与恢复

任务、消息、执行记录、表格原文件、资料提取文本和引用均在 MySQL；只备份前端构建产物不足以恢复数据。以下命令在仓库根目录、MySQL 容器名为 `staranaly-mysql` 时使用，备份目录已被 Git 忽略：

```bash
mkdir -p backups
chmod 700 backups
umask 077
backup_path="backups/staranaly-$(date -u +%Y%m%dT%H%M%SZ).sql"
temp_path="${backup_path}.part"
if docker exec staranaly-mysql sh -c 'export MYSQL_PWD="$MYSQL_PASSWORD"; exec mysqldump --single-transaction --quick --hex-blob --no-tablespaces --set-gtid-purged=OFF -u "$MYSQL_USER" staranaly' > "$temp_path" && test -s "$temp_path"; then
    mv "$temp_path" "$backup_path"
    sha256sum "$backup_path"
else
    rm -f "$temp_path"
    echo '备份失败，未生成正式备份文件。' >&2
    exit 1
fi
```

备份文件含用户输入与上传原文，应妥善限制读取权限，并复制到受控存储。恢复时先在**新的空数据库**上启动 MySQL，保持后端停止；确定备份文件无误后导入：

```bash
backup_path="backups/要恢复的备份文件.sql"
docker exec -i staranaly-mysql sh -c 'export MYSQL_PWD="$MYSQL_PASSWORD"; exec mysql -u "$MYSQL_USER" staranaly' < "$backup_path"
```

导入后启动后端并确认 Flyway 版本、任务列表、文件和执行记录。不要把备份导入已有业务数据的库；恢复操作会创建和写入数据库表，应只在明确选定的空库中执行。

## 最终验收

在测试机启动后端并配置可用模型后，从仓库根目录执行：

```bash
mvn test
cd frontend && npm ci && npm run build
cd ..
python3 scripts/verify_release.py
```

`verify_release.py` 依次调用既有 CSV、资料问答、质量回归脚本；会创建测试任务、上传样例并调用模型，不会删除已有任务。通过标准：CSV 两个月汇总为 300 / 200、总计 500；资料回答为签收日起 14 天并附第 3 行出处，删除后引用清空；错误文件和跨任务访问被拒绝。最终还要在浏览器确认两个流程、后端断连与恢复，并核对日志不含模型密钥。

模型失败恢复的补测可在独立测试任务上进行：先停止正常后端，在一个测试终端用 `MODEL_BASE_URL=http://127.0.0.1:9 MODEL_API_KEY=placeholder mvn spring-boot:run` 启动后端，提交问题并确认执行进入 `FAILED`、页面显示原因且输入框保留问题；停止该进程，再用原环境变量启动后端，重新提交并确认新增成功执行。不要把真实密钥写入命令、截图或报告。

## 已知限制与交付判定

- 当前支持 CSV/XLS/XLSX 表格及 TXT/Markdown/PDF/DOC/DOCX 资料；格式和大小限制见[文件上传说明](file-upload.md)。表格不提供删除接口；资料删除会清除结构化引用，历史文字回答保留。
- 资料使用词面双字检索，没有语义召回；同义改写和大量资料的质量、速度仍需真实数据评估。
- 单次执行最多 120 秒、2 次工具调用；模型和网络不可用时执行会失败，需由用户重新提交。
- 当前无多用户身份验证和权限体系，也未提供公网部署方案。

测试机完成上述回归、失败恢复和备份恢复演练后，将版本、命令结果和脱敏证据写入 `fox.md`。若有阻断问题，先修复并重新验收，再考虑标记交付版本。
