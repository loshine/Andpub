# OPPO 应用市场 API 发包接入文档

来源：

- https://open.oppomobile.com/documentation/page/info?id=10998
- https://open.oppomobile.com/documentation/page/info?id=10999
- https://open.oppomobile.com/documentation/page/info?id=11000
- https://open.oppomobile.com/documentation/page/info?id=11002
- https://open.oppomobile.com/documentation/page/info?id=11003
- https://open.oppomobile.com/documentation/page/info?id=11004
- https://open.oppomobile.com/documentation/page/info?id=11005
- https://open.oppomobile.com/documentation/page/info?id=11017
- https://open.oppomobile.com/documentation/page/info?id=11176
- https://open.oppomobile.com/documentation/page/info?id=11001
- https://open.oppomobile.com/documentation/page/info?id=12442

整理时间：2026-05-15

## 1. 能力范围

OPPO API 传包能力用于在开发者自己的业务系统里完成应用发布、版本更新、资料更新和详情查询，不必每次进入 OPPO 开放平台管理中心手工操作。

官方限制：

- 使用前必须先在 OPPO 开放平台手动创建应用或游戏。
- 当前能力面向企业普通应用、合作游戏开发者开放。
- 发版接口不能直接填写开发者自己的静态资源 CDN 地址。APK、图标、截图、资质等资源必须先通过 OPPO 文件上传接口上传，拿到 OPPO 返回的 URL 后再填入发布或更新资料接口。

接口正式环境：

```text
https://oop-openapi-cn.heytapmobi.com
```

无特殊说明时，请求和响应编码均为 `UTF-8`，请求 `Content-Type` 为：

```text
application/x-www-form-urlencoded
```

## 2. 接入准备

1. 登录 OPPO 开放平台。
2. 进入 `管理中心` -> `产品导航` -> `我的API`。
3. 创建服务端应用。
4. 获取 `client_id` 和 `client_secret`。
5. 确认目标应用或游戏已经在开放平台创建，并且当前账号有权限操作。

## 3. 推荐发包流程

发布新版本：

1. 调用 `GET /developer/v1/token` 获取 `access_token`。
2. 调用 `GET /resource/v1/upload/get-upload-url` 获取一次性 `upload_url` 和 `sign`。
3. 调用 `upload_url`，用 multipart 上传 APK，`type=apk`。
4. 对图标、截图、资质等资源重复第 2、3 步，按类型上传，拿到 OPPO 返回的资源 URL。
5. 调用 `POST /resource/v1/app/upd` 发布版本。
6. 调用 `POST /resource/v1/app/task-state` 查询异步任务处理状态。

只更新资料、不新增版本：

1. 获取 `access_token`。
2. 上传需要替换的图标、截图、资质等文件。
3. 调用 `POST /resource/v1/app/updm`。
4. 调用 `POST /resource/v1/app/task-state` 查询任务状态。

多包应用更新资料：

```text
POST /resource/v1/app/multi-updm
```

查询当前资料：

```text
GET /resource/v1/app/info
GET /resource/v1/app/multi-info
```

## 4. 获取 Access Token

接口：

```text
GET /developer/v1/token
```

完整地址：

```text
GET https://oop-openapi-cn.heytapmobi.com/developer/v1/token
```

Query：

| 参数 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `client_id` | 是 | string | OPPO 开放平台分配的客户端 ID，18 位字符串 |
| `client_secret` | 是 | string | 与 `client_id` 配对的密钥，64 位字符串 |

响应：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `errno` | int | `0` 表示成功 |
| `data.access_token` | string | 后续接口签名使用的 token |
| `data.expire_in` | int | Unix 时间戳，token 到期时间 |

`access_token` 有效期为 48 小时。过期前重新获取时，新 token 有效 48 小时，旧 token 会在 5 分钟内过期。

## 5. 公共参数和签名

除获取 token 外，调用任何业务接口都必须带以下公共参数：

| 参数 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `access_token` | 是 | string | 获取 token 接口返回的 `access_token` |
| `timestamp` | 是 | string | 秒级时间戳，例如 `1609401600`；允许最大时间误差 15 分钟 |
| `api_sign` | 是 | string | API 输入参数签名 |

签名规则：

1. 取除 `api_sign` 外的公共参数和业务参数。
2. 参数名按 ASCII 升序排序。
3. 忽略值为 `null` 的参数。
4. 拼接为 `k1=v1&k2=v2`。
5. 使用 `client_secret` 作为 key，对拼接字符串做 `HmacSHA256`。
6. 将 hash 结果转成小写 16 进制，得到 `api_sign`。

