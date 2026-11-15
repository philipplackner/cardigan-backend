# Spec Delta

## Purpose

Defines how the backend publishes its generated OpenAPI document to client repos as a committed JSON snapshot, how drift is caught, and the naming and schema rules that keep generated client code type-safe and stable.

## ADDED Requirements

### Requirement: The spec is a committed JSON snapshot
The OpenAPI document generated from the backend code SHALL be committed as `app/src/test/resources/openapi/openapi.json`. Client repos SHALL take the API contract from this file. Test-only endpoints SHALL NOT appear in it.

#### Scenario: File present
- **WHEN** the repository is checked out at any commit after this change
- **THEN** `app/src/test/resources/openapi/openapi.json` exists and is a valid OpenAPI document in JSON

#### Scenario: Test endpoints stay out of the snapshot
- **WHEN** the snapshot is inspected
- **THEN** no path starts with `/api/v1/test`

### Requirement: The spec uses OpenAPI 3.0.1
The snapshot SHALL declare `"openapi": "3.0.1"` and SHALL use only OpenAPI 3.0 constructs, such as the `nullable` flag instead of `null` in a type list.

#### Scenario: Version field
- **WHEN** the snapshot is inspected
- **THEN** its `openapi` field is `3.0.1` and no schema has a `type` that is a list

### Requirement: The build fails on drift
`./gradlew build` SHALL fail when the generated document differs from the committed snapshot, and the failure SHALL say how to update it.

#### Scenario: Endpoint added without snapshot update
- **WHEN** a controller gains an endpoint and the snapshot is not updated
- **THEN** `./gradlew build` fails and names the snapshot file and the update task

#### Scenario: No change
- **WHEN** neither the API nor the snapshot changed
- **THEN** the snapshot check passes

### Requirement: The snapshot is updated by a task
A Gradle task SHALL regenerate the snapshot file from the code. The output SHALL be deterministic: generating twice from the same code SHALL produce byte-identical files. The document SHALL declare exactly one server with the URL `/`.

#### Scenario: Update then build
- **WHEN** a developer runs the update task after changing an endpoint and then runs `./gradlew build`
- **THEN** the snapshot check passes and the diff shows only the endpoint change

#### Scenario: Repeated generation
- **WHEN** the update task runs twice without code changes
- **THEN** the file is unchanged by the second run, and no value depends on the host, port or time of generation

### Requirement: No API docs at runtime
The running service SHALL NOT serve the OpenAPI document or an API documentation UI in any profile.

#### Scenario: Docs path
- **WHEN** a client calls `GET /v3/api-docs` or `GET /swagger-ui/index.html` on a service running with any profile
- **THEN** the response is 404

### Requirement: Operations have explicit stable names
Every operation SHALL have an explicit camelCase `operationId` made of a verb and the resource, such as `getCurrentUser` or `listBoards`, and exactly one tag, the plural resource name in PascalCase, such as `Users` or `Boards`. `operationId`s SHALL be unique.

#### Scenario: Current user operation
- **WHEN** the snapshot is inspected
- **THEN** `GET /api/v1/users/me` has the `operationId` `getCurrentUser` and the single tag `Users`

#### Scenario: Every operation
- **WHEN** the snapshot is inspected
- **THEN** every `operationId` matches `^[a-z][a-zA-Z0-9]*$` and is unique, and every operation has exactly one tag matching `^[A-Z][a-zA-Z]*s$`

### Requirement: Request fields are documented as required or optional, never nullable
Every request body property that the endpoint requires SHALL be listed as `required` and SHALL NOT be marked nullable in the document, even though the backend accepts its absence to report `REQUIRED`. Optional request properties SHALL NOT be listed as `required` and SHALL NOT be marked nullable either, because `null` is rejected with `REQUIRED` (`api-conventions`, "Null and empty updates"). Clients omit an optional property instead of sending `null`.

#### Scenario: Required and optional request fields
- **WHEN** the document is generated for a test request DTO with a required property `name` and an optional property `note`
- **THEN** `name` is under `required` and not nullable, and `note` is not under `required` and not nullable

### Requirement: Response fields are required and non-null
Every response body property SHALL be listed as `required` and SHALL NOT be marked nullable, except where `api-conventions` ("Documented fields are always present") allows it.

#### Scenario: Current user response
- **WHEN** the snapshot is inspected
- **THEN** the current user schema lists `id`, `email` and `createdAt` as required and none is nullable

### Requirement: Identifiers and timestamps have formats
Every UUID property and path parameter SHALL be documented as `type: string, format: uuid`, and every timestamp as `type: string, format: date-time`.

#### Scenario: Current user formats
- **WHEN** the snapshot is inspected
- **THEN** `id` has `format: uuid` and `createdAt` has `format: date-time`

### Requirement: Error responses are documented per operation
Every operation SHALL document 500 `INTERNAL_ERROR`, 401 `UNAUTHENTICATED` when it requires a token, and every status named in its `@ApiErrors`, each with the problem-details schema and its content type `application/problem+json`. The schema SHALL name the fields of `error-responses`. Each documented status SHALL list in its description the codes the operation can return with it.

#### Scenario: Current user errors
- **WHEN** the snapshot is inspected
- **THEN** `getCurrentUser` documents exactly the statuses 200, 401 and 500, with 401 using the problem-details schema and the code `UNAUTHENTICATED`, and 500 the code `INTERNAL_ERROR`

#### Scenario: Error schema
- **WHEN** the problem-details schema is inspected
- **THEN** it has the required fields `type`, `title`, `status`, `detail`, `code` and `fieldErrors`, and each field error has `field`, `code`, and the optional integers `min` and `max`

### Requirement: Transport errors are documented only where a body is read
Statuses caused only by malformed transport (413 `PAYLOAD_TOO_LARGE`, 415 `UNSUPPORTED_MEDIA_TYPE`, 400 `MALFORMED_REQUEST`) SHALL be documented on every operation with a request body and on no other operation.

#### Scenario: Operation without a body
- **WHEN** the snapshot is inspected
- **THEN** `getCurrentUser` documents none of 400, 413 and 415

### Requirement: Security is documented
The document SHALL declare an HTTP bearer security scheme and SHALL apply it to every operation that requires an access token.

#### Scenario: Protected operation
- **WHEN** the snapshot is inspected
- **THEN** `getCurrentUser` requires the bearer security scheme
