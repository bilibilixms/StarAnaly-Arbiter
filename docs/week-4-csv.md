# 第 4 周：CSV 数据分析

> 状态：代码待用户测试机验证 · 对应 [开发规划](development-plan.md) 第 4 周

## 功能与边界

上传的 CSV 保存在 MySQL 的 `task_csv_files` 中；V4 迁移还创建 `task_csv_analysis_results`，保存工具执行后的结构化汇总。服务端只接受 UTF-8 `.csv`，文件非空且不超过 5 MiB、数据不超过 50,000 行、列数不超过 50。表头必须唯一；所有数据行列数必须一致。解析支持逗号、双引号、转义引号和换行。

列表与预览展示文件元数据和前 20 行。筛选采用列值精确匹配；分组求和支持普通分组和 `YYYY-MM-DD` 日期按月分组。金额列只接受限定长度的十进制数；分组最多 100 组。API 计算使用确定性代码，模型只负责选择 `summarize_csv` 工具并解释结果。工具按当前任务 ID 检查文件归属，不能读取其他任务的文件。

| 操作 | 方法与路径 |
| --- | --- |
| 上传 | `POST /api/tasks/{id}/files`，multipart 字段 `file` |
| 文件列表 | `GET /api/tasks/{id}/files` |
| 文件预览 | `GET /api/tasks/{id}/files/{fileId}` |
| 筛选行 | `GET /api/tasks/{id}/files/{fileId}/rows?filterColumn=category&filterValue=A&limit=100` |
| 分组求和 | `GET /api/tasks/{id}/files/{fileId}/aggregate?groupBy=date&sumColumn=amount&byMonth=true` |
| 执行分析结果 | `GET /api/tasks/{id}/runs/{runId}/analysis` |

分组求和也可带 `filterColumn` 和 `filterValue`。错误文件与非法列返回 `400`；超出上传请求大小返回 `413`；任务或文件不存在返回 `404`。工具请求错误会把对应执行标为 `FAILED`，并在步骤里记录公开错误原因。

## 测试机器上的验收

更新代码并启动 MySQL 与后端后，在仓库根目录运行：

```bash
mvn test
python3 scripts/verify_csv.py
```

脚本会创建任务、上传 [固定样例](../fixtures/monthly-sales.csv)、预览、筛选、验证 2026-01=300、2026-02=200、总计 500，然后请求 DeepSeek 调用 CSV 工具并检查步骤与结构化结果。请把测试结果追加到 `fox.md`，失败时提供脱敏后的状态、响应和日志，不要提交密钥。浏览器中的文件与分析结果页面计划在第 6 周接入。