URL 中所有参数名和参数值都需要 URL 编码。`application/x-www-form-urlencoded` 请求体中的参数值也要 URL 编码。`multipart/form-data` 的表单字段值不需要 URL 编码，但每个表单字段的 charset 需要指定为 `UTF-8`。

## 6. 文件上传

OPPO 发版接口要求先上传资源，再把上传接口返回的 URL 填到发布接口。每个新文件都要重新获取一次上传配置，`sign` 单次有效。

### 6.1 获取上传配置

接口：

```text
GET /resource/v1/upload/get-upload-url
```

请求参数：只需要公共参数。

响应：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `errno` | int | `0` 表示成功 |
| `data.upload_url` | string | 文件上传 URL |
| `data.sign` | string | 一次性上传标识 |

成功示例：

```json
{
  "errno": 0,
  "data": {
    "upload_url": "https://oppo.com/xxxxxxxxx",
    "sign": "b1fe****ad13"
  }
}
```

### 6.2 上传文件

接口：

```text
POST ${upload_url}
```

请求类型：

```text
multipart/form-data
```

表单字段：

| 字段 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `type` | 是 | string | 文件类型：`photo` 图片，`apk` APK 包，`resource` 其它资源 |
| `sign` | 是 | string | 获取上传配置返回的 `sign` |
| `file` | 是 | binary | 文件对象 |

响应 `data`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `url` | string | 文件地址，带域名。发布接口使用这个字段 |
| `uri_path` | string | 文件 URI，不带域名 |
| `md5` | string | 文件 MD5。APK 的 `apk_url` 结构要用 |
| `file_extension` | string | 文件扩展名 |
| `file_size` | int | 文件大小 |
| `id` | string | 标记 |
| `width` | int | 图片宽度，仅图片返回 |
| `height` | int | 图片高度，仅图片返回 |

APK 上传成功示例：

```json
{
  "errno": 0,
  "data": {
    "url": "https://oppo.com/********261d.apk",
    "uri_path": "/********261d.apk",
    "md5": "5efd****4d4d",
    "file_extension": "apk",
    "file_size": 4181241,
    "id": "XXXXX"
  }
}
```

`sign` 过期或不合法时会返回：

```json
{
  "errno": 910003,
  "data": {
    "message": "Unauthorized upload [1]",
    "logid": 2965369111,
    "ext": []
  }
}
```

## 7. 发布版本

接口：

```text
POST /resource/v1/app/upd
```

用途：补充完善资源信息并新增版本。该接口是异步处理任务，处理可能耗时较长，官方建议客户端等待时间设置为 10 秒以上。后续调用获取任务状态接口查询结果。

请求类型：

```text
application/x-www-form-urlencoded
```

核心参数：

| 参数 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `pkg_name` | 是 | string | 应用包名 |
| `version_code` | 是 | string | 版本号 |
| `apk_url` | 是 | ApkInfo[] | APK 包信息，提交时转 JSON 字符串 |
| `app_name` | 是 | string | 应用名称 |
| `second_category_id` | 是 | int | 二级分类 ID，见资源分类对照表 |
| `third_category_id` | 是 | int | 三级分类 ID，见资源分类对照表 |
| `summary` | 是 | string | 一句话简介，不多于 13 个字符，不能包含标点符号和空格 |
| `detail_desc` | 是 | string | 软件介绍，不少于 20 个字 |
| `update_desc` | 是 | string | 版本说明，不少于 5 个字 |
| `privacy_source_url` | 是 | string | 隐私政策网址 |
| `icon_url` | 是 | string | 图标 URL，512x512px，PNG，小于 1MB |
| `pic_url` | 是 | string | 竖版截图 URL，多张用英文逗号分隔；3-5 张，JPG/PNG，1080x1920，单张小于 1MB |
| `landscape_pic_url` | 否 | string | 横版截图 URL；3-5 张，JPG/PNG，1915x1080，单张小于 1MB |
| `video_url` | 否 | string | 游戏宣传视频地址，小于 30MB，MP4 |
| `video_url_material` | 否 | VideoInfo[] | 视频扩展信息，提交时转 JSON 字符串 |
| `online_type` | 是 | int | 发布类型：`1` 审核后立即发布，`2` 定时发布 |
| `sche_online_time` | 否 | datetime | 定时发布时间，`online_type=2` 时必填，格式 `2006-01-02 15:04:05` |
| `test_desc` | 是 | string | 测试附加说明，最多 400 字符 |
| `electronic_cert_url` | 否 | string | 电子版权证书，PDF，小于 20MB |
| `copyright_url` | 是 | string | 软件版权证明，应用、合作应用必填 |
| `icp_url` | 否 | string | ICP 备案网址或备案号 |
| `special_url` | 否 | string | 特殊类证书，JPG/PNG，单张小于 1MB |
| `special_file_url` | 否 | string | 特殊类证书压缩包，RAR/ZIP，小于 30MB |
| `business_username` | 是 | string | 商务联系人姓名 |
| `business_email` | 是 | email | 商务联系人邮箱 |
| `business_mobile` | 是 | string | 商务联系人电话 |
| `age_level` | 是 | int | APP 年龄分级，例如 `3` |
| `adaptive_equipment` | 是 | int | 平板适配：`4` 手机，`5` 平板，`6` 手机和平板 |
| `adaptive_type` | 否 | int | 适配方式：`1` 横竖屏自适应，`2` 平行视窗 |

