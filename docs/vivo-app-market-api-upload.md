# vivo 应用市场 API 发包接入文档

来源：

- https://dev.vivo.com.cn/documentCenter/doc/326
- https://dev.vivo.com.cn/documentCenter/doc/327
- https://dev.vivo.com.cn/documentCenter/doc/330
- https://dev.vivo.com.cn/documentCenter/doc/344
- https://dev.vivo.com.cn/documentCenter/doc/690
- https://dev.vivo.com.cn/documentCenter/doc/329
- https://dev.vivo.com.cn/documentCenter/doc/331
- https://dev.vivo.com.cn/documentCenter/doc/332
- https://dev.vivo.com.cn/documentCenter/doc/513
- https://dev.vivo.com.cn/documentCenter/doc/514
- https://dev.vivo.com.cn/documentCenter/doc/341
- https://dev.vivo.com.cn/documentCenter/doc/487
- https://dev.vivo.com.cn/documentCenter/doc/488
- https://dev.vivo.com.cn/documentCenter/doc/340
- https://dev.vivo.com.cn/documentCenter/doc/434
- https://dev.vivo.com.cn/documentCenter/doc/710
- https://dev.vivo.com.cn/documentCenter/doc/711
- https://dev.vivo.com.cn/documentCenter/doc/792
- https://dev.vivo.com.cn/documentCenter/doc/342
- https://dev.vivo.com.cn/documentCenter/doc/515
- https://dev.vivo.com.cn/documentCenter/doc/343
- https://dev.vivo.com.cn/documentCenter/doc/517
- https://dev.vivo.com.cn/documentCenter/doc/900
- https://dev.vivo.com.cn/documentCenter/doc/355
- https://dev.vivo.com.cn/documentCenter/doc/518
- https://dev.vivo.com.cn/documentCenter/doc/356
- https://dev.vivo.com.cn/documentCenter/doc/519
- https://dev.vivo.com.cn/documentCenter/doc/357
- https://dev.vivo.com.cn/documentCenter/doc/358
- https://dev.vivo.com.cn/documentCenter/doc/883
- https://dev.vivo.com.cn/documentCenter/doc/651
- https://dev.vivo.com.cn/documentCenter/doc/648
- https://dev.vivo.com.cn/documentCenter/doc/649
- https://dev.vivo.com.cn/documentCenter/doc/650
- https://dev.vivo.com.cn/documentCenter/doc/1007
- https://dev.vivo.com.cn/documentCenter/doc/718

整理时间：2026-05-15

## 1. 能力范围

vivo API 传包能力用于把开发者内部发布系统和 vivo 应用市场打通，通过接口完成应用创建、应用更新、文件上传、URL 下载传包、异步任务查询、分包、分阶段发布和回调通知。

vivo 提供两种传包方式：

- 文件直传：先把 APK、图标、截图、资质等文件上传到 vivo 开放平台，拿到 `serialnumber`，再调用同步创建或同步更新接口。
- 下载文件：把 APK、图标、截图、资质等文件放在开发者自己的公网文件服务器，接口里传文件 URL，vivo 后台异步下载处理，再查询任务状态。

开通入口：

1. 登录 vivo 开放平台。
2. 进入 `管理中心` -> `账号管理` -> `api管理`。
3. 点击开通 API 传包服务。
4. 开通后获取 `access_key` 和 `access_secret`。

账号开通一次后，该账号下应用都具备接口调用权限。

## 2. 调用入口

API 传包服务网关：

| 环境 | 地址 | 说明 |
| --- | --- | --- |
| 测试环境 | `https://sandbox-developer-api.vivo.com.cn/router/rest` | 每个接口 100 次/天；测试环境和正式环境数据隔离 |
| 正式环境 | `https://developer-api.vivo.com.cn/router/rest` | 应用上线后，传包相关接口限制通常为每个接口 50 次/天 |

无特殊说明时，请求统一走同一个网关，通过公共参数 `method` 区分具体能力。

普通接口可用：

```text
POST application/x-www-form-urlencoded
```

文件上传接口使用：

```text
POST multipart/form-data; charset=utf-8
```

所有请求和响应编码均为 `UTF-8`。URL 中参数名和参数值需要 URL 编码；`application/x-www-form-urlencoded` 的请求体参数值也需要 URL 编码；`multipart/form-data` 的表单字段值不需要 URL 编码。

