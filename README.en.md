<p align="center">
  <h1 align="center">Andpub</h1>
  <p align="center">A local multi-market Android publishing client</p>
</p>

<p align="center">
  English &nbsp;|&nbsp; <a href="./README.md">简体中文</a>
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

Andpub is a **purely local** desktop client for submitting Android apps to multiple Chinese
app markets in one go. It runs on macOS, Windows, and Linux, keeps credentials only on the
local machine, and never depends on a server.

## ✨ Features

- 🖥️ **Cross-platform desktop**: Built on Kotlin Multiplatform + Compose Multiplatform — one
  codebase for macOS / Windows / Linux.
- 🏪 **Multi-market**: Built-in adapters for 6 mainstream Chinese app markets.
- 📦 **Flexible publishing**: Unified-artifact or per-channel-artifact modes, with both local
  files and public URLs as sources.
- 🧩 **Capability matrix**: Publishing options are shown dynamically based on each market's
  real capabilities; unsupported combinations cannot be submitted.
- 🔄 **Batch submission**: Batch publishing fans out into independent tasks — a failure on one
  market won't block the others.
- 🔐 **Local-only secrets**: Credentials stay on the local machine — never in logs, never in
  exports.
- 🧪 **Retry + task timeline**: Each task keeps request/response digests, error codes and
  troubleshooting hints; failed tasks can be re-submitted.

## 🏮 Supported markets

| Market | Unified APK | 32/64 APK | AAB | URL pull | App details | Submit for review |
|---|:---:|:---:|:---:|:---:|:---:|:---:|
| Huawei AppGallery | ✅ | 🟡 | ✅ | ✅ | ✅ | ✅ |
| Honor | ✅ | 🟡 | — | ✅ | ✅ | ✅ |
| Xiaomi | ✅ | — | — | — | ✅ | ✅ |
| OPPO | ✅ | ✅ | — | ⚠️ | ✅ | ✅ |
| vivo | ✅ | ✅ | — | ✅ | 🟡 | ✅ |
| Tencent | ✅ | ✅ | — | — | ✅ | ✅ |

Legend: ✅ supported · — not open yet · 🟡 to be confirmed · ⚠️ URL must be obtained by uploading to the market first

## 🛠 Tech stack

- **Kotlin Multiplatform** — shared cross-platform code
- **Compose Multiplatform** — a single declarative UI for desktop / Android / iOS
- **Ktor** — HTTP client
- **Koin** — dependency injection
- **Room + DataStore** — local persistence and config storage
- **Napier** — logging

## 📐 Module layout

```text
composeApp
  ├── ui               # screens: app list / app detail / channel config / publish wizard
  ├── domain           # models: app, channel, artifact, publish task, market capability
  ├── application      # use cases: create app, add channel, submit publish task, etc.
  ├── data
  │   ├── market       # per-market MarketPublisher implementations
  │   ├── remote       # per-market Ktor remote data sources
  │   ├── repository   # repository implementations
  │   └── local        # local Room storage
  └── platform         # file picker, APK parsing, secure storage, etc.
```

Module boundaries are designed to allow a future server + Web evolution while keeping core
interfaces such as `MarketPublisher`, `PublishTask`, and `Artifact` stable.

## 🚀 Quick start

### Requirements

- JDK 17+
- Android SDK (when building the Android target)
- Xcode (when building the iOS target; placeholder only)

### Build and run the desktop app

```shell
# macOS / Linux
./gradlew :composeApp:run

# Windows
.\gradlew.bat :composeApp:run
```

### Package a desktop installer

```shell
./gradlew :composeApp:packageDistributionForCurrentOS
```

Output is written to `composeApp/build/compose/binaries/`.

### Build the Android debug APK

```shell
# macOS / Linux
./gradlew :composeApp:assembleDebug

# Windows
.\gradlew.bat :composeApp:assembleDebug
```

### Build the iOS app

Use the corresponding Run Configuration in your IDE, or open the [`iosApp`](./iosApp) directory
in Xcode and run it from there.

## 📖 Docs

Full requirements and roadmap live under [`docs/`](./docs):

- [Multi-market publisher requirements](./docs/multi-market-publisher-requirements.md) (in Chinese)
- [Multi-market publisher roadmap](./docs/multi-market-publisher-roadmap.md) (in Chinese)
- Per-market API docs: [Huawei](./docs/huawei-appgallery-connect-publishing-api.md) · [Honor](./docs/honor-app-market-api-upload.md) · [Xiaomi](./docs/xiaomi-app-market-api-upload.md) · [OPPO](./docs/oppo-app-market-api-upload.md) · [vivo](./docs/vivo-app-market-api-upload.md) · [Tencent](./docs/tencent-open-api-update-app-info.md)

## 🗺 Roadmap

- **Phase 0–1**: Desktop skeleton + market-capability modeling ✅
- **Phase 2**: Channel config & app details ✅
- **Phase 3**: Local artifact handling (APK/AAB parsing, hashing) ✅
- **Phase 4**: Unified-APK submit-for-review across 6 markets ✅
- **Phase 5**: URL publishing (Huawei / Honor / vivo)
- **Phase 6**: 32/64 APK (vivo / OPPO / Tencent)
- **Phase 7**: AAB (Huawei)
- **Phase 8**: UX & reliability polish

See the [roadmap doc](./docs/multi-market-publisher-roadmap.md) for details.

## 📜 License

This project is open-sourced under the [Apache License 2.0](./LICENSE).