# 文件上传说明

任务详情页现在使用一个上传入口。选择文件后，前端按扩展名调用已有的表格或资料 API；上传结果分别列在“表格数据”和“知识资料”中。API 路径没有变化，已有数据无需迁移。

| 用途 | 格式 | 文件上限 | 处理方式 |
| --- | --- | --- | --- |
| 表格分析 | UTF-8 `.csv`、`.xls`、`.xlsx` | 5 MiB | Excel 读取第一张工作表，首行作为表头；上传后可预览、筛选与汇总 |
| 资料问答 | UTF-8 `.txt`、`.md`、`.markdown` | 1 MiB | 保存文本并按行分段检索 |
| 资料问答 | `.pdf`、`.doc`、`.docx` | 5 MiB，提取文字最多 1 MiB | 提取文字后按行分段检索；PDF 提取文本含页码标记 |

表格仍限制最多 50 列和 50,000 行，表头不可为空或重复。Excel 数值单元格读取原始数值，日期单元格转换为 `YYYY-MM-DD`，其他单元格读取显示值；公式使用文件保存的缓存结果，不会重新计算。多工作表只读取第一张。表格分析工具名称仍为 `summarize_csv`，以兼容已有模型工具调用。

PDF 最多 100 页。扫描版或纯图片 PDF 没有可提取的文字，会返回明确错误；可先在测试机完成 OCR 后再上传。Word、PDF 中的图片和复杂排版不会转成文字。资料库保存提取后的文本，不保存原始 PDF/Word 文件；资料引用的行号指向提取后的文本，PDF 的页码标记会保存在该文本中。

后端使用 [Apache POI](https://poi.apache.org/download.html) 读取 Excel、Word，使用 [Apache PDFBox](https://pdfbox.apache.org/download.html) 提取 PDF 文字。上传文件名和大小会在前后端检查；文件内容仍由后端解析验证。

## 测试机核对

拉取代码并启动现有 MySQL 和后端后，从仓库根目录运行：

```bash
mvn test
(cd frontend && npm ci && npm run build)
python3 scripts/verify_release.py
```

在任务详情页用统一入口分别上传一个 CSV、XLSX、TXT、DOCX 和带文字的 PDF：确认表格显示表头和预览，资料出现在知识列表并可搜索。再试损坏的 Excel/PDF、无文字 PDF 和超限文件，确认页面显示可读错误。
