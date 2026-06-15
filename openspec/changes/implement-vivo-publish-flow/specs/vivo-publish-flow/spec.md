## ADDED Requirements

### Requirement: Vivo update publish execution
The system SHALL execute real vivo update publish operations for selected vivo channels after artifact preparation succeeds and the target app is known to exist in the selected vivo environment.

#### Scenario: Unified APK publish starts after validation
- **WHEN** a selected vivo channel has a validated unified APK artifact and the vivo app is known to exist
- **THEN** the system SHALL invoke the vivo unified APK publish flow for that channel

#### Scenario: Split APK publish starts after validation
- **WHEN** a selected vivo channel has validated 32-bit and 64-bit APK artifacts and the vivo app is known to exist
- **THEN** the system SHALL invoke the vivo split APK publish flow for that channel

#### Scenario: Invalid artifact blocks publish
- **WHEN** vivo artifact preparation or validation fails
- **THEN** the system SHALL NOT call vivo upload or submit APIs for that task

#### Scenario: Unknown vivo app existence blocks publish
- **WHEN** the system cannot prove the app exists in the selected vivo environment
- **THEN** the publish task SHALL fail before upload or submit with a clear message

### Requirement: Vivo unified APK upload
The system SHALL upload a validated unified APK to vivo and record the returned upload identifier.

#### Scenario: Upload unified APK file
- **WHEN** the vivo unified APK publish flow reaches the upload stage
- **THEN** the system SHALL call vivo `app.upload.apk.app` with the application package name, APK MD5, and APK file content as multipart form data

#### Scenario: Record unified APK serial number
- **WHEN** vivo returns an upload `serialnumber`
- **THEN** the publish task SHALL record the serial number in user-visible publish details

#### Scenario: Unified APK upload failure
- **WHEN** vivo unified APK upload fails
- **THEN** the publish task SHALL become failed and display the vivo error message

### Requirement: Vivo split APK upload
The system SHALL upload validated 32-bit and 64-bit APK artifacts to vivo and record both returned upload identifiers.

#### Scenario: Upload 32-bit and 64-bit APK files
- **WHEN** the vivo split APK publish flow reaches the upload stage
- **THEN** the system SHALL call vivo `app.upload.apk.app.32` for the 32-bit APK and `app.upload.apk.app.64` for the 64-bit APK with each APK MD5 and file content as multipart form data

#### Scenario: Record split APK serial numbers
- **WHEN** vivo returns upload serial numbers for both split APK files
- **THEN** the publish task SHALL record both serial numbers in user-visible publish details

#### Scenario: One split upload fails
- **WHEN** either 32-bit or 64-bit vivo APK upload fails
- **THEN** the publish task SHALL become failed and SHALL NOT submit the split publish request

### Requirement: Vivo submit operation
The system SHALL submit uploaded vivo artifact identifiers through the appropriate vivo update API using typed update submit fields.

#### Scenario: Submit unified APK update
- **WHEN** the vivo unified APK upload succeeds and the target app is known to exist
- **THEN** the system SHALL call vivo `app.sync.update.app` with `packageName`, `versionCode`, `apk`, `fileMd5`, `onlineType`, and `compatibleDevice`

#### Scenario: Submit split APK update
- **WHEN** the vivo split APK uploads succeed and the target app is known to exist
- **THEN** the system SHALL call vivo `app.sync.update.subpackage.app` with `packageName`, `apk32`, `apk64`, `onlineType`, and `compatibleDevice`

#### Scenario: Required submit fields are missing
- **WHEN** a required vivo update submit field is missing
- **THEN** the publish task SHALL fail before submit and display the missing field names

#### Scenario: Vivo app creation is not supported
- **WHEN** a vivo channel requires first-time app creation
- **THEN** the publish task SHALL fail with a clear message that vivo creation is not supported by this change

### Requirement: Vivo publish task status and logs
The system SHALL expose vivo publish progress through task status, concise publish information, and detailed logs.

#### Scenario: Publish progress stages
- **WHEN** a vivo publish task runs
- **THEN** the task SHALL record progress through download, validation, upload, submit, and result stages

#### Scenario: Concise process view
- **WHEN** the user views the publish process with detailed logs disabled
- **THEN** the system SHALL show download, validation, and publish information grouped by vivo channel

#### Scenario: Detailed process view
- **WHEN** the user enables detailed logs
- **THEN** the system SHALL show vivo request-stage logs including environment, upload method names, vendor identifiers, and error messages

### Requirement: Vivo task status query
The system SHALL support querying vivo task status for update publish operations that return or imply asynchronous processing.

#### Scenario: Query task status
- **WHEN** a vivo update publish operation has an associated package name
- **THEN** the system SHALL allow querying vivo `app.query.task.status` with `packageName` and `packetType=0` for update tasks

#### Scenario: Task status success
- **WHEN** vivo task status indicates success
- **THEN** the publish task SHALL display the success state and relevant vendor details

#### Scenario: Task status failure
- **WHEN** vivo task status indicates failure
- **THEN** the publish task SHALL display the failure state and vivo failure reason

### Requirement: Vivo channel isolation in batch publish
The system SHALL isolate vivo publish failures from other selected channel tasks.

#### Scenario: Vivo fails while other channels exist
- **WHEN** a vivo publish task fails during a batch publish
- **THEN** the system SHALL keep other channel tasks visible and SHALL NOT remove their prepared results

#### Scenario: Non-vivo channels remain validation-only
- **WHEN** a batch publish includes non-vivo channels
- **THEN** non-vivo channels SHALL continue to produce validation-only publish tasks until their market publish flows are implemented
