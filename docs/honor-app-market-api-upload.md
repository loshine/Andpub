# 荣耀应用市场 API 传包接入文档

来源：https://developer.honor.com/en/doc/guides/101359  
原文标题：API传包服务指引  
整理时间：2026-05-15

说明：该英文链接实际返回的是中文正文，以下按官方接口内容整理。

## 1. 能力范围

荣耀 Publish-API 用于通过接口维护荣耀开发者服务平台上的安卓应用，包括：

- 获取账号级 `Access Token`。
- 根据包名查询 `APPID`。
- 查询应用详情。
- 获取文件上传地址。
- 上传 APK、图标、截图、资质等资源文件。
- 通过公网 URL 让荣耀后台拉取文件。
- 更新应用基础信息、多语言信息、文件绑定信息。
- 提交审核。
- 查询审核状态和最新版本状态。

接口域名：

```text
https://appmarket-openapi-drcn.cloud.honor.com
```

鉴权域名：

```text
https://iam.developer.honor.com
```

## 2. 接入前准备

1. 已注册荣耀开发者服务平台账号。
2. 已在平台创建安卓应用，并在应用管理中绑定包名。
3. 在 `管理中心` -> `开放能力` -> `凭证` 下申请 API 密钥。
4. 保存好 `client_id` 和 `client_secret`。
5. 已确认当前账号拥有目标应用权限。

## 3. 推荐传包流程

更新已有应用 APK：

1. 调用 `POST /auth/token` 获取 `access_token`。
2. 调用 `GET /openapi/v1/publish/get-app-id`，用包名查 `appId`。
3. 可选：调用 `GET /openapi/v1/publish/get-app-detail`，取当前应用资料。
4. 计算 APK 文件大小和 SHA-256。
5. 调用 `POST /openapi/v1/publish/get-file-upload-url`，获取 APK 的 `uploadUrl` 和 `objectId`，其中 APK 的 `fileType=100`。
6. 调用 `POST /openapi/v1/publish/file-upload`，用 multipart 字段 `file` 上传 APK。
7. 调用 `POST /openapi/v1/publish/update-file-info`，把 APK 的 `objectId` 绑定到应用。
8. 如需要，调用 `update-app-info` 和 `update-language-info` 更新资料。
9. 调用 `POST /openapi/v1/publish/submit-audit` 提交审核。
10. 调用 `POST /openapi/v1/publish/get-audit-result` 查询审核状态。

大文件或已有公网下载地址时，可以用 `upload-by-url` 替代第 5、6 步。它会让荣耀后台从公网 URL 拉取文件。

## 4. 获取 Access Token

接口：

```text
POST https://iam.developer.honor.com/auth/token
```

请求类型：

```text
Content-Type: application/x-www-form-urlencoded
```

Body：

| 参数 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `grant_type` | 是 | String | 固定值：`client_credentials` |
| `client_id` | 是 | String | API 密钥中申请的 `Client_id` |
| `client_secret` | 是 | String | API 密钥中申请的密钥 |

响应：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `access_token` | String | 账号级 Access Token |
| `expires_in` | Integer | 剩余有效期，单位秒 |
| `token_type` | String | 固定值：`Bearer` |

示例：

```http
POST https://iam.developer.honor.com/auth/token HTTP/1.1
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials&client_id=9f***39e&client_secret=Ot***wpJ
```

后续 Publish-API 请求统一带：

```text
Authorization: Bearer ${access_token}
```

## 5. 根据包名查询 APPID

接口：

```text
GET /openapi/v1/publish/get-app-id?pkgName=${packageName}
```

完整地址：

```text
https://appmarket-openapi-drcn.cloud.honor.com/openapi/v1/publish/get-app-id?pkgName=${packageName}
```

约束：

- 目标应用必须已创建并绑定包名。
- 只能查询当前账号下的应用。
- `pkgName` 支持多个包名，用英文逗号分隔，最多 10 个。

响应：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `code` | Integer | `0` 成功 |
| `msg` | String | 错误原因 |
| `data` | List<AppIdInfo> | 应用列表，未查询到或无权限的不返回 |

`AppIdInfo`：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `appId` | Integer | APPID |
| `packageName` | String | 包名 |

示例：

```http
GET https://appmarket-openapi-drcn.cloud.honor.com/openapi/v1/publish/get-app-id?pkgName=com.example.app HTTP/1.1
Authorization: Bearer ${access_token}
```

