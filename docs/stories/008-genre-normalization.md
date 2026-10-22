# 008 – Genre normalization

**As a** user who wants to browse by genre on every device **I want** genres to be consistent **so that**
`Hip Hop`, `Hip-Hop` and `hiphop` don't appear as three genres, and my old car shows a sensible genre too.

## Acceptance criteria
- [ ] A genre list with aliases (`Hip-Hop` ← `Hip Hop`, `HipHop`, `hip hop`); extracted/entered genres are mapped to the main name.
- [ ] Unknown genres are kept as entered and reported, with an option to add them as new genre or alias.
- [ ] I can edit the genre list (own genres like `Balkan`, `Narodna`, `Turbo-folk`).
- [ ] Each genre has an ID3v1 mapping (nearest standard genre number, e.g. `Turbo-folk` → `Folk`/`Ethnic`); used when writing v1.
- [ ] Multiple genres per file are supported in the model; the profile decides whether one (first) or all are written.
- [ ] Existing numeric genres `(17)`, `17` are converted to text.

## Edge cases
- Genre in Cyrillic
- ID3v1 has no fitting genre → `Other` (12)

## Data safety
Mapping only changes proposed values.

## Interfaces
- CLI: `musicstudio genres list|add|alias`
- GUI: genre field with autocomplete, genre editor.

## Out of scope
Automatic genre detection from audio or online databases.

## Assumptions
- Initial list = ID3v1/Winamp genres + a few Balkan/regional genres I'll provide.
