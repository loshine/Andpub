# 华为 AppGallery Connect API 传包与发布接入文档

来源：

- https://developer.huawei.com/consumer/cn/doc/AppGallery-connect-Guides/agcapi-getstarted-0000001111845114
- https://developer.huawei.com/consumer/cn/doc/AppGallery-connect-References/agcapi-appid-list-0000001111845086
- https://developer.huawei.com/consumer/cn/doc/AppGallery-connect-References/agcapi-app-info-query-0000001158365045
- https://developer.huawei.com/consumer/cn/doc/AppGallery-connect-References/agcapi-app-info-update-0000001111685198
- https://developer.huawei.com/consumer/cn/doc/AppGallery-connect-References/agcapi-language-info-update-0000001158245057
- https://developer.huawei.com/consumer/cn/doc/AppGallery-connect-References/agcapi-language-info-delete-0000001111845088
- https://developer.huawei.com/consumer/cn/doc/AppGallery-connect-References/agcapi-app-file-info-0000001111685202
- https://developer.huawei.com/consumer/cn/doc/AppGallery-connect-References/agcapi-app-submit-0000001158245061
- https://developer.huawei.com/consumer/cn/doc/AppGallery-connect-References/agcapi-app-submit-with-file-0000001111845092
- https://developer.huawei.com/consumer/cn/doc/AppGallery-connect-References/agcapi-phased-release-0000001111685204
- https://developer.huawei.com/consumer/cn/doc/AppGallery-connect-References/agcapi-notify-release-0000001158245063
- https://developer.huawei.com/consumer/cn/doc/AppGallery-connect-References/agcapi-gms-0000001111845094
- https://developer.huawei.com/consumer/cn/doc/AppGallery-connect-References/agcapi-update-releasetime-0000001158365053
- https://developer.huawei.com/consumer/cn/doc/AppGallery-connect-References/agcapi-query-aabfile-0000001111685206
- https://developer.huawei.com/consumer/cn/doc/AppGallery-connect-References/agcapi-add-packageurl-0000001158245065
- https://developer.huawei.com/consumer/cn/doc/AppGallery-connect-References/agcapi-obbfile-package-list-0000001111685210
- https://developer.huawei.com/consumer/cn/doc/AppGallery-connect-References/agcapi-publishingapi-errorcode-0000001163523297

整理时间：2026-05-15

## 1. 能力范围

华为 AppGallery Connect Publishing API 用于通过接口维护应用资料、上传或提交软件包、提交审核、控制发布节奏。本文只整理上面来源链接覆盖的能力：

- 根据包名查询 `appId`。
- 查询和更新应用基础信息。
- 新增、更新、删除多语言描述信息。
- 更新应用文件信息，把已上传文件对象绑定到应用。
- 通过公网下载地址提交软件包。
- 通过公网下载地址直接提交发布。
- 提交已有软件包审核。
- 查询当前关联软件包列表。
- 查询 AAB 软件包编译状态。
- 更新分阶段发布、版本上架时间、GMS 依赖属性。
- 接收异步处理回调。
- 查询 Publishing API 错误码。

接口基地址：

```text
https://connect-api.cloud.huawei.com
```

本文中的接口路径都以该基地址为前缀。

## 2. 鉴权方式

官方 Getting Started 文档现在列出三种授权方式。本项目默认使用 API 客户端方式，与现有 AppGallery 发布插件保持一致；Service Account 和 OAuth 客户端保留为显式可选方式。实际接入选一种，别混着传。

### 2.1 Service Account 方式

Service Account 用于服务器与服务器之间的接口鉴权。官方文档说明它相比 API 客户端方式更安全，并提示新创建凭据应选择 Service Account，已有 API 客户端应尽快切换。

基本流程：

1. 登录 AppGallery Connect，进入“用户与访问”。
2. 左侧导航选择“API密钥 > Connect API”。
3. 在“Service Account”页签创建 Service Account。
4. 类型选择“开发者级”。Connect API 要求使用开发者级凭据。
5. 选择角色；角色决定该 Service Account 可访问哪些 AppGallery Connect API。
6. 创建成功后保存自动下载的 `******private.json` 凭据文件。