## 6. 查询应用详情

接口：

```text
GET /openapi/v1/publish/get-app-detail?appId=${APPID}
```

用途：获取应用名、描述、版权资质、资源图片、APK 资源等当前全量信息。更新前先查一遍，别盲写。

响应 `data` 为 `PubAppInfo`：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `basicInfo` | PubBasicInfo | 应用基础信息 |
| `languageInfo` | List<PubLanguageInfo> | 多语言信息 |
| `publishInfo` | PubPublishInfo | 上次提交的发布信息 |
| `fileInfo` | List<PubFileInfo> | 已绑定文件列表 |
| `releaseInfo` | PubReleaseInfo | 已上架版本信息 |

关键字段：

| 对象 | 字段 | 说明 |
| --- | --- | --- |
| `basicInfo` | `packageName` | 包名 |
| `basicInfo` | `appId` | APPID |
| `basicInfo` | `appClassification` | 应用分类，见应用分类表 |
| `basicInfo` | `defaultLanguage` | 默认语言，如 `zh-CN` |
| `basicInfo` | `releaseCountry` | 发布国家和地区，多值用 `|` 分隔 |
| `basicInfo` | `paymentInfo` | 是否联运：`1` 非联运，`2` 联运 |
| `basicInfo` | `ratingId` | 年龄分级：`3`、`8`、`12`、`16`、`18` |
| `languageInfo` | `appName` | 应用名称 |
| `languageInfo` | `intro` | 应用介绍 |
| `languageInfo` | `newFeature` | 新版本特性 |
| `fileInfo` | `fileType` | 文件类型 |
| `fileInfo` | `fileSha256` | API 上传文件的 SHA-256 |
| `releaseInfo` | `versionName` | 已上架版本名 |
| `releaseInfo` | `versionCode` | 已上架版本号 |

## 7. 获取文件上传路径

接口：

```text
POST /openapi/v1/publish/get-file-upload-url?appId=${APPID}
```

用途：给资源文件申请上传地址。资源包括 APK、图标、截图、资质文件等。

请求类型：

```text
Content-Type: application/json
```

Body 为 `List<UploadFile>`，一次最多 20 个：

| 参数 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `fileName` | 是 | String | 文件名，用于 ID 生成和后缀校验；单次请求不允许重名 |
| `fileType` | 是 | Integer | 文件类型，APK 为 `100` |
| `fileSize` | 是 | Long | 文件大小，单位 byte |
| `fileSha256` | 是 | String | 文件 SHA-256，用于完整性校验 |

响应 `data` 为 `List<FileUploadPath>`：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `fileName` | String | 请求中的文件名 |
| `uploadUrl` | String | 文件上传链接 |
| `objectId` | Long | 文件对象 ID；后续上传和绑定都要用 |
| `expireTime` | Long | 上传链接过期 UTC 时间，单位秒 |

示例：

```json
[
  {
    "fileName": "release.apk",
    "fileType": 100,
    "fileSize": 4000000,
    "fileSha256": "1f380ac3ab334440d3d25a9e06c239e7201ec5812065535b1474a8e61b7958bf"
  }
]
```

## 8. 上传应用文件

接口：

```text
POST /openapi/v1/publish/file-upload?appId=${APPID}&objectId=${objectId}
```

请求类型：

```text
Content-Type: multipart/form-data
```

Body：

| multipart 字段 | 必选 | 说明 |
| --- | --- | --- |
| `file` | 是 | 上传文件流，字段名固定为 `file` |

响应：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `code` | Integer | `0` 上传成功 |
| `msg` | String | 错误原因 |

示例：

```http
POST https://appmarket-openapi-drcn.cloud.honor.com/openapi/v1/publish/file-upload?appId=123456&objectId=1234567890 HTTP/1.1
Authorization: Bearer ${access_token}
Content-Type: multipart/form-data; boundary=----boundary

------boundary
Content-Disposition: form-data; name="file"; filename="app-release.apk"
Content-Type: application/vnd.android.package-archive

[file content]
------boundary--
```

上传成功只是暂存云端，必须再调用 `update-file-info` 绑定文件，并提交审核；审核通过后才会体现在荣耀应用市场。

## 9. 通过 URL 上传文件

接口：

```text
POST /openapi/v1/publish/upload-by-url?appId=${APPID}
```

