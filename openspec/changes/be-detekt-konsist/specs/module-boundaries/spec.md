# Spec Delta

## MODIFIED Requirements

### Requirement: The backend has a fixed set of modules
The build SHALL consist of exactly the application modules `:app`, `:common`, `:user` and `:board`, built together into one deployable service, plus the tooling module `:tooling`, which is not part of the service. Boards, columns, cards, labels and import/export SHALL belong to `:board`.

#### Scenario: Listing the modules
- **WHEN** the build's projects are listed
- **THEN** the modules are exactly `:app`, `:common`, `:user`, `:board` and `:tooling`

#### Scenario: One deployable
- **WHEN** the whole build is packaged
- **THEN** only `:app` produces a runnable service artifact, and it contains no class from `:tooling`

### Requirement: Business modules are independent
`:user` and `:board` SHALL depend only on `:common` among the project's modules, and SHALL NOT depend on each other. `:common` SHALL NOT depend on any other project module. Only `:app` SHALL depend on every application module. `:tooling` SHALL NOT depend on any project module, and no project module SHALL depend on `:tooling` except to load its detekt rules.

#### Scenario: Business module dependencies
- **WHEN** the project dependencies of `:user` and `:board` are inspected
- **THEN** each one lists `:common` and neither lists the other

#### Scenario: Common dependencies
- **WHEN** the project dependencies of `:common` are inspected
- **THEN** it lists no other project module

#### Scenario: App dependencies
- **WHEN** the project dependencies of `:app` are inspected
- **THEN** it lists `:common`, `:user` and `:board`, and does not list `:tooling` in any compile, runtime or test configuration

#### Scenario: Tooling dependencies
- **WHEN** the project dependencies of `:tooling` are inspected
- **THEN** it lists no other project module
