## 1. Domain Model And Configuration

- [ ] 1.1 Add a typed vivo environment model with sandbox and production values.
- [ ] 1.2 Persist the vivo environment in channel `extraFields` with production as the default for existing channels.
- [ ] 1.3 Add UI form support for selecting vivo environment when creating or editing a vivo channel.
- [ ] 1.4 Show guidance that vivo sandbox and production credentials are separate.

## 2. Vivo Endpoint Routing

- [ ] 2.1 Replace the hard-coded vivo API base URL with a selected endpoint parameter or typed environment resolver.
- [ ] 2.2 Route vivo app-info queries through the channel-selected environment.
- [ ] 2.3 Route vivo upload, submit, and task-status calls through the channel-selected environment.
- [ ] 2.4 Add request-construction tests for sandbox and production vivo endpoints.

## 3. Publish Contract And Task State

- [ ] 3.1 Extend the publisher boundary with a publish method that can return structured stage logs and vendor identifiers.
- [ ] 3.2 Extend publish task data to represent download, validation, upload, submit, accepted/submitted, and failed stages.
- [ ] 3.3 Add typed vivo update submit options for `onlineType` and `compatibleDevice`; fail before submit when required options are missing.
- [ ] 3.4 Preserve validation-only task behavior for non-vivo markets until their publish flows are implemented.
- [ ] 3.5 Ensure one channel failure does not prevent other selected channel tasks from being visible.

## 4. Vivo Upload And Submit Flow

- [ ] 4.1 Implement vivo unified APK upload through `app.upload.apk.app` as multipart form data with a real APK file part.
- [ ] 4.2 Record vivo unified APK upload `serialnumber`, version, and file hash details in the publish task.
- [ ] 4.3 Implement vivo 32-bit and 64-bit APK upload through `app.upload.apk.app.32` and `app.upload.apk.app.64` as multipart form data with real APK file parts.
- [ ] 4.4 Record both split APK upload serial numbers in the publish task.
- [ ] 4.5 Prove the vivo app exists in the selected environment before upload or submit.
- [ ] 4.6 Implement vivo update submission through `app.sync.update.app` for unified APK with typed required fields.
- [ ] 4.7 Implement vivo split update submission through `app.sync.update.subpackage.app` with typed required fields.
- [ ] 4.8 Fail with a clear message when the app does not exist or requires first-time vivo creation.

## 5. Vivo Task Status

- [ ] 5.1 Add a user-triggered refresh action for vivo task status.
- [ ] 5.2 Call vivo `app.query.task.status` with the selected environment, `packageName`, and update `packetType=0`.
- [ ] 5.3 Map vivo task status success and failure into publish task status and visible details.
- [ ] 5.4 Include vivo environment context in task-status errors and detailed logs.

## 6. Publish Process UI

- [ ] 6.1 Display the vivo target environment in the publish-process channel card.
- [ ] 6.2 Show vivo upload serial numbers and submit result in concise publish information.
- [ ] 6.3 Show vivo method names, environment, vendor identifiers, and errors when detailed logs are enabled.
- [ ] 6.4 Keep non-vivo channel cards clearly labeled as validation-only.
- [ ] 6.5 Display the vivo target environment before task creation and require explicit confirmation before production upload/submit.

## 7. Verification

- [ ] 7.1 Add unit tests for vivo environment parsing, defaulting, and persistence.
- [ ] 7.2 Add RemoteDataSource contract tests for vivo sandbox and production endpoint selection.
- [ ] 7.3 Add RemoteDataSource contract tests proving vivo upload requests are multipart, include a real file part, and exclude `file` from signing.
- [ ] 7.4 Add VivoMarketPublisher tests for unified APK upload success and upload failure.
- [ ] 7.5 Add VivoMarketPublisher tests for 32/64 APK upload success and partial upload failure.
- [ ] 7.6 Add VivoMarketPublisher tests for missing update submit fields and unsupported vivo creation.
- [ ] 7.7 Add publish orchestration tests showing vivo failure does not hide non-vivo tasks.
- [ ] 7.8 Run `./gradlew :composeApp:compileKotlinJvm --no-configuration-cache`.
- [ ] 7.9 Run targeted JVM tests for vivo RemoteDataSource, VivoMarketPublisher, and publish use cases.