用途：替代“获取上传地址 + multipart 上传”。适合大文件或已有公网文件地址的场景，由荣耀后台下载文件。

约束：

- 文件 URL 必须是 HTTPS。
- 无鉴权。
- 可直接通过 GET 获取文件流。
- 查询上传状态频率低于 3 分钟一次，否则可能被限流。

Body：

| 参数 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `type` | 是 | Integer | `1` 创建上传任务，`2` 查询上传结果 |
| `uploadList` | 条件必选 | List<UploadFile> | `type=1` 时必传，一次最多 20 个 |
| `objectList` | 条件必选 | List<ObjectFile> | `type=2` 时必传 |

`UploadFile`：

| 参数 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `fileName` | 是 | String | 文件名 |
| `fileType` | 是 | Integer | 文件类型，APK 为 `100` |
| `fileSize` | 是 | Long | 文件大小，单位 byte |
| `fileSha256` | 是 | String | 文件 SHA-256 |
| `fileUploadUrl` | 是 | String | 文件公网下载 URL，小于 1024 字符 |

响应 `data` 中的文件状态：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `objectId` | Long | 文件对象 ID |
| `fileName` | String | 文件名 |
| `status` | Integer | `0` 上传成功，`1` 待上传，`2` 上传失败 |
| `message` | String | 失败原因 |

## 10. 更新应用文件信息

接口：

```text
POST /openapi/v1/publish/update-file-info?appId=${APPID}
```

用途：把已上传文件绑定到应用。传包的关键动作在这里：APK 上传完不绑定，等于没给应用更新 APK。

Body：

| 参数 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `bindingFileList` | 是 | List<BindingFile> | 要绑定的文件列表 |

`BindingFile`：

| 参数 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `objectId` | 是 | Long | 文件对象 ID，来自上传路径或 URL 上传接口 |
| `languageId` | 否 | String | 语种 ID；部分文件类型必须绑定语种 |
| `order` | 否 | Integer | 同类多文件顺序，从 `0` 开始 |

更新场景只需要绑定本次更新的资源文件，未更新资源会继承上一版本。首次提交发布前需要绑定所有必需文件。

绑定 APK 示例：

```json
{
  "bindingFileList": [
    {
      "objectId": 1254833546
    }
  ]
}
```

绑定截图示例：

```json
{
  "bindingFileList": [
    { "objectId": 12345678, "languageId": "zh-CN", "order": 0 },
    { "objectId": 22345868, "languageId": "zh-CN", "order": 1 },
    { "objectId": 22345869, "languageId": "zh-CN", "order": 2 }
  ]
}
```

## 11. 更新应用基础信息

接口：

```text
POST /openapi/v1/publish/update-app-info?appId=${APPID}
```

常用字段：

| 参数 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `appClassification` | 是 | String | 三级应用分类 ID |
| `gameType` | 游戏必填 | Integer | `1` 休闲游戏，`2` 网络游戏 |
| `supplyName` | 是 | String | 供应商名称 |
| `defaultLanguage` | 是 | String | 默认语言，如 `zh-CN` |
| `releaseCountry` | 是 | String | 发布国家和地区，多值用 `|` 分隔，如 `CN|JP|DE` |
| `paymentInfo` | 是 | Integer | `1` 非联运，`2` 联运 |
| `inAppPayment` | 条件必填 | String | 应用内资费，多值用 `|` 分隔 |
| `ratingId` | 是 | Integer | 年龄分级：`3`、`8`、`12`、`16`、`18` |
| `privacyPolicyUrl` | 是 | String | 隐私政策 URL，`http://` 或 `https://` |
| `publicationNumber` | 条件必填 | String | 中国大陆游戏必填版号 |
| `appRegistrationEntityStatus` | 条件必填 | Integer | 中国大陆分发必填：`1` 主体一致，`2` 主体不一致，`3` 单机应用不需要备案 |
| `unifiedSocialCreditId` | 条件必填 | String | 备案主体状态为 `2` 时必填 |
| `appRegistrationEntityName` | 条件必填 | String | 备案主体状态为 `2` 时必填 |
| `appRegistrationNumber` | 否 | String | APP 备案号 |

## 12. 更新应用多语言信息

接口：

```text
POST /openapi/v1/publish/update-language-info?appId=${APPID}
```

Body：

| 参数 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `languageInfoList` | 是 | List<PubLanguageInfo> | 多语言信息列表 |
| `setAll` | 否 | Integer | 默认 `1`；`1` 更新所有语言，不在列表中的语言会删除；`0` 只更新列表中的语种 |