合作游戏额外常用参数：

| 参数 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `app_subname` | 否 | string | 副标题，1-10 个字符，不得包含空格等特殊字符 |
| `game_type` | 是 | int | 游戏类型：`1` 单机，`2` 网游，`3` 棋牌，`5` 超休闲 |
| `video_pic_url` | 否 | string | 游戏宣传视频横屏封面图，1080x594px，JPG/PNG，小于 1MB |
| `cover_url` | 是 | CoverURLInfo | 游戏空间封面图，提交时转 JSON 字符串 |
| `ascription_type` | 是 | int | 游戏归属权：`1` 自研，`2` 代理 |
| `proxy_contract_url` | 是 | string | 授权合同或代理协议；`ascription_type=2` 时必填 |
| `authorize_type` | 是 | int | 软件著作权登记类型：`1` 著作权证书 |
| `authorize_url` | 是 | string | 软件著作权登记证 |
| `authorize_desc` | 是 | string | 软件著作权登记号 |
| `approval_doc_number` | 是 | string | 游戏版号编号 |
| `approval_doc_type` | 是 | int | 版号有效期类型：`1` 永久有效，`2` 固定有效期 |
| `approval_doc_start_time` | 是 | datetime | 版号有效期开始时间，`approval_doc_type=2` 时必填 |
| `approval_doc_end_time` | 是 | datetime | 版号有效期结束时间，`approval_doc_type=2` 时必填 |
| `approval_doc_url` | 是 | string | 游戏版号证书 |
| `customer_contact` | 是 | string | 客服联系方式 JSON 字符串 |

`ApkInfo`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `url` | string | APK 包地址，即上传接口返回的 `url` |
| `md5` | string | APK 包 MD5，即上传接口返回的 `md5` |
| `cpu_code` | int | 多包平台：`64` 为 64 位 CPU 包，`32` 为 32 位 CPU 包，非多包应用传 `0` |

`apk_url` 示例：

```json
[
  {
    "url": "https://oppo.com/example.apk",
    "md5": "5efd****4d4d",
    "cpu_code": 0
  }
]
```

`VideoInfo`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `url` | string | 视频地址 |
| `md5` | string | 视频 MD5 |
| `size` | int | 视频文件大小 |
| `width` | int | 视频宽度 |
| `height` | int | 视频高度 |
| `fps` | int | 视频帧率 |
| `duration` | int | 视频时长 |
| `definition` | int | 视频码率 |

`CoverURLInfo`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `h` | map | 横版封面，包含 `url`，尺寸 939x507px，JPG/PNG，小于 1MB |
| `v` | map | 竖版封面，包含 `url`，尺寸 756x1080px，JPG/PNG，小于 1MB |

响应：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `errno` | int | `0` 表示请求正常 |
| `data.success` | bool | 请求成功时为 `true` |
| `data.message` | string | 失败时返回错误说明 |
| `data.logid` | int | 请求 ID |

成功示例：

```json
{
  "errno": 0,
  "data": {
    "success": true,
    "message": ""
  }
}
```

## 8. 更新资料

接口：

```text
POST /resource/v1/app/updm
```

用途：更新资源相关信息，不新增版本。该接口也可能耗时较长，官方建议客户端等待时间设置为 10 秒以上。

常用参数：

