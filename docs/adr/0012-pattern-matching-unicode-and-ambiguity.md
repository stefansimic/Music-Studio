# 0012 – Pattern matching: Unicode normalisation, typed digits and ambiguity definition

Status: proposed (story 002). Complements [0009](0009-pattern-language-own-parser-and-matcher.md).

## Context
The library contains Greek, Serbian Cyrillic, Latin diacritics (č ć š ž đ) and emoji in file and folder names. Operating
systems store the same text differently (macOS: NFD; Linux/Windows: usually NFC). Users type pattern literals on their own
keyboard. Without defined rules a pattern works on one machine and not on another, and saved patterns (story 007) would be
unstable. "Ambiguous" must also be defined precisely, because 002 shows it and 007 reuses it.

## Decision
1. Pattern literals, file/folder names and extracted values are normalised to **NFC** (`java.text.Normalizer`, JDK only)
   before matching and storing. Matching runs on code points, never splitting surrogate pairs.
2. Typed numeric placeholders accept **ASCII digits 0-9 only**. Other scripts' digits are plain text, not numbers.
3. Literals are **case-sensitive and not folded**: en dash, em dash and hyphen are different literals. The NO_MATCH reason
   mentions look-alike dashes. Only space handling is lenient (leading/trailing literal spaces match zero or more
   whitespace characters incl. NBSP and U+3000; inner runs match one or more).
4. A match is **ambiguous** when at least two distinct vectors of capture boundaries make all tokens match (after typed
   placeholders pruned invalid splits). The chosen split is the non-greedy left-to-right one (shortest earlier capture).
   Captures that are empty after stripping are invalid (wildcards may be empty).
5. No `java.util.regex`; the matcher is a DP in `core.pattern`.

## Alternatives
- Match on raw strings (no normalisation): fails across OSes for diacritics. Rejected.
- Fold dash look-alikes and case: surprising, hides different names, hard to undo. Rejected (revisit with a flag later).
- Unicode `\d` digits: would turn `٢٠٢٠` into years; tag writers then fail. Rejected.
- Regex-based matcher: reflection-free but ambiguity counting and column errors are awkward (see 0009).

## Consequences
- Extracted values may differ byte-wise from the on-disk (NFD) name; the written tag is NFC, which tag readers expect.
- Saved patterns (007) and output patterns (004) inherit NFC and the ambiguity definition.
- Changing normalisation or the ambiguity definition later changes results for saved presets, so it requires a new ADR.
