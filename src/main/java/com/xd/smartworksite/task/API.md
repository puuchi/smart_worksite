# 任务模块接口文档

本文档描述 `task` 模块当前已实现的任务查询、统计、阶段日志、重试和取消接口。长任务包括报告生成、OCR 识别、知识库入库、文件解析等。

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

## 异步任务配置

任务 outbox 调度和 Worker 默认关闭。本地需要真实消费任务时配置：

```env
TASK_OUTBOX_DISPATCHER_ENABLED=true
TASK_OUTBOX_DISPATCHER_BATCH_SIZE=20
TASK_OUTBOX_DISPATCHER_FIXED_DELAY_MS=5000
TASK_WORKER_ENABLED=true
TASK_WORKER_ID=smart-worksite-worker
TASK_WORKER_POLL_DELAY_MS=2000
TASK_WORKER_POP_TIMEOUT_MS=1000
TASK_WORKER_LEASE_SECONDS=300
```

## 接口总览

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| `GET` | `/api/tasks` | 分页查询任务 |
| `GET` | `/api/tasks/statistics` | 查询任务统计 |
| `GET` | `/api/tasks/{taskId}` | 查询任务详情 |
| `GET` | `/api/tasks/{taskId}/stages` | 查询任务阶段日志 |
| `POST` | `/api/tasks/{taskId}/retry` | 重试失败任务 |
| `POST` | `/api/tasks/{taskId}/cancel` | 取消等待或运行中任务 |

## 1. 查询任务列表

```text
GET /api/tasks
```

查询参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| projectId | Long | 否 | 项目 ID |
| taskType | String | 否 | 任务类型 |
| status | String | 否 | 任务状态 |
| createdFrom | DateTime | 否 | 创建开始时间 |
| createdTo | DateTime | 否 | 创建结束时间 |
| pageNo | int | 否 | 默认 1 |
| pageSize | int | 否 | 默认 20 |

示例：

```bash
curl --noproxy '*' "http://127.0.0.1:8080/api/tasks?projectId=1&pageNo=1&pageSize=20" \
  -H "Authorization: Bearer $TOKEN"
```

## 2. 查询任务统计

```text
GET /api/tasks/statistics?projectId=1
```

响应字段：`statusCounts`、`queuedCount`、`runningCount`、`failedCount`。

## 3. 查询任务详情

```text
GET /api/tasks/{taskId}
```

响应重点字段：

| 字段 | 说明 |
| --- | --- |
| taskId | 任务 ID |
| projectId | 项目 ID |
| taskType | 任务类型 |
| bizType | 业务类型 |
| bizId | 业务 ID |
| status | 任务状态 |
| currentStage | 当前阶段 |
| retryCount | 已重试次数 |
| maxRetryCount | 最大重试次数 |
| cancelRequested | 是否请求取消 |
| errorMessage | 失败原因 |
| startedAt | 开始时间 |
| finishedAt | 结束时间 |

任务状态：

```text
PENDING
QUEUED
RUNNING
SUCCESS
FAILED
RETRYING
CANCELED
```

## 4. 查询阶段日志

```text
GET /api/tasks/{taskId}/stages
```

响应字段：`stageCode`、`status`、`inputSummary`、`outputSummary`、`errorMessage`、`startedAt`、`finishedAt`、`costMs`。

## 5. 重试失败任务

```text
POST /api/tasks/{taskId}/retry
```

规则：

- 仅允许 `FAILED` 任务重试。
- 不能超过最大重试次数。
- 重试会创建可投递的 outbox 事件。

## 6. 取消任务

```text
POST /api/tasks/{taskId}/cancel
```

规则：

- 等待中的任务可直接取消。
- 运行中的任务写入 `cancelRequested=true`，由 Worker 协作停止。
- 终态任务取消应返回冲突。

## Worker 规则

- Redis 只是投递通道，MySQL `task_outbox` 是事实源。
- Worker 领取任务必须写入 `worker_id`、`lease_until` 和心跳。
- 完成任务必须校验 owner，过期租约、非 owner 或已取消任务必须失败可见。
- 无效 Redis 消息必须记录 payload 摘要并拒绝，不能 claim 任务。
