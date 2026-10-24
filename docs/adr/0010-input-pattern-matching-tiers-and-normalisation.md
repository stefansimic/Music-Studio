# 0010 – Input pattern matching: separator tiers, possessive whitespace, NFC

Status: accepted · Date: 2026-10-22 · Refines: [ADR 0009](0009-pattern-language-own-parser-and-matcher.md),
[architecture §10.2](../architecture.md) · Plan: [`docs/plan-002.md`](../plan-002.md)

## Context
§10.2 says that the spaces around a separator match "zero or more" whitespace, so `" - "` also matches a bare `-`.
That handles "separator spaces missing in a few files", but taken literally it breaks the most common case:
`Jay-Z - Song` and `Hip-Hop Mix - Song` with `{artist} - {title}` would have two splits (at the inner `-` and at
` - `). They would be reported as AMBIGUOUS, and the chosen non-greedy split (`Jay` / `Z - Song`) is wrong.
"Zero or more" spaces also allows several end positions for one separator (`" - "` vs `" -  "`), so the DP would count
one visible split several times. Saved patterns (story 007) make the matching semantics hard to change later,
so they are fixed here.

macOS stores file names in Unicode NFD. Without normalisation, `Đorđe` from a file name would not equal the same
name typed in a pattern literal or in a `--set` value, and decomposed text would be written into tags.

## Decision
1. **Possessive whitespace.** A literal is split into a leading whitespace run, a core and a trailing whitespace run.
   The core may itself contain inner whitespace runs. Every whitespace run in the pattern consumes the *whole*
   whitespace run in the name. A literal with a leading run may only start where the name's whitespace run starts.
   One split of the name therefore gives exactly one DP solution, and AMBIGUOUS means "different values".
2. **Two tiers per segment.**
   - *Strict tier:* each whitespace run of the pattern needs ≥ 1 whitespace character in the name. The exception is a
     run at a segment edge, which may match zero.
   - *Lenient tier:* the edge runs of a literal (around its core) may match zero whitespace. Inner runs and
     whitespace-only literals still need ≥ 1.
   - The matcher counts solutions in the strict tier first. If there is at least one, the result comes from that
     tier alone. Only if there is none does the lenient tier run. Then `Jay-Z - Song` is MATCHED (strict),
     `Artist-Song` is MATCHED (lenient), and `A - B - C` with `{artist} - {title}` is still AMBIGUOUS.
3. **NFC.** Path element names and pattern literals are normalised to NFC before matching. Extracted values are NFC.
   `FieldValueParser` normalises `--set` values the same way.
4. **Ambiguity is reported with an alternative.** For an ambiguous segment, the lexicographically smallest split
   (non-greedy, chosen) and the largest split (greedy) are both reconstructed from the DP table. With ≥ 2 solutions
   these are always different. `MatchResult.alternative` replaces the `alternativeCount` field of §10.1.

## Alternatives
- *Keep "zero or more" without tiers:* hyphenated artists, very common in the user's library, become ambiguous and
  are split wrongly.
- *Always require the spaces (no lenient tier):* `Artist-Song` in a batch of `Artist - Song` files would be
  NO_MATCH. The story explicitly lists "separator missing … in a few files" and "extra spaces" as edge cases to
  tolerate.
- *Count distinct value assignments instead of DP paths:* this would also work, but it needs the set of solutions
  instead of a count capped at 2, and it is slower on long names.

## Consequences
- A pattern gives a different result than plain "first match" semantics only where the old text was ambiguous or
  wrong. This is documented in §10.2 and tested with table-driven cases (`PathMatcherTest`).
- The matcher runs the DP at most twice per segment. The cost stays O(tokens × length²) per segment.
- Patterns saved in 007 rely on these semantics. Changing them later needs a new ADR and a preset schema bump.
- Known limits, accepted after review (2026-10-23):
  - The tier is chosen per segment, not per separator. In `Jay-Z - Song-2020` with `{artist} - {title} - {year}`
    the missing spaces before the year push the whole segment to the lenient tier, where it becomes AMBIGUOUS. That
    is safe, because the file is skipped and shown.
  - An empty `{*}` between two space-delimited separators (`A - - B` with `{artist} - {*} - {title}`) does not match,
    because the first separator already consumed the shared whitespace run.
  - AMBIGUOUS also covers solutions between the shortest and the longest split: if those two agree, every
    completable capture is checked for other values.

