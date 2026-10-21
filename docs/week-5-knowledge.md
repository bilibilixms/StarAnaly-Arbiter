# 第 5 周：资料检索与来源引用

本周在已有任务执行链中增加 TXT/Markdown 资料上传、分段检索和引用记录。固定样例是 `fixtures/knowledge-demo.md`。第 4 周的 `fox.md` 记录已确认数据库迁移和 CSV/DeepSeek 工具闭环可在测试机运行，因此本周继续沿用 MySQL 保存资料和运行记录。

## 数据与检索方式

- 资料仅接受非空的 `.txt`、`.md`、`.markdown`，采用严格 UTF-8 解码，单文件上限 1 MiB；无效编码、NUL 字符、空白内容和超过 2,000 个分段会被拒绝。
- 按空行和最多 800 字符分段，并保存原文起止行号。迁移 `V5__knowledge_documents.sql` 新增资料、片段和执行引用表。
- 检索先在当前任务内读取片段，再用字母、数字及汉字的双字组合计算词面重合数，按得分排序，返回最多 5 个片段。无重合时返回空列表。这是小规模资料的起步方案，没有语义召回能力；同义改写或资料过多时应根据实测改进索引策略。
- 模型通过只读工具 `search_knowledge` 获取片段。工具结果带资料名称、行号和原文，命中片段的引用与执行记录绑定。系统提示要求按资料回答并标注出处；如果没有证据，说明未找到依据。
- 删除资料会级联删除片段和相应引用。历史模型回复文本保留，但引用接口将不再返回已删除资料的原文。

## API

| 方法与路径 | 用途 |
| --- | --- |
| `POST /api/tasks/{taskId}/documents` | multipart 字段 `file`，成功返回 `201`、`Location` 和资料元数据 |
| `GET /api/tasks/{taskId}/documents` | 列出任务资料元数据 |
| `GET /api/tasks/{taskId}/documents/search?query=...&limit=5` | 返回匹配片段、得分和原文行号；`limit` 为 1～5 |
| `DELETE /api/tasks/{taskId}/documents/{documentId}` | 删除资料，成功返回 `204` |
| `GET /api/tasks/{taskId}/runs/{runId}/citations` | 查询一次执行使用过的资料片段 |

上传示例：

```bash
curl -i -F 'file=@fixtures/knowledge-demo.md' http://localhost:8080/api/tasks/1/documents
```

检索词须含至少两个有效字符且不超过 200 字符。错误文件、检索词或数量返回 `400`；任务、执行或资料不存在时返回 `404`。当前版本仅面向单用户或受控内网试用，没有账号授权隔离，勿直接公开部署。

## 测试机验收

在已配置 MySQL 和 DeepSeek 环境变量的测试机，拉取代码、启动后端并执行：

```bash
mvn test
python3 scripts/verify_knowledge.py
```

脚本创建新任务，上传固定资料，验证“14 天”及原文第 3 行、无关问题空命中、模型工具调用、最终回答与结构化引用，最后删除资料并确认检索和引用都为空。执行结果和失败日志可追加到 `fox.md`，不要写入密钥。
