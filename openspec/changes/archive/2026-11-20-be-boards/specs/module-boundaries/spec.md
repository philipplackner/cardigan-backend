# Spec Delta

## MODIFIED Requirements

### Requirement: Module layer packages exist from the start
`:user` and `:board` SHALL each contain the layer packages `api`, `service`, `domain` and `infra` under their own root package. `:common` SHALL contain its `api`, `domain` and `infra` packages. A package with no real code SHALL hold no placeholder classes.

#### Scenario: Fresh clone
- **WHEN** the repository is cloned
- **THEN** the `:user` and `:board` layer packages exist on disk under `com.plcoding.cardigan.user` and `com.plcoding.cardigan.board`, and the `:common` layer packages exist under `com.plcoding.cardigan.common`

#### Scenario: No placeholder classes
- **WHEN** the layer packages of `:user`, `:board` and `:common` in the main source folders are inspected
- **THEN** each one holds either at least one Kotlin source file at any depth and no `.gitkeep`, or a `.gitkeep` and no source file