Service Account JSON 凭据包含以下关键字段：

| 字段 | 说明 |
| --- | --- |
| `key_id` | 密钥 ID，用于 JWT Header 的 `kid` |
| `private_key` | 私钥，用于 JWT 签名 |
| `sub_account` | Service Account 标识，用于 JWT Payload 的 `iss` |
| `token_uri` | Token endpoint，示例为 `https://oauth-login.cloud.huawei.com/oauth2/v3/token` |

获取鉴权令牌：

1. 用 `key_id` 和 `private_key` 生成 JWT。
2. JWT Header 字段：

| 字段 | 值 |
| --- | --- |
| `kid` | `key_id` |
| `typ` | `JWT` |
| `alg` | `PS256` |

3. JWT Payload 使用以下字段：

| 字段 | 值 |
| --- | --- |
| `aud` | `https://oauth-login.cloud.huawei.com/oauth2/v3/token` |
| `iss` | `sub_account` |
| `exp` | 过期时间，UTC 时间戳，等于 `iat + 3600` 秒 |
| `iat` | 签发时间，UTC 时间戳，单位秒 |

4. 将 Base64URL 编码后的 Header 和 Payload 用 `.` 拼接，使用 `private_key` 和 `SHA256withRSA/PSS` 签名，再对签名做 Base64URL 编码，得到 `header.payload.signature`。
5. 得到的 `header.payload.signature` 就是 Service Account 鉴权令牌。
6. 访问具体 AppGallery Connect API 时，将该 JWT 放入 Header：`Authorization: Bearer ${jwt}`。

注意：

- Service Account 私钥必须按密钥处理，不能写进仓库、日志或崩溃报告。
- 本项目支持粘贴官方 `private.json`，也支持拆字段录入 `key_id`、`private_key`、`sub_account`、`token_uri`。
- 新建华为渠道默认使用 API 客户端；旧的 Service Account 配置按 Service Account 方式兼容读取。

### 2.2 API 客户端方式

API 客户端方式，所有 Publishing API 请求 Header 带：

| Header | 必选 | 说明 |
| --- | --- | --- |
| `client_id` | 是 | API 客户端 ID |
| `Authorization` | 是 | `Bearer ${access_token}`，`access_token` 来自获取 Token 接口 |

创建 API 客户端时，“项目”保持 `N/A`，表示团队级 API 客户端；官方文档提示如果不为 `N/A`，调用 API 可能返回 403。

获取访问 API 的 Token：

```http
POST /api/oauth2/v1/token HTTP/1.1
Host: connect-api.cloud.huawei.com
Content-Type: application/json

{
  "client_id": "${client_id}",
  "client_secret": "${client_secret}",
  "grant_type": "client_credentials"
}
```

### 2.3 OAuth 客户端方式

OAuth 客户端方式，Header 带：

| Header | 必选 | 说明 |
| --- | --- | --- |
| `teamId` | 是 | 开发者团队 ID |
| `oauth2Token` | 是 | 用户授权得到的 Access Token |

OAuth 客户端方式面向平台类开发者；官方文档说明普通应用开发者暂无法使用。该方式需要申请 Scope、对接华为账号服务、获取用户授权码，再以用户身份访问 API。Publishing API、Upload Management API 和 Testing API 对应的 Scope URL 都是：

```text
https://www.huawei.com/auth/agc/publish
```

JSON 接口统一使用：

```text
Content-Type: application/json
```

## 3. 推荐传包流程

更新已有 Android 应用，最直接的链路是让华为后台从公网 URL 拉包并提交审核：