## 3. 公共参数和签名

调用任何 API 都必须带公共参数：

| 参数 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `method` | 是 | String | API 接口名称，例如 `app.upload.apk.app` |
| `access_key` | 是 | String | vivo 开放平台分配的接入 key |
| `timestamp` | 是 | String/Long | 毫秒时间戳，例如 `1567945333425`；允许最大时间误差 20 分钟 |
| `format` | 是 | String | 响应格式，默认 `json` |
| `v` | 是 | String | API 协议版本，固定 `1.0` |
| `sign_method` | 是 | String | 签名算法，使用 `HMAC-SHA256` |
| `sign` | 是 | String | 签名结果 |
| `target_app_key` | 是 | String | 接口传包固定传 `developer` |

签名规则：

1. 取公共参数和业务参数，不包含 `sign`。
2. 文件上传接口计算签名时排除 `file` 参数。
3. 参数名按 ASCII 升序排序。
4. 按排序结果拼接为 `key1=value1&key2=value2`。
5. 使用 `access_secret` 作为 HMAC key，对拼接字符串做 `HmacSHA256`。
6. 将加密结果转为 16 进制字符串，作为 `sign`。

伪代码：

```text
params = public_params + business_params - sign
if multipart upload:
  params = params - file

plain = ascii_sort(params).map(k => k + "=" + params[k]).join("&")
sign = hex(HMAC_SHA256(access_secret, plain))
```

## 4. 推荐发包流程

### 4.1 文件直传方式

适合内部系统直接把本地文件推给 vivo：

1. 调用 APK 上传接口，应用单包用 `app.upload.apk.app`，32/64 位分包用 `app.upload.apk.app.32` 和 `app.upload.apk.app.64`。
2. 调用图标、截图、资质、隐私自检、备案授权函等上传接口，拿到每个文件的 `serialnumber`。
3. 创建应用调用 `app.sync.create.app`，更新应用调用 `app.sync.update.app`。
4. 32/64 位分包创建调用 `app.sync.create.subpackage.app`，分包更新调用 `app.sync.update.subpackage.app`。
5. 接口返回 `code=0` 且业务 `subCode=0` 视为成功。

### 4.2 下载文件方式

适合文件已经在开发者 CDN 或制品仓库里：

1. 准备公网可访问的 APK、图标、截图、资质等下载地址。
2. 创建应用调用 `app.create.app`，更新应用调用 `app.update.app`。
3. 32/64 位分包创建调用 `app.create.subpackage.app`，分包更新调用 `app.update.subpackage.app`。
4. 用 `app.query.task.status` 查询异步任务状态。
5. 状态为 `3` 表示处理成功，`4` 表示处理失败，失败原因看 `errorReason`。

下载文件方式是异步处理，不要把网关成功响应当成最终上架成功。

## 5. 文件上传接口

文件上传成功后，响应 `data.serialnumber` 是后续创建或更新接口要使用的流水号。

通用成功响应结构：

```json
{
  "code": 0,
  "subCode": "0",
  "msg": "成功",
  "data": {
    "packageName": "com.example.app",
    "serialnumber": "210c5cbc67fdded24245eec7ff5d605c"
  }
}
```

常用上传接口：