`PubLanguageInfo`：

| 参数 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `languageId` | 是 | String | 语言 ID，如 `zh-CN` |
| `appName` | 是 | String | 应用名称，15 个以内汉字或 30 个其他字符以内 |
| `intro` | 是 | String | 应用介绍，8000 字符以内 |
| `briefIntro` | 否 | String | 一句话介绍，80 字符以内 |
| `newFeature` | 否 | String | 新版本特性，500 字符以内 |

注意：每个语种都需要对应的应用文件信息。图标、截图、视频等语种相关文件要在 `update-file-info` 中带 `languageId`。

## 13. 提交审核

接口：

```text
POST /openapi/v1/publish/submit-audit?appId=${APPID}
```

约束：

- 更新发布场景下，至少更新过基础信息、多语言信息、文件信息之一，才可以提交。
- 首次发布场景下，需要更新全部所需信息。
- 存在审核中、待发布、待分阶段发布、分阶段发布中且未全网发布的版本时，不可再次提交。

Body：

| 参数 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `forceUpdate` | 否 | Integer | `0` 非强制更新，`1` 强制更新 |
| `testAccount` | 否 | String | 审核测试账号 |
| `testPassword` | 否 | String | 审核测试密码 |
| `testComment` | 否 | String | 审核备注，500 字以内 |
| `releaseType` | 是 | Integer | `1` 全网发布，`2` 指定时间发布，`3` 分阶段发布 |
| `releaseTime` | 条件必填 | String | 指定时间发布时必填，格式 `yyyy-MM-dd'T'HH:mm:ssZZ` |
| `phasedReleaseInfo` | 条件必填 | object | 分阶段发布时必填 |

响应：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `code` | Integer | `0` 成功 |
| `msg` | String | 错误原因 |
| `data` | String | 发布流程 ID，即 `releaseId` |

示例：

```json
{
  "forceUpdate": 0,
  "testAccount": null,
  "testPassword": null,
  "testComment": null,
  "releaseType": 1
}
```

## 14. 查询审核状态

接口：

```text
POST /openapi/v1/publish/get-audit-result
```

用途：根据 `appId` 和 `releaseId` 查询审核状态。官方建议轮询频率为 3 小时一次，太频繁可能被限流。

Body：

| 参数 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `appId` | 是 | List<AppReleaseQueryInfo> | 要查询的审核申请列表，单次最多 20 个 |

`AppReleaseQueryInfo`：

| 参数 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `appId` | 是 | Integer | 应用 APPID |
| `releaseId` | 是 | String | `submit-audit` 返回的发布流程 ID |

响应 `PubAuditResult`：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `appId` | Integer | APPID |
| `releaseId` | String | 发布流程 ID |
| `auditResult` | Integer | `0` 审核中，`1` 审核通过，`2` 审核不通过，`3` 未提交审核或其他非审核状态 |
| `auditMessage` | String | 审核意见 |
| `auditAttachment` | List<String> | 审核意见附件 URL |

示例：

```json
{
  "appId": [
    {
      "appId": 123456,
      "releaseId": "******"
    }
  ]
}
```

## 15. 查询最新版本与审核状态

接口：

```text
GET /openapi/v1/publish/get-app-current-release?appId=${APPID}
```

响应里的 `auditResult`：

| 值 | 说明 |
| --- | --- |
| `0` | 审核中 |
| `1` | 审核通过 |
| `2` | 审核不通过 |
| `3` | 其他非审核状态 |
| `4` | 编辑中，未提交审核 |

## 16. 文件类型

传 APK 和常见资源时重点看这些 `fileType`：

