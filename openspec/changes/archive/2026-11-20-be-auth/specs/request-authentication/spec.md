# Spec Delta

## MODIFIED Requirements

### Requirement: Public routes are listed explicitly
Every path under `/api/v1` SHALL require an access token unless it is on an explicit list of public routes, each given by method and exact path. The public routes are `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh` and `POST /api/v1/auth/logout`. Paths outside `/api/v1` SHALL NOT require a token. On public routes and on paths outside `/api/v1`, the backend SHALL ignore the `Authorization` header: a missing, invalid, expired or non-bearer value SHALL NOT produce 401. Every endpoint mapping SHALL be under `/api/v1/`.

#### Scenario: Unknown API path without a token
- **WHEN** a client without a token calls `GET /api/v1/does-not-exist`
- **THEN** the response is 401 `UNAUTHENTICATED`

#### Scenario: Unknown API path with a token
- **WHEN** a client with a valid token calls `GET /api/v1/does-not-exist`
- **THEN** the response is 404 `ROUTE_NOT_FOUND`

#### Scenario: Operational path without a token
- **WHEN** a client without a token calls `GET /actuator/env`
- **THEN** the response is 404, not 401

#### Scenario: Health with an expired token
- **WHEN** a client calls `GET /actuator/health` with an expired access token
- **THEN** the response is 200 with `status` `UP`

#### Scenario: No endpoint outside the API base path
- **WHEN** the application's request mappings are listed
- **THEN** every controller mapping starts with `/api/v1/`

#### Scenario: Login without a token
- **WHEN** a client without an `Authorization` header calls `POST /api/v1/auth/login` with valid credentials
- **THEN** the response is 200

#### Scenario: Login with a broken token
- **WHEN** a client calls `POST /api/v1/auth/login` with valid credentials and the header `Authorization: Bearer garbage`
- **THEN** the response is 200

#### Scenario: Other method on an auth path
- **WHEN** a client without a token calls `GET /api/v1/auth/login`
- **THEN** the response is 401 `UNAUTHENTICATED`

#### Scenario: Auth operations in the document
- **WHEN** the OpenAPI snapshot is inspected
- **THEN** the four auth operations have no bearer security requirement and document no 401 `UNAUTHENTICATED`
