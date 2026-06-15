## Context

Andpub currently manages applications/channels, queries vendor app information, prepares local or URL artifacts, and renders a publish-process page. The publish action still creates local records only; `MarketPublisher` exposes `fetchAppInfo(...)` but no publish capability. vivo is the best first real publishing integration because the docs define a sandbox endpoint, production endpoint, unified APK upload, 32/64 APK upload, update submission, and task-status querying.

Current relevant shape:

```text
Publish UI
  │
  ▼
CreatePublishTasksUseCase
  │  prepares artifacts and logs
  ▼
Local PublishTaskRecord
  │
  └── no vendor upload/submit yet
```

Target shape:

```text
Publish UI
  │
  ▼
Publish orchestration use case
  ├── prepare/download/validate artifact
  ├── route by MarketType
  └── MarketPublisher.publish(...)
        │
        ▼
      VivoMarketPublisher
        ├── upload APK / upload 32+64 APK
        ├── submit update request
        └── query task status when applicable
```

## Goals / Non-Goals

**Goals:**

- Add a per-vivo-channel environment switch for sandbox vs production.
- Route every vivo API call through the selected environment endpoint.
- Execute a real vivo update publish flow for existing vivo apps with unified APK and 32/64 APK artifacts.
- Record download, validation, upload, submit, and task-status information in publish tasks.
- Make failures visible per channel without blocking other selected channels.
- Keep existing non-vivo markets on the current prepared/validation-only path.

**Non-Goals:**

- Implement real publish flows for Huawei, Honor, Xiaomi, OPPO, or Tencent.
- Build a complete vivo app metadata editor for icons, screenshots, classifications, copyright, or other non-APK review materials.
- Support first-time vivo app creation.
- Implement automated polling, background scheduling, or callback hosting.
- Store credentials outside the existing local channel configuration model.

## Decisions

### Decision 1: Store vivo environment as a channel extra field

Use a typed domain enum in code, persisted through the channel's existing `extraFields`, for example `vivoEnvironment=sandbox|production`.

Rationale:

- It keeps environment selection scoped to the channel because sandbox and production credentials are separate.
- It avoids adding a global setting that could accidentally route production credentials to sandbox or vice versa.
- It fits the current channel model without a database migration beyond existing serialized state compatibility.

Alternative considered: global vivo environment setting. Rejected because it makes multi-channel testing and production coexistence unsafe.

### Decision 2: Inject vivo base URL into RemoteDataSource calls

Replace the hard-coded vivo `BASE` usage with an endpoint selected from channel configuration. The RemoteDataSource can keep default production behavior for backward compatibility, but publisher calls must pass the selected environment endpoint.

Rationale:

- Endpoint selection belongs near remote calls, not in UI.
- Tests can assert sandbox and production request URLs without invoking the real network.

Alternative considered: create separate `SandboxVivoRemoteDataSource` and `ProductionVivoRemoteDataSource`. Rejected because all methods are identical except base URL.

### Decision 3: Extend MarketPublisher with publish capability

Add a publish method to the publisher abstraction instead of wiring vivo directly in the ViewModel. The method should accept app, channel, prepared artifact, and publish options; it returns a typed result with stage logs and vendor identifiers.

Rationale:

- The app already uses strategy-style `MarketPublisher` implementations by market.
- Adding publish to the same boundary prevents market-specific logic from leaking into the UI.
- Other markets can later implement the same contract incrementally.

Alternative considered: create `VivoPublishUseCase` called directly from the ViewModel. Rejected because it duplicates the strategy boundary and would be harder to generalize to other markets.

### Decision 4: First publish flow uses direct file upload for downloaded/local artifacts

For the first real vivo publish slice, prefer direct file upload:

- Unified APK: `app.upload.apk.app` then `app.sync.update.app`.
- 32/64 APK: `app.upload.apk.app.32` and `app.upload.apk.app.64` then `app.sync.update.subpackage.app`.

URL artifacts should continue to be downloaded locally first by the existing preparation flow, then uploaded from the downloaded file.

Rationale:

- Direct upload works in sandbox without requiring vivo to fetch from a developer-hosted public URL.
- The current preparation flow already produces verified local files.
- It gives the publish-process page concrete upload serial numbers.

Alternative considered: use vivo download-file APIs (`app.update.app`, `app.update.subpackage.app`) directly for URL artifacts. Keep this for a later extension after direct upload works end to end.

### Decision 5: First implementation is update-only

The first implementation should only update apps that are known to exist in the selected vivo environment. Existence can be proven by a successful app-info query for the package/channel in that environment. If existence cannot be proven, the publish task must fail before upload or submit with a clear message.

Rationale:

- Creating an app requires icon, screenshots, categories, copyright, and other metadata that are outside this change.
- Update-only avoids implicitly creating an app from fragile local state during an update publish flow.
- Sandbox can validate create behavior later as a separate change.

### Decision 6: Model update submit fields explicitly

The vivo update submit request must be built from typed data, not an open `Map<String, String>` assembled in UI code. The first version must support these update inputs:

- `packageName` from the selected app.
- `versionCode` from the validated APK metadata or vivo upload response.
- `apk` / `apk32` / `apk64` from vivo upload `serialnumber` values.
- `fileMd5` from the prepared artifact or vivo upload response.
- `onlineType` from an explicit publish option.
- `compatibleDevice` from an explicit publish option.

If any required field is missing, the publish task fails before submit and displays the missing field names. No hidden default should be used for `onlineType` or `compatibleDevice`.

## Risks / Trade-offs

- [Risk] vivo sandbox credentials are separate and may not be available to users. → Mitigation: expose environment visibly on the channel form and fail fast with environment-specific guidance.
- [Risk] Direct file upload needs real multipart file parts; current RemoteDataSource upload helpers accept extra form fields but must be checked for actual file content wiring. → Mitigation: add RemoteDataSource contract tests before enabling publish execution.
- [Risk] vivo update requires fields beyond APK serial numbers. → Mitigation: model the required update submit fields explicitly and fail before submit when any are unavailable.
- [Risk] Publish task status may become too vendor-specific. → Mitigation: keep generic stages in domain and attach vendor details as structured result fields/logs.
- [Risk] Sandbox and production data are isolated, so app IDs/status may differ. → Mitigation: make environment part of channel identity in UI summaries and logs.
- [Risk] A real production publish is high impact. → Mitigation: show the selected environment before task creation and require explicit confirmation before submitting to production.

## Migration Plan

1. Add vivo environment to channel configuration with default `production` for existing channels.
2. Preserve existing fetch behavior by using production when no environment field exists.
3. Add sandbox/production base URL selection in vivo RemoteDataSource calls.
4. Add publish status/result fields in a backward-compatible way with defaults.
5. Implement vivo update publish execution behind the existing publish button for vivo channels only.
6. Keep non-vivo publish tasks as validation-only until their market publishers implement publish.

Rollback strategy: revert the publish method wiring and keep the artifact preparation page; existing app/channel records remain valid because environment defaults are non-breaking.

## Resolved Scope

- The first implementation is update-only.
- First-time vivo app creation is deferred until app metadata editing/export is designed.
- URL-download-mode vivo APIs are deferred because this change downloads URL artifacts locally and uses direct upload.