1. 按选定鉴权方式获取访问令牌。
2. 调用 `GET /api/publish/v2/appid-list`，用包名查 `appId`。
3. 可选：调用 `GET /api/publish/v2/app-info` 查询当前应用资料。
4. 可选：调用 `PUT /api/publish/v2/app-info`、`PUT /api/publish/v2/app-language-info` 更新应用资料。
5. 调用 `POST /api/publish/v2/app-submit-with-file`，传 `downloadUrl`、`downloadFileName`、`requestId`，华为后台下载软件包并启动发布流程。
6. 如果传了 `callbackAddr`，接收华为回调；否则定期调用 `GET /api/publish/v2/app-info` 判断是否提交审核成功。
7. AAB 场景可用 `GET /api/publish/v2/package/compile/status` 查询编译状态。

如果只想先让华为下载软件包，但不提交审核，使用：

```text
POST /api/publish/v2/app-package-file/by-url
```

如果你已经通过华为文件上传链路拿到了文件对象 ID，则调用：

```text
PUT /api/publish/v2/app-file-info
POST /api/publish/v2/app-submit
```

注意：`POST /api/publish/v2/app-submit` 的官方说明要求软件包已上传，并建议传包后等待 2 分钟再提交审核。别把异步解析当同步成功。

## 4. 接口总览

| 能力 | 方法 | 路径 |
| --- | --- | --- |
| 查询包名对应 `appId` | GET | `/api/publish/v2/appid-list` |
| 查询应用信息 | GET | `/api/publish/v2/app-info` |
| 更新应用基本信息 | PUT | `/api/publish/v2/app-info` |
| 更新语言描述信息 | PUT | `/api/publish/v2/app-language-info` |
| 删除语言描述信息 | DELETE | `/api/publish/v2/app-language-info` |
| 更新应用文件信息 | PUT | `/api/publish/v2/app-file-info` |
| 提交发布 | POST | `/api/publish/v2/app-submit` |
| 通过下载方式提交发布 | POST | `/api/publish/v2/app-submit-with-file` |
| 通过下载方式提交软件包 | POST | `/api/publish/v2/app-package-file/by-url` |
| 查询软件包列表 | GET | `/api/publish/v2/package-list` |
| 查询软件包编译状态 | GET | `/api/publish/v2/package/compile/status` |
| 更新分阶段发布 | PUT | `/api/publish/v2/phased-release` |
| 更新版本上架时间 | PUT | `/api/publish/v2/on-shelf-time` |
| 设置应用 GMS 依赖属性 | PUT | `/api/publish/v2/properties/gms` |
| 回调结果通知 | POST | `callbackAddr` 指定的开发者服务器地址 |

## 5. 查询包名对应 appId

接口：

```text
GET /api/publish/v2/appid-list
```

Query：

| 参数 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `packageName` | 是 | String(4096) | 应用包名；多个包名用英文逗号分隔，最多 50 个 |
| `packageTypes` | 否 | String(32) | 软件包类型过滤：`1` APK，`2` RPK，`8` EXE；多值用英文逗号分隔 |
| `pcVersionName` | 否 | String(255) | Windows 应用版本号过滤；仅 `packageTypes=8` 且只有一个 Windows 应用时有效 |

请求示例：

```http
GET /api/publish/v2/appid-list?packageName=com.example.app HTTP/1.1
Host: connect-api.cloud.huawei.com
client_id: ${client_id}
Authorization: Bearer ${access_token}
```

响应关键字段：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `ret` | Object | 返回码和描述 |
| `appids` | List<Pair> | 包名对应的应用 ID 列表 |
| `appids[].key` | String | 应用名称 |
| `appids[].value` | String | 应用 ID |

## 6. 查询应用信息

接口：

```text
GET /api/publish/v2/app-info
```

Query：

| 参数 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `appId` | 是 | String | 应用 ID |
| `lang` | 否 | String | 指定语言；不传则查询全部语言信息 |
| `releaseType` | 否 | Integer | 发布方式：`1` 全网，`3` 分阶段；默认 `1` |

用途：更新前先查一遍当前资料，避免把未提交字段覆盖空。接口返回应用基本信息、语言描述、文件信息、版本状态等应用详情。

## 7. 更新应用基本信息

接口：

