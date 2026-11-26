# Spec Delta

## MODIFIED Requirements

### Requirement: Non-logic classes are not mutated
Mutation testing SHALL NOT mutate the application main class and its top-level `main`, or classes annotated with `@SpringBootApplication`, `@Configuration`, `@ConfigurationProperties`, `@Repository` or `@Entity`. New classes of those kinds SHALL be excluded without changing the mutation setup.

#### Scenario: Configuration classes are excluded
- **WHEN** the report is generated
- **THEN** it contains no mutant in a class annotated with `@SpringBootApplication`, `@Configuration` or `@ConfigurationProperties`

#### Scenario: A new configuration class
- **WHEN** a new `@Configuration` class is added to an opted-in module
- **THEN** the next report has no mutant in it, with no change to the mutation setup

#### Scenario: Persistence adapters and entities are excluded
- **WHEN** the report is generated
- **THEN** it contains no mutant in a class annotated with `@Repository` or `@Entity`

#### Scenario: A new entity or adapter
- **WHEN** a new `@Entity` class or `@Repository` class is added to an opted-in module
- **THEN** the next report has no mutant in it, with no change to the mutation setup

#### Scenario: Entity mappers are still mutated
- **WHEN** the report is generated and a module has top-level functions that map between entities and domain models
- **THEN** the report contains mutants for those functions

#### Scenario: Persistence classes keep their coverage
- **WHEN** `./gradlew coverageReport` runs
- **THEN** classes annotated with `@Repository` or `@Entity` are still part of the coverage report
