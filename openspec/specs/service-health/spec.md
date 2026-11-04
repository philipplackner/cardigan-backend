# service-health Specification

## Purpose

Defines the public endpoint that reports whether the service process is alive, so operators and later deployment tooling can check it without credentials or knowledge of its dependencies.

## Requirements

### Requirement: Public liveness endpoint
The service SHALL expose `GET /actuator/health` without authentication. While the process is running, it SHALL respond with HTTP 200 and a body whose `status` is `UP`.

#### Scenario: Service running
- **WHEN** a client with no credentials calls `GET /actuator/health` on a running service
- **THEN** the response is HTTP 200 with `status` equal to `UP`

### Requirement: Health does not depend on the database
The health response SHALL NOT depend on the state of the database or other external systems.

#### Scenario: Database goes down after startup
- **WHEN** the database becomes unreachable after the service has started and a client calls `GET /actuator/health`
- **THEN** the response is still HTTP 200 with `status` equal to `UP`

### Requirement: Health reveals no details
The health response SHALL contain only the overall status, and SHALL NOT list components or connection details.

#### Scenario: Response body
- **WHEN** a client calls `GET /actuator/health`
- **THEN** the response body contains `status` and no `components` or `details`

### Requirement: Only health is exposed operationally
The service SHALL NOT expose any other operational endpoint over HTTP.

#### Scenario: Other operational path
- **WHEN** a client calls `GET /actuator`, `GET /actuator/env` or `GET /actuator/beans`
- **THEN** the response is HTTP 404

### Requirement: Health is not part of the client API
The health endpoint SHALL be served at `/actuator/health`, outside the `/api/v1` base path defined by `api-conventions` ("All endpoints use a versioned base path").

#### Scenario: Path
- **WHEN** a client calls `GET /api/v1/actuator/health`
- **THEN** the response is HTTP 404
