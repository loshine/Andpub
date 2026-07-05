<p align="center">
  <h1 align="center">Andpub</h1>
  <p align="center">本地多市场 Android 发包客户端</p>
</p>

<p align="center">
  <a href="./README.en.md">English</a> &nbsp;|&nbsp; 简体中文
</p>

<p align="center">
  <a href="https://github.com/loshine/Andpub/releases"><img alt="Release" src="https://img.shields.io/github/v/release/loshine/Andpub?include_prereleases&label=Release"></a>
  <a href="https://github.com/loshine/Andpub/actions"><img alt="CI" src="https://img.shields.io/github/checks-status/loshine/Andpub/main?label=CI"></a>
  <a href="https://kotlinlang.org"><img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.3.21-7F52FF?logo=kotlin&logoColor=white"></a>
  <a href="https://www.jetbrains.com/lp/compose-multiplatform/"><img alt="Compose Multiplatform" src="https://img.shields.io/badge/Compose%20Multiplatform-1.11.0-4285F4?logo=jetpackcompose&logoColor=white"></a>
  <a href="https://kotlinlang.org/docs/multiplatform.html"><img alt="KMP" src="https://img.shields.io/badge/Kotlin%20Multiplatform-✓-7F52FF"></a>
  <a href="https://github.com/loshine/Andpub/blob/main/LICENSE"><img alt="License" src="https://img.shields.io/github/license/loshine/Andpub?label=License"></a>
  <a href="https://github.com/loshine/Andpub"><img alt="Stars" src="https://img.shields.io/github/stars/loshine/Andpub?style=social"></a>
</p>

---

Andpub 是一个**纯本地运行**的桌面客户端，用于把 Android 应用一键提交到国内多个应用市场审核。
支持 macOS、Windows、Linux，账号密钥只在本机保存，不依赖任何服务端。

## ✨ 特性

- 🖥️ **桌面跨平台**：基于 Kotlin Multiplatform + Compose Multiplatform，一套代码跑 macOS / Windows / Linux。
- 🏪 **多市场支持**：内置 6 个国内主流应用市场适配器。
- 📦 **灵活发包**：统一产物 / 按渠道产物两种模式，支持本地文件与公网 URL 双来源。
- 🧩 **能力矩阵**：根据每个市场的真实能力动态显示发包选项，不支持的能力不可选。
- 🔄 **批量提交**：批量发包时拆成独立任务，单个市场失败不影响其余市场。
- 🔐 **本机密钥**：密钥只保存在本机，不进日志、不进导出文件。
- 🧪 **可重试 + 任务时间线**：保留每个任务的请求/响应摘要、错误码与排查建议，失败任务可重提。

## 🏪 支持的市场

| 市场 | 统一 APK | 32/64 APK | AAB | URL 拉包 | 应用详情 | 提交审核 |
|---|:---:|:---:|:---:|:---:|:---:|:---:|
| 华为 AppGallery | ✅ | 🟡 | ✅ | ✅ | ✅ | ✅ |
| 荣耀应用市场 | ✅ | 🟡 | — | ✅ | ✅ | ✅ |
| 小米应用市场 | ✅ | — | — | — | ✅ | ✅ |
| OPPO 应用市场 | ✅ | ✅ | — | ⚠️ | ✅ | ✅ |
| vivo 应用市场 | ✅ | ✅ | — | ✅ | 🟡 | ✅ |
| 腾讯开放平台 | ✅ | ✅ | — | — | ✅ | ✅ |

图例：✅ 支持 · — 暂不开放 · 🟡 待确认 · ⚠️ 仅支持先上传到市场后返回的 URL

## 🛠 技术栈

- **Kotlin Multiplatform** — 跨平台代码共享
- **Compose Multiplatform** — 一套声明式 UI 跑桌面 / Android / iOS
- **Ktor** — HTTP 客户端
- **Koin** — 依赖注入
- **Room + DataStore** — 本地持久化与配置存储
- **Napier** — 日志

## 📐 模块结构

```text
composeApp
  ├── ui               # 界面：应用列表 / 应用详情 / 渠道配置 / 发布向导
  ├── domain           # 领域模型：应用、渠道、产物、发布任务、市场能力
  ├── application      # 用例：创建应用、添加渠道、提交发布任务等
  ├── data
  │   ├── market       # 各市场 MarketPublisher 实现
  │   ├── remote       # 各市场 Ktor 远程数据源
  │   ├── repository   # 仓储实现
  │   └── local        # Room 本地存储
  └── platform         # 文件选择、APK 解析、安全存储等平台能力
```

模块边界按「未来可演进为服务端 + Web 形态」预留，`MarketPublisher`、`PublishTask`、`Artifact` 等核心接口保持稳定。

## 🚀 快速开始

### 环境要求

- JDK 17+
- Android SDK（编译 Android 目标时）
- Xcode（编译 iOS 目标时，仅为占位）

### 构建并运行桌面端

```shell
# macOS / Linux
./gradlew :composeApp:run

# Windows
.\gradlew.bat :composeApp:run
```

### 构建桌面端安装包

```shell
./gradlew :composeApp:packageDistributionForCurrentOS
```

产物位于 `composeApp/build/compose/binaries/`。

### 构建 Android Debug 包

```shell
# macOS / Linux
./gradlew :composeApp:assembleDebug

# Windows
.\gradlew.bat :composeApp:assembleDebug
```

### 构建 iOS 应用

在 IDE 中选择对应 Run Configuration，或用 Xcode 打开 [`iosApp`](./iosApp) 目录运行。

## 📖 文档

完整需求与路线图位于 [`docs/`](./docs)：

- [多市场发包客户端需求文档](./docs/multi-market-publisher-requirements.md)
- [多市场发包客户端路线图](./docs/multi-market-publisher-roadmap.md)
- 各市场 API 文档：[华为](./docs/huawei-appgallery-connect-publishing-api.md) · [荣耀](./docs/honor-app-market-api-upload.md) · [小米](./docs/xiaomi-app-market-api-upload.md) · [OPPO](./docs/oppo-app-market-api-upload.md) · [vivo](./docs/vivo-app-market-api-upload.md) · [腾讯](./docs/tencent-open-api-update-app-info.md)

## 🗺 路线图

- **阶段 0–1**：桌面端骨架 + 市场能力建模 ✅
- **阶段 2**：渠道配置与应用详情连贯 ✅
- **阶段 3**：本地产物处理（APK/AAB 解析、hash）✅
- **阶段 4**：6 个市场统一 APK 提交审核 ✅
- **阶段 5**：URL 发包（华为 / 荣耀 / vivo）
- **阶段 6**：32/64 APK（vivo / OPPO / 腾讯）
- **阶段 7**：AAB（华为）
- **阶段 8**：体验与可靠性打磨

详见 [路线图文档](./docs/multi-market-publisher-roadmap.md)。

## 📜 许可证

本项目基于 [Apache License 2.0](./LICENSE) 开源。