```text
PUT /api/publish/v2/app-info
```

Query：

| 参数 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `appId` | 是 | String | 应用 ID |
| `releaseType` | 否 | Integer | `1` 全网，`3` 分阶段；默认 `1` |

Body 关键字段：

| 参数 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `privacyPolicy` | 是 | String(255) | 隐私声明地址 |
| `defaultLang` | 否 | String(64) | 默认语言 |
| `childType` | 否 | Integer | 二级分类 ID |
| `grandChildType` | 否 | Integer | 三级分类 ID |
| `appNetType` | 否 | Integer | 联网类型：`1` 单机，`2` 网游 |
| `isFree` | 否 | Integer | 付费类型：`1` 免费，`0` 付费 |
| `price` | 否 | String | 价格，`isFree=0` 时必填 |
| `priceDetail` | 否 | String | 国家/地区维度价格数组 JSON，`isFree=0` 时必填 |
| `publishCountry` | 否 | String | 发布国家码，多值用英文逗号分隔；不能只传 `OTHER` |
| `isAppForcedUpdate` | 否 | Integer | 是否强制升级：`0` 否，`1` 是 |
| `hispaceAutoDown` | 否 | Integer | 是否允许华为应用市场抓包：`0` 不允许，`1` 允许 |
| `deviceTypeInfo` | 否 | Object | 设备类型信息 |

`deviceTypeInfo`：

| 参数 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `deviceType` | 是 | Integer | 设备类型：`4` 手机，`6` VR，`7` 手表，`8` 大屏，`9` 路由器，`10` 车机 |
| `appAdapters` | 是 | String | 适配设备类型，多值用英文逗号分隔。修改设备类型时该字段必传 |

## 8. 语言描述信息

更新或新增语言描述：

```text
PUT /api/publish/v2/app-language-info
```

删除语言描述：

```text
DELETE /api/publish/v2/app-language-info
```

更新 Query：

| 参数 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `appId` | 是 | String | 应用 ID |
| `releaseType` | 否 | Integer | `1` 全网，`3` 分阶段；默认 `1` |

更新 Body：

| 参数 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `lang` | 是 | String(64) | 语言类型，例如 `zh-CN` |
| `appName` | 否 | String(64) | 应用名称；新增语言时必填 |
| `appDesc` | 否 | String(8000) | 应用描述 |
| `briefInfo` | 否 | String(80) | 一句话简介 |
| `newFeatures` | 否 | String(500) | 新版本简介 |

删除 Query：

| 参数 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `appId` | 是 | String | 应用 ID |
| `lang` | 是 | String | 需要删除的语言 |
| `releaseType` | 否 | Integer | `1` 全网，`3` 分阶段；默认 `1` |

默认语言不支持删除。

## 9. 更新应用文件信息

接口：

```text
PUT /api/publish/v2/app-file-info
```

用途：图片、视频、APK、RPK、AAB 等文件上传完成后，用该接口刷新并绑定应用文件信息。

Query：

| 参数 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `appId` | 是 | String | 应用 ID |
| `releaseType` | 否 | Integer | `1` 全网，`3` 分阶段；默认 `1` |

Body 关键字段：

| 参数 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `fileType` | 是 | Integer | 文件类型。软件包为 `5` |
| `files` | 是 | List<FileInfo> | 文件信息 |
| `lang` | 否 | String(8) | 更新图片、视频时必填 |
| `imgShowType` | 否 | Integer | 截图展现方式：`0` 竖屏，`1` 横屏 |
| `videoShowType` | 否 | Integer | 视频展现方式：`0` 竖屏，`1` 横屏 |
| `clearArk` | 否 | Integer | 是否清除方舟包：`1` 是，`0` 否；默认 `0` |
| `sensitivePermissionDesc` | 否 | String(2048) | 敏感权限描述 |

`FileInfo`：