| 参数 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `pkg_name` | 是 | string | 应用包名 |
| `version_code` | 是 | string | 版本号 |
| `summary` | 是 | string | 一句话简介，不多于 15 个字符，不能包含标点符号和空格 |
| `detail_desc` | 是 | string | 软件介绍，不少于 20 个字 |
| `update_desc` | 是 | string | 版本说明，不少于 5 个字 |
| `privacy_source_url` | 是 | string | 隐私政策网址 |
| `icon_url` | 是 | string | 图标 URL，512x512px，PNG，小于 1MB |
| `pic_url` | 是 | string | 竖版截图 URL，多张用英文逗号分隔；3-5 张 |
| `landscape_pic_url` | 否 | string | 横版截图 URL；3-5 张 |
| `video_url` | 否 | string | 游戏宣传视频，小于 30MB，MP4 |
| `video_url_material` | 否 | VideoInfo[] | 视频扩展信息，提交时转 JSON 字符串 |
| `test_desc` | 是 | string | 测试附加说明，最多 400 字符 |
| `electronic_cert_url` | 否 | string | 电子版权证书，PDF，小于 20MB |
| `copyright_url` | 是 | string | 软件版权证明，应用、合作应用必填 |
| `icp_url` | 否 | string | ICP 备案网址或备案号 |
| `special_url` | 否 | string | 特殊类证书，JPG/PNG，单张小于 1MB |
| `special_file_url` | 否 | string | 特殊类证书压缩包，RAR/ZIP，小于 30MB |
| `business_username` | 是 | string | 商务联系人姓名 |
| `business_email` | 是 | email | 商务联系人邮箱 |
| `business_mobile` | 是 | string | 商务联系人电话 |
| `customer_contact` | 是 | string | 合作游戏客服联系方式 JSON 字符串 |

响应结构同发布版本接口。

## 9. 多包资料更新

接口：

```text
POST /resource/v1/app/multi-updm
```

用途：多包应用资料更新。

参数：

| 参数 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `pkg_name` | 是 | string | 包名 |
| `version_code` | 是 | string | 版本号 |
| `summary` | 是 | string | 一句话简介，不多于 13 个字符，不能包含标点符号和空格 |
| `detail_desc` | 是 | string | 软件介绍，不少于 20 个字 |
| `update_desc` | 是 | string | 版本说明，不少于 5 个字 |
| `privacy_source_url` | 是 | string | 隐私政策网址 |
| `icon_url` | 是 | string | 图标 URL，512x512px，PNG，小于 1MB |
| `pic_url` | 是 | string | 竖版截图 URL，多张用英文逗号分隔，3-5 张 |
| `landscape_pic_url` | 否 | string | 横版截图 URL，3-5 张 |
| `video_url` | 否 | string | 游戏宣传视频地址，小于 30MB，MP4 |
| `video_url_material` | 否 | VideoInfo[] | 视频扩展信息，提交时转 JSON 字符串 |
| `test_desc` | 是 | string | 测试附加说明，最多 400 字符 |
| `electronic_cert_url` | 否 | string | 电子版权证书，PDF，小于 20MB |
| `copyright_url` | 是 | string | 软件版权证明 |
| `icp_url` | 否 | string | ICP 备案网址或备案号 |
| `special_url` | 否 | string | 特殊类证书，JPG/PNG，单张小于 1MB |
| `special_file_url` | 否 | string | 特殊类证书压缩包，RAR/ZIP，小于 30MB |
| `business_username` | 是 | string | 商务联系人姓名 |
| `business_email` | 是 | email | 商务联系人邮箱 |
| `business_mobile` | 是 | string | 商务联系人电话 |

失败示例：

```json
{
  "errno": 911219,
  "data": {
    "message": "游戏暂不支持 CPU 多包",
    "logid": 4218474084
  }
}
```

## 10. 查询应用详情

普通包应用详情：

```text
GET /resource/v1/app/info
```

Query：

| 参数 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `pkg_name` | 是 | string | 应用包名 |
| `version_code` | 否 | string | 版本号，默认取最新版本 |

多包应用详情：

```text
GET /resource/v1/app/multi-info
```

Query：

| 参数 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `pkg_name` | 是 | string | 应用包名 |

