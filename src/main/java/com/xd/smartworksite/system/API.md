# 系统模块接口文档

本文档描述 `system` 模块当前已实现的探活、版本、运行时和依赖健康检查接口。

公开接口：

```text
GET /api/system/ping
GET /actuator/health
GET /actuator/info
```

其他系统接口默认需要：

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

| 方法 | 路径 | 用途 | 鉴权 |
| --- | --- | --- | --- |
| `GET` | `/api/system/ping` | 系统探活 | 公开 |
| `GET` | `/api/system/version` | 查询版本信息 | 登录 |
| `GET` | `/api/system/runtime` | 查询运行时信息 | 登录 |
| `GET` | `/api/system/dependencies/health` | 查询依赖健康状态 | 登录 |
| `GET` | `/actuator/health` | Spring Actuator 健康检查 | 公开 |
| `GET` | `/actuator/info` | Spring Actuator 信息 | 公开 |

## 1. 系统探活

```text
GET /api/system/ping
```

示例：

```bash
curl --noproxy '*' "http://127.0.0.1:8080/api/system/ping"
```

响应 `data`：

| 字段 | 说明 |
| --- | --- |
| status | `UP` |
| service | `smart-worksite` |
| time | 服务端时间 |

## 2. 查询版本

```text
GET /api/system/version
```

响应字段：`applicationName`、`artifactVersion`、`springBootVersion`、`javaVersion`、`serverTime`。

## 3. 查询运行时

```text
GET /api/system/runtime
```

响应字段：

| 字段 | 说明 |
| --- | --- |
| applicationName | 应用名 |
| activeProfiles | 激活 profile |
| javaVersion | Java 版本 |
| osName | 操作系统 |
| osVersion | 操作系统版本 |
| availableProcessors | CPU 核心数 |
| maxMemoryBytes | JVM 最大内存 |
| totalMemoryBytes | JVM 当前总内存 |
| freeMemoryBytes | JVM 空闲内存 |
| serverTime | 服务端时间 |

## 4. 查询依赖健康

```text
GET /api/system/dependencies/health
```

该接口检查 MySQL、Redis 和 MinIO，并返回每个依赖的状态、耗时和错误原因。

响应示例：

```json
{
  "status": "UP",
  "checkedAt": "2026-07-11T16:00:00+08:00",
  "dependencies": {
    "mysql": {
      "status": "UP",
      "elapsedMs": 2,
      "errorMessage": null
    },
    "redis": {
      "status": "UP",
      "elapsedMs": 3,
      "errorMessage": null
    },
    "minio": {
      "status": "UP",
      "elapsedMs": 5,
      "errorMessage": null
    }
  }
}
```

## 可观测性要求

- 依赖不可用时返回具体依赖名称和错误原因。
- 不允许把 MySQL、Redis、MinIO 的失败包装成无法定位的泛化错误。
- 健康检查不得泄露数据库密码、Redis 密码或 MinIO Secret。