| 能力 | `method` | 主要业务参数 | 文件要求 |
| --- | --- | --- | --- |
| 上传应用 APK | `app.upload.apk.app` | `packageName`, `file`, `fileMd5`; 分阶段包传 `stageType=1` | APK 不超过 3G |
| 上传 32 位 APK | `app.upload.apk.app.32` | `packageName`, `file`, `fileMd5`; 分阶段包传 `stageType=1` | 32 位 APK |
| 上传 64 位 APK | `app.upload.apk.app.64` | `packageName`, `file`, `fileMd5`; 分阶段包传 `stageType=1` | 64 位 APK |
| 上传图标 | `app.upload.icon` | `packageName`, `file` | jpg/png，正方形，256x256 到 512x512，50KB 内，仅直角图标 |
| 上传截图 | `app.upload.screenshot` | `packageName`, `file` | 竖图 1080x1920，jpg/png，单张不超过 2MB；多张截图分别上传，创建/更新时用逗号拼流水号 |
| 上传特殊资质 | `app.upload.qualification` | `packageName`, `file` | jpg/png，小于 2MB |
| 上传电子版权证书 | `app.upload.ecopyright` | `packageName`, `file` | 电子版权材料 |
| 上传版权证明 | `app.upload.copyright` | `packageName`, `file` | 版权证明材料 |
| 上传安全报告 | `app.upload.safety.report` | `packageName`, `file` | PDF，不超过 10MB |
| 上传隐私权限自检文件 | `app.upload.private.self.check` | `packageName`, `file` | 隐私自检 PDF；创建/更新接口也支持直接传 HTTP/HTTPS 地址 |
| 上传承诺函 | `app.upload.commitment.letter` | `packageName`, `file` | jpg/png/jpeg，小于 2MB |
| 上传视频 | `app.upload.video` | `packageName`, `file` | 应用或游戏视频 |
| 上传视频封面 | `app.upload.video.cover` | `packageName`, `file` | 竖版 554x984 或横版 984x554，png/jpg，100KB 内 |
| 上传 APP 核准备案授权函 | `app.upload.icp.auth.letter` | `packageName`, `file` | jpg/png/jpeg，小于 2MB |
| 通用文件上传 | `app.upload.file` | `packageName`, `file`, `fileType` | `fileType=26` 表示版权证明授权书，jpg/jpeg/png，小于 5MB |

APK 上传响应会额外返回：

| 字段 | 说明 |
| --- | --- |
| `data.versionCode` | APK 版本号 |
| `data.versionName` | APK 版本名称 |
| `data.fileMd5` | 文件 MD5 |

文件上传常见业务码：

| 业务码 | 说明 |
| --- | --- |
| `12010` | 当前更新应用正在审核，不允许更新 |
| `12022` | 当前更新应用待上架，不允许更新 |
| `13001` | 上传的文件不存在，上传失败 |
| `13002` | 包名不属于当前开发者，或其它开发者已上传过该应用 |
| `13003` | 文件异常，上传失败 |
| `13004` | 文件上传服务异常 |
| `15001` | APK 包名与当前包名不一致 |
| `15002` | `targetSdkVersion` 低于之前版本 |
| `15003` | APK 版本号低于之前上传版本 |
| `15005` | APK 包解析失败 |
| `15009` | APK MD5 与请求参数不一致 |
| `20008` | 必填参数不能为空 |

## 6. 文件直传创建和更新

### 6.1 创建应用

接口：

```text
method=app.sync.create.app
```

用途：使用已上传文件的 `serialnumber` 同步创建应用。

核心参数：

| 参数 | 必选 | 说明 |
| --- | --- | --- |
| `packageName` | 是 | 应用包名；创建后不可修改，必须与 APK 包名一致 |
| `apk` | 是 | `app.upload.apk.app` 返回的 APK 流水号 |
| `fileMd5` | 是 | APK 文件 MD5 |
| `onlineType` | 是 | 上架类型：`1` 实时上架，`2` 定时上架 |
| `updateDesc` | 是 | 新版说明，5 到 200 字符 |
| `detailDesc` | 是 | 应用简介，50 到 1000 字符 |
| `icon` | 是 | 图标上传流水号 |
| `screenshot` | 是 | 截图上传流水号，3 到 5 张，多个用英文逗号分隔，不可重复 |
| `scheOnlineTime` | 否 | 定时上架时间；`onlineType=2` 时必填，格式 `yyyy-MM-dd HH:mm:ss` |
| `appClassify` | 是 | 应用分类 ID |
| `subAppClassify` | 是 | 应用二级分类 ID |
| `mainTitle` | 是 | 主标题，不超过 20 字符 |
| `subTitle` | 否 | 副标题；主标题超过限制或以 `+` 结尾时按官方规则留空 |
| `compatibleDevice` | 是 | 兼容设备：`1` 手机，`2` 手机和平板，`3` 平板 |

可选材料参数：

