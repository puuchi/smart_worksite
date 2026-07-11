# 报告模块接口文档

本文档描述 `report` 模块当前已实现的报告创建、查询、重新生成和下载接口。报告生成是异步流程，Java 创建报告和任务，Worker 调用 CryptoAgentV3 生成文件。

所有接口都需要：

```http
Authorization: Bearer <accessToken>
```

统一响应结构：

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "requestId": "xxx",
  "timestamp": "2026-07-11T16:00:00+08:00"
}
```

## 前置配置

异步生成需要开启任务 outbox 和 Worker：

```env
TASK_OUTBOX_DISPATCHER_ENABLED=true
TASK_WORKER_ENABLED=true
```

CryptoAgentV3 配置：

```env
CRYPTO_AGENT_V3_BASE_URL=http://127.0.0.1:8012
CRYPTO_AGENT_V3_INVOKE_PATH=/v1/report-generation/invoke
CRYPTO_AGENT_V3_CONNECT_TIMEOUT_SECONDS=5
CRYPTO_AGENT_V3_READ_TIMEOUT_SECONDS=3000000
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
| templateId | Long | 是 | 报告模板 ID |
| referenceFileIds | Array<Long> | 否 | 引用文件 ID |
| knowledgeBaseIds | Array<Long> | 否 | 知识库 ID |
| dataSourceIds | Array<Long> | 否 | 数据源 ID |
| parameters | Object | 否 | 生成参数 |

示例：

```bash
curl --noproxy '*' -X POST "http://127.0.0.1:8080/api/reports" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"projectId":1,"reportName":"周安全报告","reportType":"WEEKLY","templateId":1,"referenceFileIds":[1]}'
```

响应重点字段：`reportId`、`taskId`、`status`。创建接口返回 `PENDING`，实际生成由 Worker 异步执行。

校验规则：

- `reportName` 必须显式传入。
- `templateId` 必须是当前项目可用报告模板。
- `referenceFileIds` 必须属于同一项目。
- 项目必须处于可写状态。

## 2. 查询报告

```text
GET /api/reports
GET /api/reports/{reportId}
```

列表查询参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| projectId | Long | 否 | 项目 ID |
| reportType | String | 否 | 报告类型 |
| status | String | 否 | 报告状态 |
| keyword | String | 否 | 报告名称关键字 |
| pageNo | int | 否 | 默认 1 |
| pageSize | int | 否 | 默认 20 |

报告状态：

```text
DRAFT
PENDING
PROCESSING
COMPLETED
FAILED
ARCHIVED
DELETED
```

## 3. 重新生成

```text
POST /api/reports/{reportId}/regenerate
```

说明：重新创建生成任务并返回新的 `taskId`。生成中报告不应重复触发。

## 4. 下载报告

```text
GET /api/reports/{reportId}/download?format=WORD
```

当前主要支持 Word 下载。报告必须已完成，接口返回 MinIO 预签名下载地址字符串。

## 写入规则

- 报告创建必须写入报告记录、任务、outbox，并读回创建结果。
- 报告和任务状态流转必须检查影响行数。
- Worker 执行前必须重新校验项目可写。
- CryptoAgentV3 不可用时必须记录任务和报告失败原因，不能返回假成功。
- CryptoAgentV3 返回的 DOCX payload 必须包含非空文件名。
