# 项目模块接口文档

本文档描述 `project` 模块当前已实现的项目管理、项目配置和项目统计接口。项目模块是项目隔离的基础，其他项目级业务写入必须先校验项目访问权限和可写状态。

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
| `GET` | `/api/projects` | 分页查询项目 | 登录 |
| `GET` | `/api/projects/{projectId}` | 查询项目详情 | 登录 |
| `POST` | `/api/projects` | 创建项目 | `project:manage` |
| `PUT` | `/api/projects/{projectId}` | 更新项目 | `project:manage` |
| `DELETE` | `/api/projects/{projectId}` | 删除项目 | `project:manage` |
| `PUT` | `/api/projects/{projectId}/status` | 更新项目状态 | `project:manage` |
| `POST` | `/api/projects/{projectId}/enable` | 启用项目 | `project:manage` |
| `POST` | `/api/projects/{projectId}/disable` | 停用项目 | `project:manage` |
| `POST` | `/api/projects/{projectId}/archive` | 归档项目 | `project:manage` |
| `GET` | `/api/projects/{projectId}/settings` | 查询项目配置 | 登录 |
| `PUT` | `/api/projects/{projectId}/settings` | 更新项目配置 | `project:manage` |
| `GET` | `/api/projects/{projectId}/statistics` | 查询项目统计 | 登录 |

## 1. 查询项目列表

```text
GET /api/projects
```

查询参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| keyword | String | 否 | 项目名或编码关键字 |
| status | String | 否 | `ENABLED`、`DISABLED`、`ARCHIVED` |
| pageNo | int | 否 | 默认 1 |
| pageSize | int | 否 | 默认 20 |

平台管理员可跨项目查询；非平台管理员只返回自己可访问的项目。

## 2. 查询项目详情

```text
GET /api/projects/{projectId}
```

响应重点字段：`projectId`、`projectName`、`projectCode`、`location`、`status`、`description`、`createdAt`、`updatedAt`。

## 3. 创建项目

```text
POST /api/projects
Content-Type: application/json
```

请求体：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| projectName | String | 是 | 项目名称 |
| projectCode | String | 是 | 项目编码 |
| location | String | 否 | 项目地点 |
| description | String | 否 | 项目描述 |

创建成功后，后端会读回项目记录，并记录项目操作审计。

## 4. 更新和删除项目

```text
PUT /api/projects/{projectId}
DELETE /api/projects/{projectId}
```

更新请求体字段：`projectName`、`projectCode`、`location`、`description`。

删除为逻辑删除。

## 5. 更新项目状态

```text
PUT /api/projects/{projectId}/status
Content-Type: application/json
```

请求体：

```json
{
  "status": "ENABLED"
}
```

状态只允许：

```text
ENABLED
DISABLED
ARCHIVED
```

快捷接口：

```text
POST /api/projects/{projectId}/enable
POST /api/projects/{projectId}/disable
POST /api/projects/{projectId}/archive
```

说明：`DISABLED` 或 `ARCHIVED` 项目只允许读取和重新启用，业务写入应返回冲突。

## 6. 项目配置

```text
GET /api/projects/{projectId}/settings
PUT /api/projects/{projectId}/settings
```

更新请求体：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| defaultKnowledgeBaseId | Long | 否 | 默认知识库 ID |
| defaultReportTemplateId | Long | 否 | 默认报告模板 ID |
| dataRetentionDays | Integer | 否 | 数据保留天数 |
| uploadMaxSizeMb | Long | 否 | 上传最大 MB |
| internetPolicyCrawlerEnabled | Boolean | 否 | 是否启用互联网政策爬取 |
| defaultQaRouteMode | String | 否 | `AUTO`、`MODEL`、`KNOWLEDGE`、`DATABASE`、`MIXED` |
| defaultOcrLanguage | String | 否 | 默认 OCR 语言 |
| defaultReportExportFormat | String | 否 | `WORD`、`PDF` |

配置校验规则：

- 默认知识库必须属于当前项目且处于 `ENABLED`。
- 默认报告模板必须属于当前项目、类别为 `REPORT` 且处于 `ENABLED`。
- 不允许保存跨项目或已停用的默认 ID。

## 7. 项目统计

```text
GET /api/projects/{projectId}/statistics
```

响应字段：`memberCount`、`knowledgeBaseCount`、`reportCount`、`dataSourceCount`、`qaCount`、`reviewCount`、`ocrCount`、`fileStorageBytes`。

## 写入规则

- 创建、更新、删除、状态、配置变更必须记录操作审计。
- 写入项目记录、创建者成员和配置时必须检查影响行数或生成 ID。
- 返回持久化数据前必须从数据库读回。
