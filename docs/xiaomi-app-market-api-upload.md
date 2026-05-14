# 小米应用市场 API 传包接入文档

来源：https://dev.mi.com/console/doc/detail?pId=33  
原文标题：应用自动发布接口操作指南  
整理时间：2026-05-15

## 1. 能力范围

小米应用市场提供自动发布接口，开发者可以通过 API 向小米应用商店推送 APK 包。接口支持：

- 查询应用包信息。
- 查询应用分类。
- 新增应用。
- 更新应用版本，也就是传新 APK。
- 修改应用信息。

接口基地址：

```text
http://api.developer.xiaomi.com/devupload
```

官方文档使用 HTTP 地址，并明确说明这是接口地址，不是网页地址。别擅自把协议、路径和字段名“优化”掉，老接口通常就吃这一套。

## 2. 协议和数据格式

- 协议：HTTP/1.1。
- 方法：所有接口均使用 `POST`。
- 编码：UTF-8。
- 请求和响应数据：JSON。
- 需要安全验证的接口，必须传：
  - `RequestData`：请求 JSON 字符串。
  - `SIG`：数字签名。
  - 文件字段：按接口规定传，例如 `apk`。

## 3. 签名规则

需要签名的接口包括 `/dev/query` 和 `/dev/push`。

签名输入不是业务 JSON 本身，而是一个签名 JSON：

```json
{
  "password": "访问密码",
  "sig": [
    {
      "name": "RequestData",
      "hash": "MD5(RequestData字符串)"
    },
    {
      "name": "apk",
      "hash": "MD5(apk文件内容)"
    }
  ]
}
```

生成 `SIG`：

1. 对每个请求参数计算 MD5。
2. 普通参数计算参数值字符串的 MD5。
3. 文件参数计算整个文件内容的 MD5。
4. 把参数名和 MD5 放入 `sig` 数组。
5. 加上接口平台分配的访问密码 `password`。
6. 用小米应用商店分配的公钥处理该 JSON 字符串。
7. RSA 算法按官方示例使用 `RSA/NONE/PKCS1Padding`。
8. 将结果转成小写 16 进制字符串，作为 `SIG`。

官方 Java 示例实际用的是公钥加密：

```java
Cipher cipher = Cipher.getInstance("RSA/NONE/PKCS1Padding", "BC");
cipher.init(Cipher.ENCRYPT_MODE, publicKey);
```

所以实现时按官方示例做，不要换成 `SHA256withRSA` 这类常规签名算法。字段顺序也别乱改，最稳的是按示例构造 JSON。

## 4. 传包推荐流程

更新已有应用版本：

1. 调用 `/dev/query` 查询包名在当前账号下的状态。
2. 确认返回 `updateVersion=true`。
3. 准备 APK 文件。
4. 构造 `RequestData`，其中 `synchroType=1`。
5. multipart 提交 `/dev/push`，带上 `RequestData`、`SIG`、`apk`。
6. 检查返回 `result=0`。

新增应用：

1. 调用 `/dev/query` 查询包名。
2. 确认返回 `create=true`。
3. 调用 `/dev/category` 获取分类 ID。
4. 准备 APK、图标和至少 3 张截图。
5. 构造 `RequestData`，其中 `synchroType=0`。
6. multipart 提交 `/dev/push`。
7. 检查返回 `result=0`。

只修改资料：

1. 调用 `/dev/query` 查询包名。
2. 确认返回 `updateInfo=true`。
3. 构造 `RequestData`，其中 `synchroType=2`。
4. 调用 `/dev/push`，通常不需要传 `apk`。

## 5. 查询应用包

接口：

```text
POST /dev/query
```

完整地址：

```text
http://api.developer.xiaomi.com/devupload/dev/query
```

用途：通过包名查询当前小米开发者账号下最新应用详情，判断是否允许新增、更新包或更新信息。

请求字段放在 `RequestData` 中：

| 字段 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `packageName` | 是 | string | 应用包名 |
| `userName` | 是 | string | 小米开发者站登录邮箱 |

请求示例：

```json
{
  "packageName": "com.example.app",
  "userName": "developer@example.com"
}
```

提交时还要附带 `SIG`。`SIG` 的 `sig` 数组至少包含：

```json
[
  {
    "name": "RequestData",
    "hash": "MD5(RequestData字符串)"
  }
]
```

响应字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `result` | int | `0` 表示成功；非 `0` 表示失败；`-7` 表示包名被其他开发者占用，需要认领 |
| `packageInfo` | object | 应用包详情；为空表示不存在相应包 |
| `create` | boolean | 是否允许新增该包名的应用 |
| `updateVersion` | boolean | 是否允许更新应用版本 |
| `updateInfo` | boolean | 是否允许更新应用信息 |
| `message` | string | 响应消息，异常时返回错误信息 |

