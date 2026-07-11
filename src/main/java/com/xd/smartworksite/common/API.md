# 通用模块接口文档

`common` 模块不直接暴露业务 API。它提供统一响应、分页结构、异常码、请求 ID、全局异常处理、JWT 鉴权过滤器、Redis Key 和 MyBatis 基础配置。

## 统一响应结构

所有业务接口默认返回 HTTP 200，并通过 `code` 表示业务结果：

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "requestId": "xxx",
  "timestamp": "2026-07-11T16:00:00+08:00"
}
```

常见错误响应：

```json
{
  "code": 40100,
  "message": "未登录或登录已过期",
  "data": null,
  "requestId": "xxx",
  "timestamp": "2026-07-11T16:00:00+08:00"
}
```

## 分页结构

分页接口的 `data` 使用 `PageResult`：

```json
{
  "records": [],
  "total": 0,
  "pageNo": 1,
  "pageSize": 20
}
```

## 请求 ID

请求 ID 由 `RequestIdFilter` 处理：

```http
X-Request-Id: <request-id>
```

如果客户端没有传入，后端会生成一个请求 ID，并在响应头和响应体中返回。

## 鉴权规则

以下接口放行：

```text
POST /api/auth/login
GET  /api/system/ping
GET  /actuator/health
GET  /actuator/info
```

其他接口默认需要：

```http
Authorization: Bearer <accessToken>
```

方法级权限通过 `@PreAuthorize` 控制，例如：

```text
project:manage
knowledge:manage
datasource:manage
qa:view
qa:manage
review:view
review:manage
ocr:view
```

## 错误处理约定

- 参数错误、业务冲突、权限不足、资源不存在等场景使用 `BusinessException` 和统一错误码。
- 不允许用兜底数据隐藏真实错误。
- 写入类操作必须检查影响行数或生成 ID。
- 跨服务调用失败必须保留原始错误，并写入可观测日志。

## 安全日志规则

日志、审计和外部调用摘要不得输出：

```text
密码
JWT token
MinIO secret
AI service key
Qwen API key
生产数据库凭据
图片 base64
身份证号等敏感明文
```
