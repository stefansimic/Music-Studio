# 0002 – Design by contract: Require, assert, Javadoc contracts, contract tests

Status: accepted · Date: 2026-10-22 · Details: [architecture §4, §14](../architecture.md)

## Context
Implementations will be replaced: jaudiotagger by mp3agic, JavaFX by another toolkit, playback backends, the MP4
library. Interfaces are therefore the stable layer, and a replacement must be correct when written from the interface
documentation alone. Java's `assert` is disabled by default, also in native images, so it cannot guard the API.

## Decision
1. **Javadoc contract** on every port, use case and public record: purpose, every `@param` (allowed values,
   nullability), *Preconditions*, *Postconditions/Guarantees*, *On failure*, `@throws`, *Thread-safety*
   (template in architecture §4.2).
2. **`Require`** (`core.contract`), an always-on helper. It is called first in every public method and in every
   record's compact constructor. It throws `NullPointerException` / `IllegalArgumentException` /
   `IllegalStateException` for contract violations.
3. **`assert`** is used only for internal invariants and postconditions, in addition to `Require` and never instead
   of it. Surefire runs with `enableAssertions=true`. A dedicated test checks that assertions are enabled.
4. **Contract tests**: an abstract JUnit 5 class per port lives in core's test-jar (`…core.testkit`). Every adapter
   test and every fake extends it. Fixtures are produced by independent tools, with hand-written expectations.
5. Expected failures (bad file, no match) are *not* contract violations. They are `Issue`s in results or checked
   port exceptions (`MediaAccessException`, `StoreException`).

## Alternatives
- *`Objects.requireNonNull` only*: no range/state checks and inconsistent messages.
- *Bean Validation (Jakarta Validation)*: reflection-heavy, a framework dependency in core, unfriendly to native image.
- *assert-only preconditions*: silently off in production and native images.
- *Mock-based tests per adapter*: these test interactions, not behaviour, and do not transfer to a new implementation.

## Consequences
- More Javadoc to write and keep in sync. Reviewers check contract ↔ implementation ↔ contract test.
- A new adapter's definition of done is "extends the contract test and passes".
- Precondition checks cost a few branches per call, which is negligible next to file I/O.
- Messages from `Require` are developer-facing. User-facing problems always come back as `Issue`s.
