# 0009 – Pattern language: own parser and DP matcher in core, no regex in the API

Status: accepted · Date: 2026-10-22 · Details: [architecture §10](../architecture.md)

## Context
Stories 002/004/012 define a small placeholder language (`{album}/{artist} - {title} - {year}`, `{track:00}`,
`{*}`, `S{season}E{episode}`). Extraction must detect *ambiguous* splits (artist containing ` - `), explain
*why* a file did not match, tolerate extra spaces, and validate typed placeholders. The same syntax renders output
paths. The GUI needs error columns for live validation.

## Decision
- A hand-written parser in `core.pattern` returns a result type (`Valid`/`Invalid` with column-based errors), not
  exceptions.
- AST: segments separated by `/`, tokens `Literal | Placeholder(field, padWidth) | Wildcard`. Placeholders map
  1:1 to `Field.placeholder()`.
- Matching uses dynamic programming per path segment over (token, position). It counts solutions up to 2
  (→ AMBIGUOUS) and reconstructs the non-greedy, left-to-right solution. Typed placeholders prune during matching.
  Space-tolerant literals. The furthest-progress diagnostic gives the NO_MATCH reason.
- Rendering uses the same AST, plus `FileNameSanitizer`.

## Alternatives
- *Compile to `java.util.regex`*: quick, but it cannot count alternative splits (ambiguity), gives poor failure
  reasons, and has catastrophic backtracking risks.
- *Expose regex to users*: out of scope for 002 (maybe later as an additional input type).

## Consequences
- About 300–500 lines of pure, table-testable code in core.
- Extending the language (012 placeholders, double episodes) means adding to `Field`/`FieldType` and the typed
  parsers, without touching frontends.
