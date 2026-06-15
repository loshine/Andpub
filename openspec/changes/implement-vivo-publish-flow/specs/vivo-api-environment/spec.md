## ADDED Requirements

### Requirement: Vivo channel environment selection
The system SHALL allow each vivo channel to select exactly one API environment: sandbox or production.

#### Scenario: Default environment for existing vivo channel
- **WHEN** a vivo channel has no saved environment value
- **THEN** the system SHALL treat the channel as production

#### Scenario: Select sandbox environment
- **WHEN** the user edits a vivo channel and selects sandbox
- **THEN** the system SHALL persist the sandbox environment on that channel

#### Scenario: Select production environment
- **WHEN** the user edits a vivo channel and selects production
- **THEN** the system SHALL persist the production environment on that channel

### Requirement: Vivo API endpoint routing
The system SHALL route vivo API calls to the endpoint matching the channel environment.

#### Scenario: Sandbox endpoint routing
- **WHEN** a vivo channel is configured for sandbox
- **THEN** vivo API requests SHALL use `https://sandbox-developer-api.vivo.com.cn/router/rest`

#### Scenario: Production endpoint routing
- **WHEN** a vivo channel is configured for production
- **THEN** vivo API requests SHALL use `https://developer-api.vivo.com.cn/router/rest`

### Requirement: Vivo environment visibility
The system SHALL show the selected vivo environment anywhere a user needs to understand or verify the target environment for a publish operation.

#### Scenario: Channel form displays environment
- **WHEN** the user opens the editor for a vivo channel
- **THEN** the selected environment SHALL be visible and editable

#### Scenario: Publish process displays environment
- **WHEN** a vivo publish task is created
- **THEN** the publish process view SHALL display whether the task targets sandbox or production

#### Scenario: Publish form displays environment before task creation
- **WHEN** the user selects a vivo channel in the publish tab
- **THEN** the publish form SHALL display whether that channel targets sandbox or production before the task is created

#### Scenario: Production publish requires explicit confirmation
- **WHEN** the user creates a vivo publish task for a production channel
- **THEN** the system SHALL require an explicit production confirmation before calling vivo upload or submit APIs

### Requirement: Vivo credential isolation warning
The system SHALL make it clear that vivo sandbox and production credentials are separate and must not be mixed.

#### Scenario: Environment configuration guidance
- **WHEN** the user configures a vivo channel environment
- **THEN** the system SHALL display guidance that sandbox and production `access_key` / `access_secret` are separate

#### Scenario: Vivo API failure includes environment context
- **WHEN** a vivo API request fails
- **THEN** the visible error or detailed log SHALL include the environment used for the request