| 参数 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `fileName` | 否 | String | 文件名。`fileType=5` 时必填 |
| `fileDestUrl` | 是 | String | 文件服务器对象 ID，即上传文件后得到的 `objectId` |
| `versionName` | 否 | String | Windows EXE 软件包版本号；EXE 时必填 |
| `displayName` | 否 | String | Windows 应用注册表显示名称；EXE 时必填 |

软件包绑定示例：

```http
PUT /api/publish/v2/app-file-info?appId=${appId} HTTP/1.1
Host: connect-api.cloud.huawei.com
client_id: ${client_id}
Content-Type: application/json
Authorization: Bearer ${access_token}

{
  "fileType": 5,
  "files": [
    {
      "fileName": "example.apk",
      "fileDestUrl": "${objectId}"
    }
  ]
}
```

## 10. 提交已有软件包发布

接口：

```text
POST /api/publish/v2/app-submit
```

调用前要求应用信息完整，应用软件包已上传。官方建议传包后等待 2 分钟再调用。

Query：

| 参数 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `appId` | 是 | String | 应用 ID |
| `releaseTime` | 否 | String | 指定发布 UTC 时间，格式 `yyyy-MM-ddTHH:mm:ssZZ`；不填则审核通过立即上架 |
| `remark` | 否 | String | 提审备注；填写时长度 10-300 |
| `releaseType` | 否 | Integer | `1` 全网，`3` 分阶段；默认 `1` |

分阶段发布时 Body 传：

| 参数 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `phasedReleaseStartTime` | 是 | String(64) | 分阶段发布开始时间，UTC 格式 `yyyy-MM-ddTHH:mm:ssZZ` |
| `phasedReleaseEndTime` | 是 | String(64) | 分阶段发布结束时间 |
| `phasedReleasePercent` | 是 | String(10) | 百分比，不含 `%`；Android 要求 `0.00` 到 `100.00` 且两位小数 |
| `phasedReleaseDescription` | 是 | String(500) | 分阶段发布说明 |
| `isPureDetection` | 否 | Integer | 是否申请绿色应用认证：`0` 否，`1` 是 |
| `sensitivePermissionIconUrl` | 否 | String | 绿色认证审核材料 objectId；`isPureDetection=1` 时必填 |

普通全网发布可以不传 Body 或传空对象，按实际 HTTP 客户端能力处理。

## 11. 通过下载方式提交发布

接口：

```text
POST /api/publish/v2/app-submit-with-file
```

用途：软件包在自己的服务器上时，传公网下载地址给华为。华为服务器下载软件包后启动发布流程。该接口是异步接口，立即返回成功不代表最终发布成功。

Query：

| 参数 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `appId` | 是 | String | 应用 ID |

Body：

| 参数 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `downloadUrl` | 是 | String | 软件包下载地址，支持 APK/RPK/AAB/EXE |
| `downloadFileName` | 是 | String(64) | 下载文件名，带后缀 |
| `requestId` | 是 | String | 自定义请求 ID，必须唯一；回调时原样携带 |
| `callbackAddr` | 否 | String | 回调地址 |
| `releaseTime` | 否 | String(64) | 指定发布时间；不填则审核通过立即上架 |
| `remark` | 否 | String(300) | 提交发布备注；填写时长度 10-300 |
| `releaseType` | 否 | Integer | `1` 全网，`3` 分阶段；默认 `1` |
| `clearArk` | 否 | String | 是否清除方舟包：`1` 是，`0` 否；默认 `0` |
| `sensitivePermissionDesc` | 否 | String | 敏感信息 |
| `phasedReleaseInfo` | 否 | Object | 分阶段发布信息；`releaseType=3` 时必填 |
| `versionName` | 否 | String | Windows EXE 软件包版本号；EXE 时必填 |
| `displayName` | 否 | String | Windows 应用注册表显示名称；EXE 时必填 |

文件大小限制：

| 类型 | 限制 |
| --- | --- |
| APK | 不超过 4GB |
| RPK | 快应用不超过 20MB，快游戏不超过 30MB |
| AAB | 不超过 150MB |
| EXE | 不超过 4GB |

请求示例：