| 参数 | 说明 |
| --- | --- |
| `remark` | 审核留言，10 到 200 字符 |
| `specialQualifications` | 特殊资质流水号，1 到 5 张，多个逗号分隔 |
| `ecopyright` | 电子版权证书流水号 |
| `copyrightList` | 版权证明流水号，最多 5 张，多个逗号分隔 |
| `copyrightAuthLetter` | 版权证明授权书流水号 |
| `safetyreport` | 安全报告流水号 |
| `networkCultureLicense` | 网络文化经营许可证号 |
| `privateSelfCheck` | 隐私权限自检文件流水号，或 HTTP/HTTPS 查看地址 |
| `customerService` | 对外联系方式，例如座机或邮箱 |
| `video` | 视频流水号 |
| `videoCover` | 视频封面流水号 |
| `simpleDesc` | 一句话简介 |
| `commitmentLetter` | 承诺函流水号 |
| `icpRecordType` | 核准备案类型：`0` 需要核准备案，`1` 不联网 |
| `icpLicenseType` | APP 核准备案证件类型：`0` 统一社会信用代码，`1` 组织机构代码，`2` 其他 |
| `icpLicenseNo` | APP 核准备案证件号 |
| `icpAuthLetter` | APP 核准备案授权函流水号 |

电子版权证书 `ecopyright` 和版权证明 `copyrightList` 二者不能同时为空，并且二者只传其一。

### 6.2 更新应用

接口：

```text
method=app.sync.update.app
```

用途：使用已上传文件的 `serialnumber` 同步更新应用。

核心参数：

| 参数 | 必选 | 说明 |
| --- | --- | --- |
| `packageName` | 是 | 必须与 APK 包名、vivo 平台应用包名一致 |
| `versionCode` | 是 | 应用版本号 |
| `apk` | 是 | APK 上传流水号 |
| `fileMd5` | 是 | APK 文件 MD5 |
| `onlineType` | 是 | 上架类型：`1` 实时上架，`2` 定时上架 |
| `compatibleDevice` | 是 | 兼容设备：`1` 手机，`2` 手机和平板，`3` 平板 |

其它应用资料字段与创建接口基本一致，但多数为可选字段。草稿状态应用更新时，`appClassify` 和 `subAppClassify` 仍可能必填。

### 6.3 32/64 位分包

分包文件直传先分别上传 32 位和 64 位 APK：

```text
method=app.upload.apk.app.32
method=app.upload.apk.app.64
```

再调用：

| 场景 | `method` | 关键参数 |
| --- | --- | --- |
| 分包创建 | `app.sync.create.subpackage.app` | `packageName`, `apk32`, `apk64`, 应用资料字段 |
| 分包更新 | `app.sync.update.subpackage.app` | `packageName`, `apk32`, `apk64`, 应用资料字段 |

`apk32` 和 `apk64` 填对应上传接口返回的流水号。

## 7. 下载文件方式创建和更新

下载文件方式由 vivo 后台异步拉取开发者提供的公网文件地址。普通应用创建和更新的字段与直传方式基本一一对应，只是文件字段从流水号变为 URL。

### 7.1 创建应用

接口：

```text
method=app.create.app
```

核心参数：

| 参数 | 必选 | 说明 |
| --- | --- | --- |
| `packageName` | 是 | 应用包名 |
| `apkUrl` | 是 | APK 下载地址 |
| `apkMd5` | 是 | APK 文件 MD5 |
| `versionCode` | 是 | 应用版本号 |
| `onlineType` | 是 | 上架类型：`1` 实时上架，`2` 定时上架 |
| `updateDesc` | 是 | 新版说明 |
| `detailDesc` | 是 | 应用简介 |
| `iconUrl` | 是 | 图标下载地址 |
| `screenshotUrl` | 是 | 截图下载地址，3 到 5 张，多张用英文逗号分隔 |
| `appClassify` | 是 | 应用分类 ID |
| `subAppClassify` | 是 | 应用二级分类 ID |
| `mainTitle` | 是 | 主标题 |
| `compatibleDevice` | 是 | 兼容设备 |

常用可选 URL 字段：

| 参数 | 说明 |
| --- | --- |
| `specialQualificationsUrl` | 特殊资质下载地址，1 到 5 张，多张逗号分隔 |
| `ecopyrightUrl` | 电子版权证书下载地址 |
| `copyrightUrl` | 版权证明下载地址 |
| `copyrightAuthLetterUrl` | 版权证明授权书下载地址 |
| `safetyreportUrl` | 安全报告下载地址 |
| `privateSelfCheckUrl` | 隐私权限自检文件下载地址 |
| `videoUrl` | 视频下载地址 |
| `videoCoverUrl` | 视频封面下载地址 |
| `commitmentLetterUrl` | 承诺函下载地址 |

