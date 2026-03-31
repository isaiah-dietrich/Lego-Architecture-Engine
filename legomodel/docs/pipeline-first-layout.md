# Pipeline-First Layout

This project is organized so day-to-day work is centered on the core runtime
pipeline, while tooling and legacy paths remain available but opt-in.

## Source Layout

- `src/main/java`: core pipeline and required runtime code
- `src/tooling/java`: analysis/benchmark/diagnostic code (opt-in)
- `src/legacy/java`: legacy code paths (opt-in)
- `src/test/java`: fast/core tests
- `src/tooling-test/java`: heavy and tooling-focused tests (opt-in)
- `src/legacy-test/java`: legacy-focused tests (opt-in)
- `src/full-test/java`: mixed tooling+legacy integration tests (opt-in)

## Build Profiles

- Default build: core only
  - `mvn test`
- Tooling build: core + tooling
  - `mvn test -Ptooling`
- Legacy build: core + legacy
  - `mvn test -Plegacy`
- Full build: core + tooling + legacy
  - `mvn test -Pfull`

## Dependency Direction

- Core code must not require tooling or legacy classes at compile time.
- Tooling/legacy code may depend on core code.
- Optional features are loaded only when their profile sources are available.

## CLI Behavior

- Default help and behavior focus on the core pipeline.
- Tooling options are available only in `-Ptooling`/`-Pfull` builds.
- Legacy options are available only in `-Plegacy`/`-Pfull` builds.
