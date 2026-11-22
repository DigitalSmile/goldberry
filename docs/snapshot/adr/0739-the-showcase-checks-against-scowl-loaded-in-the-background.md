# ADR-0739: The showcase checks against SCOWL's `en_US`, loaded in the background

- **Status:** Accepted
- **Date:** 2026-11-22
- **Relates to:** ADR-0733 (the plan, whose last phase this is), ADR-0738
  (the engine)

## Context

Phase 6 of W3.7 puts spell checking on the showcase and writes the guide's
section. The showcase needs a real dictionary, and no toolkit artifact may
ship one. The plan also asked for numbers from a real dictionary, which the
engine's phase had deferred.

## Decision

1. **SCOWL's `en_US` 2026.02.25 is a pinned asset of the showcase alone**
   (`Asset.SCOWL_EN_US`). The zip is the GitHub release's, pinned by tag and
   SHA-256. `assetTool.bundle('scowl-en-us,unicode-emoji',
   'dev/goldberry/example')` prepares it into `…example/spelling`, replacing
   the `licences` call that bundling now makes. SCOWL's README is vendored as
   `licenses/scowl.txt`, with a row under *Compiled into the showcase* in
   `THIRD-PARTY-NOTICES.md` and a native-image resource glob. The licence is
   SCOWL's permission notice plus Geoff Kuenning's BSD licence for the affix
   file.
2. **`goldberry-spell` is `runtimeOnly` in the showcase.** `:core` `uses` the
   provider, so the module system binds it from the module path with no
   `requires`. The showcase's run on the Forms screen logs the engine reading
   the dictionary.
3. **The dictionary loads in the background in a window.**
   `ShowcaseDictionary.install()` uses `Goldberry.async` on the UI thread and
   loads at once elsewhere, so an offscreen picture has it on its one frame.
   The fields hear the checker arrive through `SpellCheckers.onChange`.
4. **A Spelling card on the Forms screen.** It holds a title and a note with
   three planted misspellings, and `spellcheck=true` is said once, on the
   card. `SpellingCardTest` pins the misspellings against the real
   dictionary.
5. **Measured once, by hand, on an i7-4790K.** Loading takes 454 ms cold and
   53 to 93 ms warm. A check takes 0.6 to 10 µs a word. A suggestion takes 12
   to 38 ms on average. The numbers are recorded in `docs/spell-checking.md`
   and given to Tessera, and there is no benchmark lane: a dictionary is an
   application's, and the lane would have to fetch one to measure the engine.

## Consequences

- The search index and every picture that shows the sidebar's count or
  Home's tiles were retaken: Forms has 14 cards and the showcase 264.
- The guide's section is parked as `docs/snapshot/components-forms-spelling.md`.
- Hand checks remain: a right-click on a flagged word on each desktop, and
  the native image loading the dictionary through its resource glob.
