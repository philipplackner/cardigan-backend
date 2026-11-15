# Spec Delta

## MODIFIED Requirements

### Requirement: Health is not part of the client API
The health endpoint SHALL be served at `/actuator/health`, outside the `/api/v1` base path defined by `api-conventions` ("All endpoints use a versioned base path").

#### Scenario: Path
- **WHEN** a client with a valid access token calls `GET /api/v1/actuator/health`
- **THEN** the response is HTTP 404 with the code `ROUTE_NOT_FOUND`

#### Scenario: Path without a token
- **WHEN** a client without credentials calls `GET /api/v1/actuator/health`
- **THEN** the response is HTTP 401 with the code `UNAUTHENTICATED`, as for every unknown path under `/api/v1`
