# 审计模块接口文档

本文档描述 `audit` 模块当前已实现的审计查询接口。审计模块负责查询操作审计日志和外部服务调用日志，写入动作由各业务模块在关键路径中完成。

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

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| `GET` | `/api/audit/logs` | 查询操作审计日志 |
| `GET` | `/api/audit/external-call-logs` | 查询外部服务调用日志 |

## 1. 查询操作审计日志

```text
GET /api/audit/logs
```

查询参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| projectId | Long | 否 | 项目 ID |
| operatorId | Long | 否 | 操作人 ID |
| action | String | 否 | 操作动作 |
| objectType | String | 否 | 对象类型 |
| createdFrom | DateTime | 否 | 创建开始时间 |
| createdTo | DateTime | 否 | 创建结束时间 |
| pageNo | int | 否 | 默认 1 |
| pageSize | int | 否 | 默认 20 |

示例：

```bash
curl --noproxy '*' "http://127.0.0.1:8080/api/audit/logs?projectId=1&pageNo=1&pageSize=20" \
  -H "Authorization: Bearer $TOKEN"
```

响应 `data.records` 重点字段：

| 字段 | 说明 |
| --- | --- |
| id | 审计日志 ID |
| projectId | 项目 ID |
| operatorId | 操作人 ID |
| action | 操作动作 |
| objectType | 对象类型 |
| objectId | 对象 ID |
| requestId | 请求 ID |
| ipAddress | 客户端 IP |
| detail | 详情 JSON 字符串 |
| createdAt | 创建时间 |

## 2. 查询外部服务调用日志

```text
GET /api/audit/external-call-logs
```

查询参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| projectId | Long | 否 | 项目 ID |
| serviceName | String | 否 | 服务名 |
| callType | String | 否 | 调用类型，如 `MODEL_INVOKE`、`OCR_RECOGNIZE` |
| status | String | 否 | `SUCCESS`、`FAILED` |
| pageNo | Integer | 否 | 默认 1 |
| pageSize | Integer | 否 | 默认 20 |

示例：

```bash
curl --noproxy '*' "http://127.0.0.1:8080/api/audit/external-call-logs?projectId=1&status=FAILED" \
  -H "Authorization: Bearer $TOKEN"
```

## 写入规则

- 项目创建、更新、删除、状态、配置变更必须记录操作审计。
- AI、OCR、RAG、报告生成等跨服务调用必须记录请求摘要、响应摘要、耗时、状态和错误信息。
- 写入审计或外部调用日志时必须检查影响行数和生成 ID，不允许假装记录成功。
- 日志中不得保存密码、token、MinIO 密钥、模型密钥、图片 base64 或生产凭据。
