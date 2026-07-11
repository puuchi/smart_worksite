# 知识库模块接口文档

本文档描述 `knowledge` 模块当前已实现的知识库和知识文档接口。知识库文档入库由 Java 创建异步任务，实际切片、Embedding、向量存储和检索由 `python-ai-service` 执行。

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

## 接口总览

| 方法 | 路径 | 用途 | 权限 |
| --- | --- | --- | --- |
| `POST` | `/api/projects/{projectId}/knowledge-bases` | 创建知识库 | `knowledge:manage` |
| `GET` | `/api/projects/{projectId}/knowledge-bases` | 查询项目知识库 | 登录 |
| `GET` | `/api/knowledge-bases/{knowledgeBaseId}` | 查询知识库详情 | 登录 |
| `PUT` | `/api/knowledge-bases/{knowledgeBaseId}` | 更新知识库 | `knowledge:manage` |
| `POST` | `/api/knowledge-bases/{knowledgeBaseId}/enable` | 启用知识库 | `knowledge:manage` |
| `POST` | `/api/knowledge-bases/{knowledgeBaseId}/disable` | 停用知识库 | `knowledge:manage` |
| `DELETE` | `/api/knowledge-bases/{knowledgeBaseId}` | 删除知识库 | `knowledge:manage` |
| `POST` | `/api/knowledge-bases/{knowledgeBaseId}/documents` | 上传知识文档 | `knowledge:manage` |
| `GET` | `/api/knowledge-bases/{knowledgeBaseId}/documents` | 查询知识文档 | 登录 |
| `GET` | `/api/knowledge-documents/{documentId}` | 查询文档详情 | 登录 |
| `POST` | `/api/knowledge-documents/{documentId}/index` | 创建文档入库任务 | `knowledge:manage` |
| `DELETE` | `/api/knowledge-documents/{documentId}` | 删除知识文档 | `knowledge:manage` |

## 1. 创建知识库

```text
POST /api/projects/{projectId}/knowledge-bases
Content-Type: application/json
```

请求体：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| name | String | 是 | 知识库名称 |
| domain | String | 否 | 知识领域 |
| description | String | 否 | 描述 |

## 2. 查询知识库列表

```text
GET /api/projects/{projectId}/knowledge-bases
```

查询参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| status | String | 否 | `ENABLED`、`DISABLED` |
| domain | String | 否 | 知识领域 |
| keyword | String | 否 | 名称关键字 |
| pageNo | int | 否 | 默认 1 |
| pageSize | int | 否 | 默认 20 |

## 3. 查询、更新、启停、删除知识库

```text
GET /api/knowledge-bases/{knowledgeBaseId}
PUT /api/knowledge-bases/{knowledgeBaseId}
POST /api/knowledge-bases/{knowledgeBaseId}/enable
POST /api/knowledge-bases/{knowledgeBaseId}/disable
DELETE /api/knowledge-bases/{knowledgeBaseId}
```

更新请求体字段：`name`、`domain`、`description`。

## 4. 上传知识文档

```text
POST /api/knowledge-bases/{knowledgeBaseId}/documents
Content-Type: multipart/form-data
```

请求参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| file | MultipartFile | 是 | 文档文件 |
| title | String | 否 | 文档标题，未传可使用文件名 |
| sourceType | String | 否 | 来源类型 |

示例：

```bash
curl --noproxy '*' -X POST "http://127.0.0.1:8080/api/knowledge-bases/1/documents" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/tmp/manual.docx" \
  -F "title=项目管理手册" \
  -F "sourceType=MANUAL"
```

响应重点字段：`documentId`、`fileId`、`indexStatus`、`taskId`、`errorMessage`。

## 5. 查询知识文档

```text
GET /api/knowledge-bases/{knowledgeBaseId}/documents
GET /api/knowledge-documents/{documentId}
```

列表查询参数：`indexStatus`、`keyword`、`pageNo`、`pageSize`。

索引状态：

```text
PENDING
INDEXING
SUCCESS
FAILED
```

## 6. 创建文档入库任务

```text
POST /api/knowledge-documents/{documentId}/index
```

说明：

- 仅允许 `PENDING` 或 `FAILED` 文档提交入库。
- `INDEXING` 和 `SUCCESS` 文档重复提交应返回冲突。
- Worker 执行前必须读取成功的文件解析内容；解析内容缺失、空白或不可读时任务失败。
- Java 不直接访问向量库、不做 Embedding。

## 7. 删除知识文档

```text
DELETE /api/knowledge-documents/{documentId}
```

## 异步任务配置

文档入库依赖任务 outbox 和 Worker：

```env
TASK_OUTBOX_DISPATCHER_ENABLED=true
TASK_WORKER_ENABLED=true
AI_PYTHON_BASE_URL=http://127.0.0.1:8015
AI_PYTHON_API_KEY=dev-ai-service-key
```

## 写入规则

- 知识库更新必须检查影响行数。
- 文档上传必须校验生成 ID，并读回持久化记录后再返回。
- 入库状态写入 `INDEXING`、`SUCCESS`、`FAILED` 必须检查影响行数。
- 如果失败状态本身无法落库，必须暴露原始错误和落库失败信息。
