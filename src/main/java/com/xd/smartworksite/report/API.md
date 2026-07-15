# 报告模块接口文档

本文档描述 `report` 模块当前已实现的报告创建、查询、重新生成和下载接口。报告生成是异步流程：Java 创建报告和任务，Worker 使用 Java DOCX 模板引擎渲染报告，并调用 Python 模型服务补全模板变量内容。

所有接口都需要：

```http
Authorization: Bearer <accessToken>
```

## 前置配置

异步生成需要开启任务 outbox 和 Worker：

```env
TASK_OUTBOX_DISPATCHER_ENABLED=true
TASK_WORKER_ENABLED=true
```

Python AI 服务用于根据材料生成缺失模板变量：

```env
AI_PYTHON_BASE_URL=http://127.0.0.1:8015
AI_PYTHON_API_KEY=dev-ai-service-key
```

## 接口总览

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| `POST` | `/api/reports` | 创建报告生成任务 |
| `GET` | `/api/reports` | 分页查询报告 |
| `GET` | `/api/reports/{reportId}` | 查询报告详情 |
| `POST` | `/api/reports/{reportId}/regenerate` | 重新生成报告 |
| `GET` | `/api/reports/{reportId}/download` | 获取下载地址 |

## 1. 创建报告

```text
POST /api/reports
Content-Type: application/json
```

请求体：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| projectId | Long | 是 | 项目 ID |
| reportName | String | 是 | 报告名称 |
| reportType | String | 是 | 报告类型 |
| templateId | Long | 是 | DOCX 报告模板 ID |
| referenceFileIds | Array<Long> | 是 | 引用材料文件 ID，至少一个 |
| knowledgeBaseIds | Array<Long> | 否 | 知识库 ID，当前记录配置 |
| dataSourceIds | Array<Long> | 否 | 数据源 ID，当前记录配置 |
| variables | Object | 否 | 用户显式传入的模板变量，优先于 AI 生成值 |

示例：

```bash
curl --noproxy '*' -X POST "http://127.0.0.1:8080/api/reports" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"projectId":1,"reportName":"周安全报告","reportType":"WEEKLY","templateId":1,"referenceFileIds":[1],"variables":{"项目名称":"青岛智慧工地"}}'
```

响应重点字段：`reportId`、`taskId`、`status`。创建接口返回 `PENDING`，实际生成由 Worker 异步执行。

校验规则：

- `reportName` 必须显式传入。
- `templateId` 必须是当前项目启用的 DOCX 报告模板。
- 模板占位符支持 `${变量名}` 和 `{{变量名}}`，可混用，变量名会去除首尾空格。
- `referenceFileIds` 必须属于同一项目；文本文件直接读取，非文本文件必须有最新 `SUCCESS` 解析记录。
- 所有模板变量必须最终有非空值；用户变量优先，缺失变量由 Python 模型根据材料生成。
- 项目必须处于可写状态。

## 2. 查询报告

```text
GET /api/reports
GET /api/reports/{reportId}
```

报告状态：`DRAFT`、`PENDING`、`PROCESSING`、`COMPLETED`、`FAILED`、`ARCHIVED`、`DELETED`。

## 3. 重新生成

```text
POST /api/reports/{reportId}/regenerate
```

说明：重新创建生成任务并返回新的 `taskId`。

## 4. 下载报告

```text
GET /api/reports/{reportId}/download?format=WORD
```

当前支持 Word 下载。报告必须已完成，接口返回 MinIO 预签名下载地址字符串。`format=PDF` 明确不支持。

## 写入规则

- 报告创建必须写入报告记录、任务、outbox。
- 报告、任务、文件和版本状态流转必须检查影响行数或生成 ID。
- Worker 执行前必须重新校验项目可写。
- Python AI 服务不可用、材料不可读、变量缺失、模板不合法时，必须记录任务和报告失败原因，不能返回假成功。