`packageInfo` 字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `appName` | string | 应用名 |
| `versionName` | string | 版本名 |
| `versionCode` | long | 版本号 |
| `packageName` | string | 包名 |

响应示例：

```json
{
  "result": 0,
  "updateVersion": false,
  "updateInfo": false,
  "create": false,
  "message": "查询成功",
  "packageInfo": {
    "appName": "应用名称",
    "packageName": "com.example.app",
    "versionCode": 4,
    "versionName": "1.1.1"
  }
}
```

## 6. 查询应用分类

接口：

```text
POST /dev/category
```

完整地址：

```text
http://api.developer.xiaomi.com/devupload/dev/category
```

用途：查询小米应用商店分类 ID。新增应用时 `appInfo.category` 需要传分类 ID。

请求参数：无。

响应字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `result` | int | `0` 表示成功，非 `0` 表示失败 |
| `message` | string | 响应消息 |
| `categories` | list | 分类列表 |

分类实体：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `categoryId` | string | 分类 ID |
| `categoryName` | string | 分类名称 |

示例分类：

```json
{
  "result": 0,
  "message": "查询成功",
  "categories": [
    { "categoryId": 1, "categoryName": "理财" },
    { "categoryId": 2, "categoryName": "聊天与社交" },
    { "categoryId": 3, "categoryName": "旅行与交通" },
    { "categoryId": 4, "categoryName": "生活" },
    { "categoryId": 5, "categoryName": "实用工具" },
    { "categoryId": 12, "categoryName": "学习与教育" },
    { "categoryId": 27, "categoryName": "影音视听" }
  ]
}
```

分类会变，别把示例列表写死到业务逻辑里。启动前或发布前调用接口取一次。

## 7. 推送应用 / 传 APK

接口：

```text
POST /dev/push
```

完整地址：

```text
http://api.developer.xiaomi.com/devupload/dev/push
```

用途：推送应用到小米应用商店。新增、更新包、修改应用信息都走这个接口。

请求类型：`multipart/form-data`。

请求字段：

| 字段 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `RequestData` | 是 | string | 请求 JSON 字符串 |
| `SIG` | 是 | string | 按第 3 节生成的签名 |
| `apk` | 条件必选 | file | APK 包；`synchroType=0` 新增和 `synchroType=1` 更新包时必传 |
| `icon` | 否 | file | 应用图标 |
| `screenshot_1` | 条件必选 | file | 第 1 张截图；新增应用时必选 |
| `screenshot_2` | 条件必选 | file | 第 2 张截图；新增应用时必选 |
| `screenshot_3` | 条件必选 | file | 第 3 张截图；新增应用时必选 |
| `screenshot_4` | 否 | file | 第 4 张截图，显示顺序为 1 到 5 |
| `screenshot_5` | 否 | file | 第 5 张截图 |

`RequestData` 字段：

| 字段 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `userName` | 是 | string | 小米开发者站登录邮箱 |
| `synchroType` | 是 | int | 更新类型：`0` 新增，`1` 更新包，`2` 内容更新 |
| `appInfo` | 是 | object/string | 应用包信息，官方表格写 string，示例使用 JSON 对象 |

`appInfo` 字段：

| 字段 | 必选 | 类型 | 说明 |
| --- | --- | --- | --- |
| `appName` | 是 | string | 应用名称 |
| `packageName` | 是 | string | 包名 |
| `publisherName` | 否 | string | 开发者名称；不传默认使用开发者站注册名称 |
| `versionName` | 否 | string | 版本名；默认使用 APK 中的 `versionName` |
| `category` | 条件必选 | int | 应用分类 ID；新增应用 `synchroType=0` 时必选 |
| `keyWords` | 条件必选 | string | 搜索关键字，空格分隔；新增时必选 |
| `desc` | 条件必选 | string | 应用介绍；新增时必选 |
| `updateDesc` | 条件必选 | string | 更新说明；更新应用时必选 |
| `shortDesc` | 否 | string | 简单介绍 |
| `web` | 否 | string | 应用官网 |
| `price` | 否 | double/string | 价格；示例中可传空字符串 |

更新包请求示例：

```json
{
  "userName": "developer@example.com",
  "synchroType": 1,
  "appInfo": {
    "appName": "应用名称",
    "category": 2,
    "desc": "应用详情",
    "keyWords": "关键字1 关键字2",
    "packageName": "com.example.app",
    "price": "",
    "publisherName": "发布者名称",
    "shortDesc": "",
    "updateDesc": "版本更新日志",
    "versionName": "1.2.0",
    "web": "https://example.com"
  }
}
```

更新包时 multipart 至少包含：

| multipart 字段 | 内容 |
| --- | --- |
| `RequestData` | 上面的 JSON 字符串 |
| `SIG` | 对 `RequestData` 和 `apk` 计算后生成的签名 |
| `apk` | APK 文件 |

