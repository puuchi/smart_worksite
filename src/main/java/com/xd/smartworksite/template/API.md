# 模板模块接口文档

本文档描述 `template` 模块当前已实现的通用模板、报告模板兼容接口和审查模板兼容接口。

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
| `POST` | `/api/templates` | 上传通用模板 |
| `GET` | `/api/templates` | 分页查询模板 |
| `GET` | `/api/templates/{templateId}` | 查询模板详情 |
| `PUT` | `/api/templates/{templateId}` | 更新模板元数据 |
| `POST` | `/api/templates/{templateId}/enable` | 启用模板 |
| `POST` | `/api/templates/{templateId}/disable` | 停用模板 |
| `DELETE` | `/api/templates/{templateId}` | 删除模板 |
| `POST` | `/api/templates/report` | 上传报告模板 |
| `POST` | `/api/templates/review` | 上传审查模板 |
| `POST` | `/api/report/templates` | 上传报告模板兼容接口 |
| `GET` | `/api/report/templates` | 查询报告模板 |
| `GET` | `/api/report/templates/{templateId}/variables` | 解析报告模板变量 |
| `POST` | `/api/review/templates` | 上传审查模板兼容接口 |
| `GET` | `/api/review/templates` | 查询审查模板 |
| `GET` | `/api/review/templates/{templateId}` | 查询审查模板详情 |
| `PUT` | `/api/review/templates/{templateId}` | 更新审查模板 |
| `POST` | `/api/review/templates/{templateId}/enable` | 启用审查模板 |
| `POST` | `/api/review/templates/{templateId}/disable` | 停用审查模板 |
| `DELETE` | `/api/review/templates/{templateId}` | 删除审查模板 |

## 1. 上传通用模板

```text
POST /api/templates
Content-Type: multipart/form-data
```

请求参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| projectId | Long | 是 | 项目 ID |
| templateCategory | String | 是 | `REPORT` 或 `REVIEW` |
| templateName | String | 是 | 模板名称 |
| templateType | String | 是 | 模板类型 |
| scenario | String | 否 | 使用场景 |
| versionNo | String | 否 | 版本号 |
| description | String | 否 | 描述 |
| file | MultipartFile | 是 | 模板文件 |

示例：

```bash
curl --noproxy '*' -X POST "http://127.0.0.1:8080/api/templates" \
  -H "Authorization: Bearer $TOKEN" \
  -F "projectId=1" \
  -F "templateCategory=REPORT" \
  -F "templateName=周报模板" \
  -F "templateType=WEEKLY" \
  -F "file=@/tmp/template.docx"
```

## 2. 查询模板

```text
GET /api/templates
GET /api/templates/{templateId}
```

列表查询参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| projectId | Long | 否 | 项目 ID |
| templateCategory | String | 否 | `REPORT`、`REVIEW` |
| templateType | String | 否 | 模板类型 |
| status | String | 否 | `ENABLED`、`DISABLED` |
| keyword | String | 否 | 名称关键字 |
| pageNo | int | 否 | 默认 1 |
| pageSize | int | 否 | 默认 20 |

无效 `templateCategory` 或 `status` 必须返回参数错误，不能静默查询空结果。

## 3. 更新、启停、删除模板

```text
PUT /api/templates/{templateId}
POST /api/templates/{templateId}/enable
POST /api/templates/{templateId}/disable
DELETE /api/templates/{templateId}
```

更新请求体字段：`templateName`、`templateType`、`scenario`、`versionNo`、`description`。

## 4. 报告模板接口

上传报告模板：

```text
POST /api/templates/report
POST /api/report/templates
Content-Type: multipart/form-data
```

请求参数：`projectId`、`templateName`、`templateType`、`scenario`、`versionNo`、`description`、`file`。前端必须显式传 `templateName` 和 `templateType`，不能依赖文件名兜底。

查询报告模板：

```text
GET /api/report/templates
```

解析报告模板变量：

```text
GET /api/report/templates/{templateId}/variables
```

变量解析规则：

- 后端读取已上传模板文件真实内容。
- 当前支持 DOCX、TXT、MD 中的 `${变量名}` 占位符。
- 文件缺失、跨项目不一致、内容为空、格式不支持或对象存储读取失败时直接返回错误。
- 不允许返回假空列表。

## 5. 审查模板接口

上传审查模板：

```text
POST /api/templates/review
POST /api/review/templates
Content-Type: multipart/form-data
```

审查模板兼容接口只允许操作 `REVIEW` 类别模板：

```text
GET /api/review/templates
GET /api/review/templates/{templateId}
PUT /api/review/templates/{templateId}
POST /api/review/templates/{templateId}/enable
POST /api/review/templates/{templateId}/disable
DELETE /api/review/templates/{templateId}
```

## 写入规则

- 模板上传必须要求非空原始文件名、显式 `templateName` 和 `templateType`。
- 存储上传失败必须返回可见错误，不允许创建兜底模板元数据。
- 创建后必须读回持久化记录再返回。
- 更新、启用、停用、删除必须检查影响行数。
