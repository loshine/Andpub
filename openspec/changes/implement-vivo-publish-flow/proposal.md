## Why

The current publish tab can prepare artifacts and show a publish-process view, but it still stops at local task creation and never calls a vendor upload or submit API. vivo is the best first real integration because its API provides both sandbox and production environments, supports unified APK and 32/64 APK flows, and already has typed RemoteDataSource methods in the codebase.

## What Changes

- Add a real vivo update publish flow that can upload APK artifacts and submit update requests through vivo APIs.
- Add vivo environment selection so a channel can explicitly use sandbox or production credentials and endpoints.
- Extend publish task execution from local validation-only records to channel-scoped execution with download, validation, upload, submit, and result information.
- Surface vivo publish progress in the existing publish-process page, including concise summaries and detailed logs.
- Keep the scope limited to vivo for the first real publish integration; other markets remain prepared/validation-only.

## Capabilities

### New Capabilities
- `vivo-api-environment`: Select and persist vivo sandbox or production environment per channel, use the matching base URL, and prevent ambiguous credential usage.
- `vivo-publish-flow`: Execute vivo unified APK and 32/64 APK update flows from prepared artifacts, record upload identifiers, submit results, task status, and user-visible logs.

### Modified Capabilities

None.

## Impact

- Domain model: publish task status, publish stage/result fields, vivo environment field.
- UI: vivo channel configuration, publish target validation, publish-process summaries/logs.
- Data layer: vivo RemoteDataSource endpoint selection and publish method orchestration.
- Tests: domain use cases, vivo publisher behavior, RemoteDataSource request construction, UI-state-level publish task transitions.