| fileType | 说明 | 是否绑定语言 | 尺寸 | 大小 | 数量 | 格式 | 是否必选 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `1` | 应用图标 | 是 | 512x512 | 200KB | 1 | PNG/JPG/JPEG | 是 |
| `2` | 应用介绍截图-横向 | 是 | 1920x1080 | 5MB | 3-5 | PNG/JPG/JPEG | 横纵向二选一 |
| `3` | 应用介绍截图-纵向 | 是 | 1080x1920 | 5MB | 3-5 | PNG/JPG/JPEG | 横纵向二选一 |
| `10` | 应用介绍视频-横向 | 是 | 建议 1280x720，16:9，15 秒到 2 分钟 | 500MB | 1 | MOV/MP4 | 否，需与截图方向一致 |
| `11` | 应用介绍视频-纵向 | 是 | 建议 720x1280，9:16，15 秒到 2 分钟 | 500MB | 1 | MOV/MP4 | 否，需与截图方向一致 |
| `13` | 软件著作权登记证书 | 否 | N/A | 15MB | 1 | JPEG/JPG/PNG/BMP/PDF | 中国大陆分发必填 |
| `15` | ICP 许可证 | 否 | N/A | 15MB | 1 | JPEG/JPG/PNG/BMP/PDF | 中国大陆应用必填 |
| `21` | 版号批文 | 否 | N/A | 4MB | 1 | JPEG/JPG/PNG/BMP/PDF | 中国大陆游戏至少有一个必填 |
| `35` | 其他特殊资质 | 否 | N/A | 15MB | 0-4 | JPEG/JPG/PNG/BMP/PDF | 否 |
| `36` | 其他资质 ZIP 包 | 否 | N/A | 100MB | 1 | ZIP | 否 |
| `37` | 备案主体营业执照 | 否 | N/A | 15MB | 1 | JPEG/JPG/PNG/BMP/PDF | 中国大陆且备案主体状态为 `2` 时必填 |
| `38` | 备案说明协议 | 否 | N/A | 15MB | 1 | JPEG/JPG/PNG/BMP/PDF | 中国大陆且备案主体状态为 `2` 时必填 |
| `39` | 单机应用免责声明 | 否 | N/A | 15MB | 1 | JPEG/JPG/PNG/BMP/PDF | 中国大陆且备案主体状态为 `3` 时必填 |
| `100` | APK 应用包 | 是 | N/A | 4GB | 1 | APK | 是 |

APK 约束：

- APK 包名必须和应用绑定包名一致。
- APK 版本必须大于等于当前已上架版本。
- `fileSha256` 必须和上传文件内容一致。

## 17. 常见错误码

| 错误码 | 说明 |
| --- | --- |
| `10001` | 未传递 `access_token` |
| `10002` | `access_token` 非合法格式 |
| `10003` | `access_token` 已过期 |
| `10004` | 横向越权，无请求资源的操作权限 |
| `10005` | 纵向越权，无权限访问当前资源 |
| `20001` | 包名为空 |
| `20002` | APPID 为空 |
| `20003` | 包名格式不正确 |
| `20004` | APPID 格式不正确 |
| `20005` | 请求的 APPID 不存在 |
| `20022` | 应用正在审核中，不允许提交 |
| `20023` | 应用未上架过，不允许提交 |
| `20028` | `objectId` 为空 |
| `20029` | `objectId` 格式不正确 |
| `20030` | `objectId` 不存在 |
| `20031` | `languageId` 为空 |
| `20078` | 指定的媒体资源横纵向互相冲突 |
| `30001` | APPID 不存在 |
| `30002` | 包名不存在 |
| `30003` | 应用包名和 APK 中解析的包名不一致 |
| `30004` | 应用包下载失败 |
| `30005` | 应用包无法解析 |
| `30006` | 应用包版本低于之前上架的版本 |
| `30007` | 应用包名和之前版本不一致 |
| `30009` | 应用包 MD5 校验不一致 |
| `30010` | 文件上传失败 |
| `30011` | 版本提交过于频繁，请稍后再试 |
| `30017` | 应用不存在指定的语言信息 |
| `40000` | 系统服务异常 |

原文还列出 `31000` 到 `31005` 的签名错误码，但当前 Publish-API 主流程使用 Bearer Token 鉴权；若遇到这类错误，优先检查是否调用了其他需要签名的能力或网关侧鉴权策略。

## 18. 实现检查清单

- `access_token` 要缓存，但必须按 `expires_in` 过期刷新。
- 所有 Publish-API 请求带 `Authorization: Bearer ${access_token}`。
- 先用包名查 `appId`，不要硬编码从页面上抄来的值。
- 上传前计算文件 `SHA-256`，不是 MD5。
- APK 的 `fileType` 固定用 `100`。
- multipart 上传文件字段名固定为 `file`。
- 上传成功后必须调用 `update-file-info` 绑定 `objectId`。
- 更新 APK 后必须 `submit-audit`，否则不会进入市场审核流程。
- 审核状态轮询频率按官方建议控制在 3 小时一次。
