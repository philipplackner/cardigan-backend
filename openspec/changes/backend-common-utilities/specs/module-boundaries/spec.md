# Spec Delta

## MODIFIED Requirements

### Requirement: Module layer packages exist from the start
`:user` and `:board` SHALL each contain the layer packages `api`, `service`, `domain` and `infra` under their own root package. `:common` SHALL contain its `api`, `domain` and `infra` packages. A package with no real code SHALL hold no placeholder classes.

#### Scenario: Fresh clone
- **WHEN** the repository is cloned
- **THEN** the `:user` and `:board` layer packages exist on disk under `com.plcoding.cardigan.user` and `com.plcoding.cardigan.board`, and the `:common` layer packages exist under `com.plcoding.cardigan.common`

#### Scenario: No placeholder classes
- **WHEN** the `:board` main source folder is inspected
- **THEN** it contains no source files
