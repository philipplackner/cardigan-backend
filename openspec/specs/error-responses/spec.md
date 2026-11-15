# error-responses Specification

## Purpose

Defines the backend-specific details of error responses on top of the shared spec `api-errors`: the `type` and `title` values, which fields are sent, and that every error the service produces, wherever it is raised, uses the same body.

## Requirements

### Requirement: Type is a URN per code
The `type` of every error body SHALL be `urn:cardigan:problem:` followed by the top-level `code` in lowercase with underscores replaced by hyphens. Each code SHALL always have the same `type`.

#### Scenario: Not found
- **WHEN** a response has the code `NOT_FOUND`
- **THEN** its `type` is `urn:cardigan:problem:not-found`

#### Scenario: Validation failed
- **WHEN** a response has the code `VALIDATION_FAILED`
- **THEN** its `type` is `urn:cardigan:problem:validation-failed`

### Requirement: Title is fixed per code
The `title` of every error body SHALL be a fixed English text chosen per top-level code, the same for every response with that code.

#### Scenario: Same code, different causes
- **WHEN** one request has a missing token, one an expired token, and one a valid token for a user with no record (`GET /api/v1/users/me`)
- **THEN** all three responses have the same `title`

### Requirement: Error bodies carry exactly the documented fields
Every error body SHALL contain `type`, `title`, `status`, `detail`, `code` and `fieldErrors`, and SHALL NOT contain `instance` or any other field. Field error entries SHALL contain `field`, `code`, and `min` and `max` only where `api-errors` ("Limit errors carry the limit") requires them.

#### Scenario: Not found body
- **WHEN** a client calls `GET /api/v1/does-not-exist` with a valid token
- **THEN** the body has exactly the fields `type`, `title`, `status`, `detail`, `code` and `fieldErrors`

#### Scenario: Field error without limits
- **WHEN** a field error has the code `REQUIRED`
- **THEN** its entry has only `field` and `code`

#### Scenario: Too long carries only max
- **WHEN** a field error has the code `TOO_LONG`
- **THEN** its entry has exactly `field`, `code` and `max`

### Requirement: Errors raised outside controllers use the same body
Errors produced before a request reaches an endpoint SHALL use the same content type and body as errors from endpoints. This covers exactly: authentication, unknown routes and methods, paths rejected by the request firewall, body size, media type, unparseable bodies, and unexpected failures in filters. Requests that the servlet container rejects before the application sees them (malformed request line or headers) are out of scope.

#### Scenario: Missing token
- **WHEN** a request without a token reaches a protected path
- **THEN** the response has content type `application/problem+json`, the code `UNAUTHENTICATED`, and exactly the fields `type`, `title`, `status`, `detail`, `code` and an empty `fieldErrors`

#### Scenario: Wrong method
- **WHEN** an authenticated client sends `DELETE /api/v1/users/me`
- **THEN** the response is 404 `ROUTE_NOT_FOUND` with a problem-details body

#### Scenario: Implicit HEAD and OPTIONS
- **WHEN** an authenticated client sends `OPTIONS /api/v1/users/me` or `HEAD /api/v1/users/me`
- **THEN** the response is 404 `ROUTE_NOT_FOUND`, because HEAD and OPTIONS count as wrong methods unless an endpoint declares them

#### Scenario: Oversized body with Content-Length
- **WHEN** an authenticated client sends a body with `Content-Length` 307200 to any `/api/v1` path
- **THEN** the response is 413 `PAYLOAD_TOO_LARGE` with content type `application/problem+json` and exactly the fields `type`, `title`, `status`, `detail`, `code` and an empty `fieldErrors`

#### Scenario: Oversized chunked JSON body
- **WHEN** an authenticated client sends a 300 KB chunked body with content type `application/json` to an endpoint that reads a JSON body
- **THEN** the response is 413 `PAYLOAD_TOO_LARGE` with a problem-details body

#### Scenario: Rejected path
- **WHEN** a client calls `GET /api/v1/users/me;x=1`
- **THEN** the response is 404 `ROUTE_NOT_FOUND` with content type `application/problem+json`

#### Scenario: Failure in a filter
- **WHEN** a filter that runs before the endpoint throws an unexpected exception
- **THEN** the response is 500 `INTERNAL_ERROR` with content type `application/problem+json` and exactly the documented fields

### Requirement: The Accept header does not change the response format
The backend SHALL ignore the request's `Accept` header when choosing the response format. Successful responses with a body SHALL be JSON, and errors SHALL be `application/problem+json`. The backend SHALL NOT respond with 406.

#### Scenario: Plain text requested
- **WHEN** an authenticated client calls `GET /api/v1/users/me` with `Accept: text/plain`
- **THEN** the response is 200 with a JSON body

