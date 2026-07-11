# 认证与权限模块接口文档

本文档描述 `auth` 模块当前已实现的登录、当前用户、用户管理、角色权限和项目成员接口。

除 `POST /api/auth/login` 外，接口都需要：

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

本地默认管理员仅用于开发联调：

| 用户名 | 密码 |
| --- | --- |
| `admin` | `admin123` |

## 接口总览

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| `POST` | `/api/auth/login` | 登录并获取 JWT |
| `POST` | `/api/auth/logout` | 退出登录并拉黑当前 JWT |
| `GET` | `/api/auth/me` | 获取当前用户信息 |
| `PUT` | `/api/auth/me/password` | 当前用户修改密码 |
| `GET` | `/api/system/users` | 分页查询用户 |
| `POST` | `/api/system/users` | 创建用户 |
| `GET` | `/api/system/users/{userId}` | 查询用户详情 |
| `PUT` | `/api/system/users/{userId}` | 更新用户 |
| `PUT` | `/api/system/users/{userId}/status` | 启用或停用用户 |
| `PUT` | `/api/system/users/{userId}/password` | 管理员重置用户密码 |
| `GET` | `/api/system/roles` | 查询角色列表 |
| `POST` | `/api/system/roles` | 创建角色 |
| `PUT` | `/api/system/roles/{roleId}` | 更新角色 |
| `PUT` | `/api/system/roles/{roleId}/status` | 启用或停用角色 |
| `DELETE` | `/api/system/roles/{roleId}` | 删除角色 |
| `GET` | `/api/system/roles/permissions` | 查询权限列表 |
| `PUT` | `/api/system/roles/{roleId}/permissions` | 更新角色权限 |
| `GET` | `/api/projects/{projectId}/members` | 查询项目成员 |
| `POST` | `/api/projects/{projectId}/members` | 添加项目成员 |
| `PUT` | `/api/projects/{projectId}/members/{userId}` | 更新项目成员 |
| `DELETE` | `/api/projects/{projectId}/members/{userId}` | 移除项目成员 |

## 1. 登录

```text
POST /api/auth/login
Content-Type: application/json
```

请求体：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| username | String | 是 | 用户名 |
| password | String | 是 | 密码 |

示例：

```bash
curl --noproxy '*' -X POST "http://127.0.0.1:8080/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

响应 `data` 重点字段：`accessToken`、`tokenType`、`expiresIn`、`user`。

## 2. 退出登录

```text
POST /api/auth/logout
```

说明：后端会将当前 Bearer Token 加入黑名单。

## 3. 当前用户

```text
GET /api/auth/me
```

响应重点字段：`id`、`username`、`realName`、`roles`、`permissions`、`buttonPermissions`、`projects`、`defaultProjectId`。

## 4. 当前用户修改密码

```text
PUT /api/auth/me/password
Content-Type: application/json
```

请求体：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| oldPassword | String | 是 | 原密码 |
| newPassword | String | 是 | 新密码 |

## 5. 用户管理

用户管理接口要求平台管理员角色。

### 查询用户

```text
GET /api/system/users
```

查询参数：`keyword`、`status`、`pageNo`、`pageSize`。`status` 只允许 `ENABLED` 或 `DISABLED`。

### 创建用户

```text
POST /api/system/users
Content-Type: application/json
```

请求体字段：`username`、`password`、`displayName`、`phone`、`email`、`roleCodes`。

### 更新用户

```text
PUT /api/system/users/{userId}
Content-Type: application/json
```

请求体字段：`displayName`、`phone`、`email`、`roleCodes`。

### 更新用户状态

```text
PUT /api/system/users/{userId}/status?status=ENABLED
```

`status` 只允许 `ENABLED` 或 `DISABLED`。

### 重置用户密码

```text
PUT /api/system/users/{userId}/password
Content-Type: application/json
```

请求体：

```json
{
  "newPassword": "new-password"
}
```

## 6. 角色与权限

角色管理接口要求平台管理员角色。内置角色 `PLATFORM_ADMIN`、`PROJECT_ADMIN`、`BUSINESS_USER`、`VIEWER` 不允许编辑、停用、删除或重分配权限。

### 查询角色

```text
GET /api/system/roles?keyword=admin
```

### 创建角色

```text
POST /api/system/roles
Content-Type: application/json
```

请求体字段：`roleCode`、`roleName`、`description`、`permissionIds`。

### 更新角色

```text
PUT /api/system/roles/{roleId}
Content-Type: application/json
```

请求体字段：`roleName`、`description`、`permissionIds`。

### 更新角色状态

```text
PUT /api/system/roles/{roleId}/status?status=ENABLED
```

`status` 只允许 `ENABLED` 或 `DISABLED`。

### 查询权限与分配权限

```text
GET /api/system/roles/permissions
PUT /api/system/roles/{roleId}/permissions
```

分配权限请求体：

```json
{
  "permissionIds": [1, 2, 3]
}
```

## 7. 项目成员

```text
GET /api/projects/{projectId}/members
POST /api/projects/{projectId}/members
PUT /api/projects/{projectId}/members/{userId}
DELETE /api/projects/{projectId}/members/{userId}
```

添加或更新成员请求体：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| userId | Long | 创建必填 | 用户 ID |
| projectRole | String | 是 | 项目角色 |

示例：

```bash
curl --noproxy '*' -X POST "http://127.0.0.1:8080/api/projects/1/members" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"userId":2,"projectRole":"PROJECT_ADMIN"}'
```

## 写入规则

- 用户、密码、角色、角色权限、项目成员、最后登录时间写入都必须检查影响行数。
- JWT 鉴权会回查当前用户状态，停用或删除用户不能继续使用旧 token。
- 登录失败计数和临时锁定使用 Redis，不允许静默重置损坏计数。