### 7.2 更新应用

接口：

```text
method=app.update.app
```

核心参数：

| 参数 | 必选 | 说明 |
| --- | --- | --- |
| `packageName` | 是 | 应用包名 |
| `apkUrl` | 是 | APK 下载地址 |
| `apkMd5` | 是 | APK 文件 MD5 |
| `versionCode` | 是 | 应用版本号 |
| `onlineType` | 是 | 上架类型 |

其它资料字段与创建接口类似，多数为可选更新字段。

### 7.3 下载方式分包

| 场景 | `method` | 关键参数 |
| --- | --- | --- |
| 分包创建 | `app.create.subpackage.app` | `apkUrl32`, `apk32Md5`, `apkUrl64`, `apkMd5`, `versionCode` |
| 分包更新 | `app.update.subpackage.app` | `apkUrl64`, `apkMd5`, `apkUrl32`, `apk32Md5`, `versionCode` |

文档中 64 位包 MD5 字段名使用 `apkMd5`，32 位包 MD5 字段名使用 `apk32Md5`。

### 7.4 游戏更新

游戏下载方式更新接口：

```text
method=app.update.game
```

核心字段包括：

| 参数 | 必选 | 说明 |
| --- | --- | --- |
| `packageName` | 是 | 游戏包名 |
| `versionCode` | 是 | 版本号 |
| `apkUrl` | 是 | APK 下载地址 |
| `apkMd5` | 是 | APK MD5 |
| `onlineType` | 是 | 上架类型 |
| `updateDesc` | 是 | 新版说明 |
| `detailDesc` | 是 | 详情描述 |
| `iconUrl` | 是 | 图标地址 |
| `screenshotUrl` | 是 | 截图地址 |

其它游戏字段包括 `isbnNumber`、`videoUrl`、`testType`、`testStartTime`、`testEndTime`、`sellType`、`gameClassify`、`subAppClassify`、`isCalculateCost`、`calculateCostDescribe`、`gameEditionDepartment`、`privateSelfCheckUrl` 等，按游戏资质和测试类型填写。

## 8. 异步任务查询

下载文件方式创建或更新后，调用任务查询接口确认最终处理结果。

接口：

```text
method=app.query.task.status
```

请求参数：

| 参数 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `packageName` | 是 | String | 包名 |
| `packetType` | 是 | Integer | 传包类型：`0` 更新包，`1` 创建包 |

响应 `data`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `packageName` | String | 包名 |
| `packetType` | Integer | 传包类型 |
| `errorReason` | String | 错误原因 |
| `status` | Integer | 状态：`1` 待处理，`2` 正在处理中，`3` 处理成功，`4` 处理失败 |

常见业务码：

| 业务码 | 说明 |
| --- | --- |
| `11001` | 包名不正确，未查询到应用 |
| `20007` | 参数为空 |
| `20019` | 请求参数格式错误 |

## 9. 分阶段创建和更新

分阶段下载方式接口：

```text
method=app.create.update.stage.app
```

该接口为异步接口，用于分阶段创建或更新。

关键参数：

| 参数 | 必选 | 说明 |
| --- | --- | --- |
| `packageName` | 是 | 应用包名 |
| `subPackage` | 是 | 是否分包提交：`1` 是，`0` 否 |
| `stagedStartTime` | 是 | 分阶段开始时间，格式 `yyyy-MM-dd HH:mm:ss` |
| `stagedEndTime` | 是 | 分阶段结束时间，格式 `yyyy-MM-dd HH:mm:ss` |
| `stagedProportion` | 是 | 分阶段比例，`1` 到 `99` |
| `apkUrl` | 否 | 不分包场景 APK 下载地址 |
| `apkMd5` | 否 | 不分包 APK MD5，或 64 位 APK MD5 |
| `apk64Url` | 否 | 分包场景 64 位 APK 下载地址 |
| `apkUrl32` | 否 | 分包场景 32 位 APK 下载地址 |
| `apk32Md5` | 否 | 分包场景 32 位 APK MD5 |
| `versionCode` | 否 | 应用版本号；非分包场景下必传 |
| `distributeCompany` | 否 | 分发厂商，多个用英文逗号分隔，例如 `oppo,xiaomi,honor` |