```http
POST /api/publish/v2/app-submit-with-file?appId=${appId} HTTP/1.1
Host: connect-api.cloud.huawei.com
client_id: ${client_id}
Content-Type: application/json
Authorization: Bearer ${access_token}

{
  "downloadUrl": "https://example.com/app-release.apk",
  "downloadFileName": "app-release.apk",
  "requestId": "release-20260515-001",
  "callbackAddr": "https://example.com/huawei/callback"
}
```

## 12. 通过下载方式提交软件包

接口：

```text
POST /api/publish/v2/app-package-file/by-url
```

用途：让华为服务器从开发者提供的 URL 下载软件包。官方说明该操作只提交软件包，不发布软件包，也不会关联版本。

Query：

| 参数 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `appId` | 是 | String | 应用 ID |

Body：

| 参数 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `downloadUrl` | 是 | String(256) | 软件包下载地址 |
| `downloadFileName` | 是 | String(64) | 文件名，带后缀 |
| `requestId` | 是 | String(64) | 自定义请求 ID，必须唯一 |
| `callbackAddr` | 否 | String(256) | 回调地址 |
| `versionName` | 否 | String(255) | Windows EXE 软件包版本号；EXE 时必填 |
| `displayName` | 否 | String(255) | Windows 应用注册表显示名称；EXE 时必填 |
| `packageType` | 否 | Integer | 软件包类型：`1` APK，`2` RPK，`8` EXE |

响应只表示请求被接受。最终处理结果看回调或后续查询。

## 13. 查询软件包列表

接口：

```text
GET /api/publish/v2/package-list
```

Query：

| 参数 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `appId` | 是 | String | 应用 ID |
| `fromRecCount` | 否 | Integer | 查询起始记录号，默认 `1` |
| `maxReqCount` | 否 | Integer | 查询数量，最大 `100`，默认 `10` |

用途：查询应用当前关联的软件包列表。AAB 编译状态接口中的 `pkgIds` 来自该接口返回的软件包版本参数。

## 14. 查询 AAB 编译状态

接口：

```text
GET /api/publish/v2/package/compile/status
```

Query：

| 参数 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `appId` | 是 | String | 应用 ID |
| `pkgIds` | 是 | String | 待查询的软件包 ID，多个 ID 用英文逗号分隔 |

请求示例：

```http
GET /api/publish/v2/package/compile/status?appId=${appId}&pkgIds=${pkgVersion} HTTP/1.1
Host: connect-api.cloud.huawei.com
client_id: ${client_id}
Authorization: Bearer ${access_token}
```

## 15. 分阶段发布

提交发布时设置分阶段发布：

- `releaseType=3`
- Body 传 `phasedReleaseStartTime`、`phasedReleaseEndTime`、`phasedReleasePercent`、`phasedReleaseDescription`

更新已存在的分阶段发布：

```text
PUT /api/publish/v2/phased-release
```

Query：

| 参数 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `appId` | 是 | String | 应用 ID |
| `releaseType` | 否 | Integer | `1` 改为全网发布，`3` 更新分阶段设置；默认 `3` |

Body：

| 参数 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `state` | 否 | String | `SUSPEND` 暂停，`RELEASE` 恢复发布中 |
| `phasedReleaseStartTime` | 否 | String(64) | 开始时间；已上架分阶段发布应用不允许修改开始时间 |
| `phasedReleaseEndTime` | 否 | String(64) | 结束时间 |
| `phasedReleasePercent` | 否 | String(10) | 大于 `0.00` 且小于 `100.00`，两位小数，不含 `%` |
| `phasedReleaseDescription` | 否 | String(500) | 分阶段发布说明 |

如果 `releaseType=1` 改为全网发布，Body 传空对象 `{}`。

## 16. 更新版本上架时间

接口：

```text
PUT /api/publish/v2/on-shelf-time
```

Query：

| 参数 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `appId` | 是 | String | 应用 ID |

Body：

