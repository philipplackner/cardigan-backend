# Spec Delta

## Purpose

Defines which modules the backend is made of, which dependencies between them are allowed, and how each module's data and code are separated, so features can be added without coupling modules together.

## ADDED Requirements

### Requirement: The backend has a fixed set of modules
The build SHALL consist of exactly the modules `:app`, `:common`, `:user` and `:board`, built together into one deployable service. Boards, columns, cards, labels and import/export SHALL belong to `:board`.

#### Scenario: Listing the modules
- **WHEN** the build's projects are listed
- **THEN** the application modules are exactly `:app`, `:common`, `:user` and `:board`

#### Scenario: One deployable
- **WHEN** the whole build is packaged
- **THEN** only `:app` produces a runnable service artifact

### Requirement: Business modules are independent
`:user` and `:board` SHALL depend only on `:common` among the project's modules, and SHALL NOT depend on each other. `:common` SHALL NOT depend on any other project module. Only `:app` SHALL depend on every module.

#### Scenario: Business module dependencies
- **WHEN** the project dependencies of `:user` and `:board` are inspected
- **THEN** each one lists `:common` and neither lists the other

#### Scenario: Common dependencies
- **WHEN** the project dependencies of `:common` are inspected
- **THEN** it lists no other project module

#### Scenario: App dependencies
- **WHEN** the project dependencies of `:app` are inspected
- **THEN** it lists `:common`, `:user` and `:board`

### Requirement: Each data-owning module has its own schema
`:user` SHALL own the database schema `user_service` and `:board` SHALL own `board_service`. Each SHALL have its own migration location, even while that location holds no migrations.

#### Scenario: Configured schemas
- **WHEN** the migration configuration is inspected
- **THEN** it lists exactly the schemas `user_service` and `board_service`, and one migration location per module

### Requirement: Module layer packages exist from the start
`:user` and `:board` SHALL each contain the layer packages `api`, `service`, `domain` and `infra` under their own root package, with no placeholder classes. `:common` SHALL contain its `api`, `domain` and `infra` packages.

#### Scenario: Fresh clone
- **WHEN** the repository is cloned
- **THEN** the `:user` and `:board` layer packages exist on disk under `com.plcoding.cardigan.user` and `com.plcoding.cardigan.board`, and the `:common` layer packages exist under `com.plcoding.cardigan.common`

#### Scenario: No placeholder classes
- **WHEN** the `:user`, `:board` and `:common` main source folders are inspected
- **THEN** they contain no source files