响应成功时返回：

```json
{
  "code": 0,
  "subCode": "0",
  "msg": "成功"
}
```

## 10. 灰度包通知回调

当灰度包提交时，如果开发者设置了 `callbackUrl`，vivo 会在灰度包审核结束、上架、下架时向该地址发起回调。

回调请求：

```text
POST application/json
```

请求参数：

| 参数 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `packageName` | 是 | String | 应用包名 |
| `apkMd5` | 是 | String | APK 文件 MD5 |
| `versionCode` | 是 | Integer | 版本号 |
| `status` | 否 | Integer | 状态值 |
| `extra` | 否 | String | 扩展字段，JSON 字符串 |

开发者服务响应：

```json
"ok"
```

平台收到非 `ok` 结果会发起失败重试。

`status` 取值：

| 值 | 定义 |
| --- | --- |
| `-1` | 处理异常 |
| `0` | 测试审核中 |
| `1` | 测试或审核不通过 |
| `2` | 上架 |
| `3` | 下架 |

## 11. 字典和限制

常用字典：

| 字段 | 值 | 说明 |
| --- | --- | --- |
| `onlineType` | `1` | 实时上架 |
| `onlineType` | `2` | 定时上架，需传 `scheOnlineTime` |
| `compatibleDevice` | `1` | 手机 |
| `compatibleDevice` | `2` | 手机和平板 |
| `compatibleDevice` | `3` | 平板 |
| `sellType` | `2` | 道具付费 |
| `sellType` | `3` | 关卡付费 |
| `testType` | `1` | 计费删档测试 |
| `testType` | `2` | 不计费删档测试 |
| `testType` | `3` | 不删档测试（首测） |
| `testType` | `4` | 公测 |

应用分类 `appClassify` 和二级分类 `subAppClassify` 取值很多，接入时直接使用 vivo 文档中心“参数字典描述”页面维护的 ID。

## 12. 公共返回码

网关层常见返回码：

| 返回码 | 说明 |
| --- | --- |
| `0` | 成功 |
| `404` | 接口不存在 |
| `405` | 不允许的 HTTP 请求方法 |
| `440` | 缺少参数 |
| `441` | 请求参数错误 |
| `500` | 服务器错误 |
| `10001` | 签名校验失败 |
| `10002` | 业务请求参数不能为空 |
| `10003` | 请求查询异常，请稍后再试 |
| `10004` | 没有接口访问权限 |
| `10005` | `timestamp` 时间戳失效 |
| `10006` | 请求频次过高，请稍后再试 |
| `10007` | 账号验证失败，请重新登录 |
| `10008` | vivo 平台开发者账号非正常状态 |
| `10009` | vivo 开放平台联系人信息不全 |
| `10011` | 当天请求次数超过限制 |
| `10014` | API 版本号不正确 |
| `10015` | 签名验证方式不支持 |
| `10016` | 响应格式不支持 |
| `10017` | 第三方调用的 Key 错误 |
| `10018` | 禁止访问，请核对接入信息 |

业务层失败通常表现为 `code=0` 但 `subCode` 非 `0`，调用方必须同时判断 `code` 和 `subCode`。

## 13. 接入检查清单

- 正式环境和沙箱环境的 `access_key`、`access_secret` 分开申请，不能混用。
- 每次请求都用毫秒时间戳重新签名。
- 上传接口签名时排除 `file`。
- APK 上传和创建/更新接口的 `packageName` 必须一致。
- APK 上传必须传正确的 `fileMd5`；下载方式必须传正确的 `apkMd5`。
- 文件直传方式使用 `serialnumber`；下载方式使用公网 URL，不要混填。
- 电子版权证书和版权证明二选一，不要同时传。
- 定时上架时传 `onlineType=2` 和 `scheOnlineTime`。
- 下载文件方式调用创建/更新后，继续调用 `app.query.task.status` 查询最终结果。
- 处理失败时同时记录 `code`、`subCode`、`msg`、`errorReason`，方便定位。