普通包详情返回的关键字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `app_id` | string | 应用 ID |
| `pkg_name` | string | 应用包名 |
| `type` | int | 应用类型：`7` 普通应用，`8` 合作游戏，`10` 合作应用 |
| `sign` | string | 包签名 |
| `dev_id` | string | 开发者 ID |
| `app_name` | string | 应用名称 |
| `second_category_id` | string | 二级分类 ID |
| `third_category_id` | string | 三级分类 ID |
| `version_id` | string | 版本 ID |
| `version_code` | string | 版本号 |
| `version_name` | string | 版本名称 |
| `apk_url` | string | APK 文件地址 |
| `apk_md5` | string | APK 文件 MD5 |
| `online_type` | string | 发布类型：`1` 审核立即发布，`2` 定时发布 |
| `audit_status` | string | 审核状态 |
| `audit_status_name` | string | 审核状态描述 |
| `release_status` | string | 分阶段发布状态 |
| `update_info_check` | int | 更新资料审核状态：`1` 审核中，`0` 不在审核中 |
| `refuse_reason` | string | 审核拒绝原因 |
| `refuse_advice` | string | 修改建议 |

多包详情会返回 `apk_info`，类型为 `map<String, ApkVersionInfo>`，key 为版本号，value 为版本信息。

## 11. 获取任务状态

接口：

```text
POST /resource/v1/app/task-state
```

用途：查询发布版本、更新资料等异步任务处理状态。

参数：

| 字段 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `pkg_name` | 是 | string | 包名 |
| `version_code` | 是 | string | 版本号 |

响应：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `pkg_name` | string | 包名 |
| `version_code` | string | 版本号 |
| `task_state` | string | `1` 待处理，`2` 处理成功，`3` 处理失败 |
| `err_msg` | string | 错误原因 |

示例：

```json
{
  "errno": 0,
  "data": {
    "pkg_name": "com.foo.bar",
    "version_code": "123",
    "task_state": "3",
    "err_msg": "您上传的 apk 中的包名 com.test.foo 与当前应用包名不一致，请重新上传"
  }
}
```

## 12. 分类和审核状态

发布版本时需要传 `second_category_id` 和 `third_category_id`。完整分类以资源分类对照表为准。常见应用分类示例：

| 一级分类 ID | 一级分类 | 二级分类 ID | 二级分类 | 三级分类 ID | 三级分类 |
| --- | --- | --- | --- | --- | --- |
| `7` | 应用 | `74` | 社交通讯 | `6654` | 电话短信 |
| `7` | 应用 | `77` | 便捷生活 | `6689` | 美食外卖 |
| `7` | 应用 | `78` | 实用工具 | `6724` | 浏览器 |
| `7` | 应用 | `79` | 资讯阅读 | `8189` | 新闻 |
| `7` | 应用 | `80` | 系统优化 | `8121` | 垃圾清理 |
| `7` | 应用 | `460` | 拍摄美化 | `6680` | 相机 |

审核状态：

| 值 | 描述 |
| --- | --- |
| `0` | 未发布 |
| `1` | 审核中 |
| `2` | 审核通过 |
| `3` | 测试不通过 |
| `4` | 运营审核中 |
| `5` | 运营打回 |
| `6` | 运营通过 |
| `7` | 定时发布 |
| `00` | 资质审核中 |
| `11` | 资质审核通过 |
| `-11` | 资质审核不通过 |
| `-22` | 报备提交成功 |
| `22` | 已冻结 |
| `111` | 上线 |
| `222` | 下线 |
| `444` | 审核不通过 |
| `x` | 其他 |

## 13. 最小可用请求序列

发布 APK 新版本：

```text
GET  /developer/v1/token?client_id=${client_id}&client_secret=${client_secret}
GET  /resource/v1/upload/get-upload-url?access_token=${access_token}&timestamp=${timestamp}&api_sign=${api_sign}
POST ${upload_url}
POST /resource/v1/app/upd
POST /resource/v1/app/task-state
```

只更新资料：

```text
GET  /developer/v1/token?client_id=${client_id}&client_secret=${client_secret}
GET  /resource/v1/upload/get-upload-url
POST ${upload_url}
POST /resource/v1/app/updm
POST /resource/v1/app/task-state
```

查询当前状态：

```text
GET  /resource/v1/app/info?pkg_name=${pkg_name}
GET  /resource/v1/app/multi-info?pkg_name=${pkg_name}
POST /resource/v1/app/task-state
```

实现时别省日志。至少记录参与签名的参数串、`api_sign`、请求 URL、请求体、响应体、`logid` 和任务状态。OPPO 这套接口参数多、异步多，不留日志就是给自己挖坑。