| 参数 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `changeType` | 是 | Integer | `2` 指定时间上架改为审核通过立即上架；`3` 变更定时上架时间 |
| `releaseTime` | 是 | String | 指定上架时间，UTC 格式 `yyyy-MM-ddTHH:mm:ssZZ`；`changeType=3` 时有效 |
| `releaseType` | 是 | Integer | 发布方式，目前只支持 `1` 全网 |

## 17. 设置 GMS 依赖属性

接口：

```text
PUT /api/publish/v2/properties/gms
```

Query：

| 参数 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `appId` | 是 | String | 应用 ID |

Body：

| 参数 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `needGms` | 是 | Integer | 是否依赖 GMS：`0` 不依赖，`1` 依赖 |

示例：

```json
{
  "needGms": 0
}
```

## 18. 回调结果通知

`app-submit-with-file` 和 `app-package-file/by-url` 都是异步接口。传入 `callbackAddr` 后，华为服务器会向该地址发送处理结果。

回调接口由开发者实现：

```text
POST ${callbackAddr}
```

回调 Body：

| 参数 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `callBackData` | 是 | String | 发布结果 JSON 字符串 |
| `signatureRSAWithPSS` | 是 | String | 华为服务器对 `callBackData` 使用 `SHA256WithRSA/PSS` 的签名 |

`callBackData` 格式：

```json
{
  "requestId": "xxx",
  "retCode": 0,
  "desc": "success",
  "pkgVersion": "xxx",
  "downloadFileName": "app-release.apk"
}
```

字段说明：

| 字段 | 说明 |
| --- | --- |
| `requestId` | 提交下载或发布时传入的请求 ID |
| `retCode` | `0` 成功，其他值失败 |
| `desc` | 返回码描述 |
| `pkgVersion` | 软件包版本信息 |
| `downloadFileName` | 软件包名称 |

必须用华为验签公钥校验 `signatureRSAWithPSS`。未收到回调时，官方建议定期查询应用信息，判断应用是否已提交审核成功。

## 19. 响应与错误码

多数接口响应包含：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `ret` | String/Object | JSON 字符串或对象，包含 `code` 和 `msg` |

常见错误码：

| 错误码 | 说明 |
| --- | --- |
| `0` | 成功 |
| `204144641` | 无效入参 |
| `204144642` | 系统未知错误 |
| `204144643` | 获取开发者账号信息失败 |
| `204144644` | 查询应用信息失败 |
| `204144645` | 获取上传 authCode 失败 |
| `204144646` | 添加 APK/RPK 失败 |
| `204144647` | 更新服务失败 |
| `204144648` | 查询应用服务信息失败 |
| `204144649` | 更新应用信息失败 |
| `204144650` | 更新语种信息失败，输入语种不存在 |

完整错误码以官方错误码页面为准：

```text
https://developer.huawei.com/consumer/cn/doc/AppGallery-connect-References/agcapi-publishingapi-errorcode-0000001163523297
```

## 20. 最小可用调用序列

只走 URL 传包并提审：

```text
GET  /api/publish/v2/appid-list?packageName=${packageName}
POST /api/publish/v2/app-submit-with-file?appId=${appId}
```

已经通过文件上传链路拿到 `objectId` 后提审：

```text
GET  /api/publish/v2/appid-list?packageName=${packageName}
PUT  /api/publish/v2/app-file-info?appId=${appId}
POST /api/publish/v2/app-submit?appId=${appId}
```

带资料更新的完整链路：

```text
GET    /api/publish/v2/appid-list?packageName=${packageName}
GET    /api/publish/v2/app-info?appId=${appId}
PUT    /api/publish/v2/app-info?appId=${appId}
PUT    /api/publish/v2/app-language-info?appId=${appId}
POST   /api/publish/v2/app-submit-with-file?appId=${appId}
POST   ${callbackAddr}
GET    /api/publish/v2/package/compile/status?appId=${appId}&pkgIds=${pkgVersion}
```

实际实现时优先保留 `requestId`、请求体、响应体和回调体日志。异步接口不这么做，排查失败基本就是猜。