#### Scenario: Error with plain text requested
- **WHEN** a client without a token calls `GET /api/v1/users/me` with `Accept: text/html`
- **THEN** the response is 401 with content type `application/problem+json`

### Requirement: Error details are fixed per code
The `detail` of every error body SHALL be a fixed English text chosen per top-level code. It SHALL NOT be taken from framework or domain exception messages and SHALL NOT contain any part of the request. A request body that cannot be parsed, or that has a value of the wrong JSON type, SHALL produce 400 `MALFORMED_REQUEST`.

#### Scenario: Number expected
- **WHEN** a body has `"position": "zq9x"` where a number is expected
- **THEN** the response is 400 `MALFORMED_REQUEST`, and `detail` contains none of `position`, `zq9x`, `Exception`, `jackson`, `kotlin.` or `java.`

#### Scenario: Detail is fixed
- **WHEN** one request has invalid JSON and another has a value of the wrong JSON type
- **THEN** both responses have the same `detail`

#### Scenario: Unknown path is not echoed
- **WHEN** a client with a valid token calls `GET /api/v1/zz-marker-zz`
- **THEN** the response is 404 `ROUTE_NOT_FOUND`, and neither `detail` nor `title` contains `zz-marker-zz`

#### Scenario: Media type is not echoed
- **WHEN** an authenticated client sends a body with `Content-Type: text/zz-marker` to an endpoint that reads a JSON body
- **THEN** the response is 415 `UNSUPPORTED_MEDIA_TYPE`, and `detail` does not contain `zz-marker`

### Requirement: Unconvertible values of the right JSON type are field errors
A JSON string that is not a valid UUID SHALL produce the field error `INVALID_FORMAT`, and a JSON string that is not an allowed enum value SHALL produce `INVALID_VALUE`. An integer of any size SHALL be accepted by parsing and checked by validation. These field errors SHALL be reported together with every other broken field and SHALL NOT produce `MALFORMED_REQUEST`.

#### Scenario: Array item not a UUID
- **WHEN** a body has `"labelIds": ["<uuid>", "<uuid>", "nope"]`
- **THEN** the response is 400 `VALIDATION_FAILED` with the field error `labelIds[2]` `INVALID_FORMAT`

#### Scenario: Unknown enum value
- **WHEN** a body has a string that is not one of the field's enum values
- **THEN** the response is 400 `VALIDATION_FAILED` with that field and `INVALID_VALUE`

#### Scenario: Huge integer
- **WHEN** a body has `"position": 1099511627776` where the allowed range is smaller
- **THEN** the response is 400 `VALIDATION_FAILED` with `position` `OUT_OF_RANGE`

### Requirement: Unexpected failures are logged, not returned
A failure not covered by another code SHALL produce 500 `INTERNAL_ERROR` with a fixed `detail`, and the backend SHALL log the failure with its stack trace.

#### Scenario: Unhandled exception
- **WHEN** an endpoint throws an unexpected exception
- **THEN** the response is 500 `INTERNAL_ERROR` with the same `title` and `detail` as every other internal error, and the log contains the stack trace

### Requirement: Missing and null fields are validation errors
A request body field that the endpoint requires and that is missing or `null` SHALL produce a field error with the code `REQUIRED`, reported together with every other broken field, and SHALL NOT produce `MALFORMED_REQUEST`.

#### Scenario: Two missing fields
- **WHEN** an endpoint requires the fields `name` and `key` and receives `{}`
- **THEN** the response is 400 `VALIDATION_FAILED` with field errors for both `name` and `key` with the code `REQUIRED`

#### Scenario: Explicit null
- **WHEN** an endpoint requiring `name` receives `{"name": null}`
- **THEN** the response is 400 `VALIDATION_FAILED` with `name` `REQUIRED`

#### Scenario: Missing field and invalid field
- **WHEN** an endpoint receives a body without `name` and with an over-limit `key`
- **THEN** `fieldErrors` contains `name` with `REQUIRED` and `key` with `TOO_LONG`

### Requirement: Parsing comes before references, and references before field validation
For an endpoint that names resources in its path or body, an unparseable body SHALL be reported before a missing or malformed path reference, and a missing reference SHALL be reported before field validation errors, as `api-errors` ("Error precedence") requires.

#### Scenario: Malformed path ID and unparseable body
- **WHEN** a request to a path with the identifier `abc` has an unparseable body
- **THEN** the response is 400 `MALFORMED_REQUEST`

#### Scenario: Missing reference and invalid field
- **WHEN** a request names a resource that does not exist and has a field that fails validation
- **THEN** the response is 404 `NOT_FOUND`
