# 0005 – GraalVM native image for the CLI, jpackage (jlink runtime) for the GUI

Status: accepted · Date: 2026-10-22 · Details: [architecture §15](../architecture.md)

## Context
The CLI should start instantly and be a single binary. The GUI needs JavaFX, which GraalVM JDK does not bundle and
which is hard to compile natively (GluonFX). Java 25 is the baseline.

## Decision
- Build with Java 25 (`maven.compiler.release=25`) on GraalVM for JDK 25. Maven multi-module with a wrapper.
- **CLI**: Maven profile `native` in `music-studio-cli` using `org.graalvm.buildtools:native-maven-plugin`
  (`compile-no-fork`, `--no-fallback`, `-march=compatibility`). picocli's reflection config comes from
  `picocli-codegen` (annotation processor). Library metadata (jaudiotagger, Parsson if needed) is generated with
  the tracing agent from the adapter tests and committed under each adapter's `META-INF/native-image/`.
- **No reflection frameworks**: manual wiring in `music-studio-app`, no DI container, no ServiceLoader-dependent
  code paths of our own, no reflection-based JSON binding.
- **GUI**: JVM application packaged with `jpackage` and a jlink runtime that includes the JavaFX jmods
  (`javafx.controls`, `javafx.media`). Our jars stay on the classpath (no JPMS yet, ADR 0001).
  `org.openjfx:javafx-maven-plugin` for `javafx:run`. GluonFX native GUI stays an experiment.
- Assertions are off in shipped binaries. `Require` keeps preconditions active.

## Alternatives
- *Fat jar for the CLI*: needs an installed JRE and starts slowly.
- *jpackage for the CLI too*: large and slower to start, but a fallback if native-image metadata becomes a burden.
- *Bundling JavaFX via a JDK that includes it (Liberica Full, Zulu FX)*: simpler jlink, but ties us to one vendor.
  It stays an option.
- *Spring/Micronaut-style DI*: reflection or annotation processing frameworks are not needed for this graph size.

## Consequences
- Native builds are OS-specific (CI matrix later). Every new library on the CLI path needs a native smoke test.
- `-ea` is not active in native production builds. Native test runs (`mvn -Pnative test`) can enable it.
- jaudiotagger's metadata must be regenerated when the library version changes. This is a checklist item in upgrades.