签名 JSON 示例：

```json
{
  "password": "访问密码",
  "sig": [
    {
      "name": "RequestData",
      "hash": "RequestData字符串的MD5"
    },
    {
      "name": "apk",
      "hash": "APK文件内容的MD5"
    }
  ]
}
```

新增应用时还要把图标和截图放进 multipart，并把每个文件的 MD5 放进 `sig` 数组：

```json
{
  "password": "访问密码",
  "sig": [
    { "name": "RequestData", "hash": "MD5(RequestData)" },
    { "name": "apk", "hash": "MD5(apk文件)" },
    { "name": "icon", "hash": "MD5(icon文件)" },
    { "name": "screenshot_1", "hash": "MD5(截图1)" },
    { "name": "screenshot_2", "hash": "MD5(截图2)" },
    { "name": "screenshot_3", "hash": "MD5(截图3)" }
  ]
}
```

响应字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `result` | int | `0` 表示成功，非 `0` 表示推送失败 |
| `message` | string | 成功时可为空或返回成功消息；失败时返回错误信息 |

响应示例：

```json
{
  "result": 0,
  "message": "操作成功"
}
```

## 8. curl 形态示例

真实 `SIG` 需要由程序生成，这里只展示请求长相：

```bash
curl -X POST 'http://api.developer.xiaomi.com/devupload/dev/push' \
  -F 'RequestData={"userName":"developer@example.com","synchroType":1,"appInfo":{"appName":"应用名称","packageName":"com.example.app","updateDesc":"版本更新日志"}}' \
  -F 'SIG=小写16进制RSA结果' \
  -F 'apk=@/path/to/app-release.apk'
```

新增应用示例：

```bash
curl -X POST 'http://api.developer.xiaomi.com/devupload/dev/push' \
  -F 'RequestData={"userName":"developer@example.com","synchroType":0,"appInfo":{"appName":"应用名称","packageName":"com.example.app","category":2,"keyWords":"关键字1 关键字2","desc":"应用详情"}}' \
  -F 'SIG=小写16进制RSA结果' \
  -F 'apk=@/path/to/app-release.apk' \
  -F 'icon=@/path/to/icon.png' \
  -F 'screenshot_1=@/path/to/screenshot-1.png' \
  -F 'screenshot_2=@/path/to/screenshot-2.png' \
  -F 'screenshot_3=@/path/to/screenshot-3.png'
```

## 9. 官方 Java 示例要点

官方示例文件：

```text
https://t1.market.xiaomi.com/download/AppStore/09b54e4c74d644a292dc96a1ca379b04f13493eab/Example.java
```

示例中的常量：

```java
private static final String DOMAIN = "http://api.developer.xiaomi.com/devupload";
private static final String PUSH = DOMAIN + "/dev/push";
private static final String QUERY = DOMAIN + "/dev/query";
private static final String CATEGORY = DOMAIN + "/dev/category";
public static final String KEY_ALGORITHM = "RSA/NONE/PKCS1Padding";
```

示例中的 multipart 字段：

```java
entity.addPart("RequestData", new StringBody(json.toString(), Charset.forName("UTF-8")));
entity.addPart("SIG", new StringBody(encryptByPublicKey(sigJSON.toString(), pubKey), Charset.forName("UTF-8")));
entity.addPart("apk", new FileBody(apkFile));
entity.addPart("icon", new FileBody(iconFile));
entity.addPart("screenshot_1", new FileBody(screenshot1));
```

示例里还出现了渠道包接口：

```text
POST /dev/pushChannelApk
```

字段为：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `RequestData` | string | 包含 `userName` 和 `apkChannel` |
| `SIG` | string | 对 `RequestData` 和 `channelApk` 生成签名 |
| `channelApk` | file | 渠道 APK |

该接口出现在官方示例代码中，但不在页面正文的接口列表里。要用它，先在小米开发者后台或工单确认当前账号是否开放。

## 10. 实现检查清单

- 账号使用小米开发者站登录邮箱，即 `userName`。
- 先用 `/dev/query` 判断 `create`、`updateVersion`、`updateInfo`，不要盲传。
- `synchroType=1` 才是更新包。
- 更新包时 `apk` 必传，且 `sig` 里必须包含 `apk` 文件 MD5。
- 新增应用时至少传 `apk`、`screenshot_1`、`screenshot_2`、`screenshot_3`；图标按业务要求准备。
- `RequestData` 必须用和签名时完全相同的字符串提交。JSON 重新序列化一次就可能导致 MD5 不一致。
- `SIG` 使用小写 16 进制。
- RSA 使用 `RSA/NONE/PKCS1Padding`，并加载小米提供的 X.509 公钥证书。
- `result=0` 只表示接口处理成功；后续审核和上架状态仍以小米后台为准。
