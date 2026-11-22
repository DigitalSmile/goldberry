# Spell checking in text fields — implementation plan

Written 2026-11-22, before any code. This is W3.7 of
[`gaps/goldberry-gaps-plan.md`](gaps/goldberry-gaps-plan.md), Tessera's T-25:
"Spellcheck in text inputs". On 2026-11-08 the owner chose D18 (b), bundled
Hunspell rather than the platforms' own checkers. The plan's row says the wavy
`text-decoration-style` comes first, then Hunspell in a native library of its
own, a `SpellChecker` SPI, checks off the UI thread per `TextDocument` line,
and suggestion rows in `TextMenu`. Dictionaries come from the application,
because their licences differ by language, so no jar ships one.

The document has the same shape as `zero-copy-tiles.md`: what was found, the
decisions with a status, phases with exit criteria, risks, and a log. Each
decision becomes an ADR when it is taken, parked in `docs/snapshot/adr/` from
0733. **All nine were taken on 2026-11-22**: D5 went (b), and the other eight went as recommended.

## 1. Where things stand

Read from the tree on 2026-11-22 (master at `67e807d7`).

| Area | State |
|------|-------|
| Decoration lines | `text.flow.TextDecoration` is `UNDERLINE` and `LINE_THROUGH`, held as a `Set` in `TextFlow` and in `ComputedStyle.textDecoration`, inherited (an approximation of CSS's propagation). `text-decoration` and `text-decoration-line` are the only properties. A declaration that names a style or a colour is **dropped whole** (`ComputedStyle`, the `text-decoration` case): "a rule that asked for `underline wavy red` and got a straight rule in the text's own colour would be a property that lies" |
| Drawing them | `Paragraph.rule` (per span) and `Paragraph.decorate` (per line) call `fillRule`, a `Frame.fillRect` at the face's own `Font.Decorations` position and thickness, in the text's colour. Nothing strokes a path along the text |
| Per-span paint | `SpanPaint(start, end, argb, decorations, background)`. A span's rule takes the span's text colour. There is no separate decoration colour, so a red squiggle under black text cannot be said |
| Marks | `MarkStyle(font, argb, decorations, background)`. `Editor.spansOf` turns a hard line's mark runs into `SpanPaint`s. The marks are document state: they are kept in undo and on the clipboard. A misspelling is **not** document state |
| The document | `TextDocument` keeps one `Paragraph` per hard line and reshapes only the lines an edit touched, through `Editor.shaper(TextDocument.Shaper)` |
| Pseudo-elements | `css.select.PseudoElement` is a closed set, `PLACEHOLDER` and `SELECTION`. Each one is a part that names itself through `StyleElement.pseudoElement()`. That is the route a `::spelling-error` would take (CSS Pseudo-Elements 4 §3.4) |
| The menu | `widgets.form.edit.TextMenu`: Cut, Copy, Paste, Select all, and a field's own rows after them (`Supplier<List<Widget>> more`, which a rich field uses for its Format submenu). No rows go above the stock ones, and that is where every platform puts suggestions |
| The fields | `text-input` (`TextField`/`TextEditor`), `text-area` (`TextArea`), `rich-input` (`RichInputState` over `Editor` with marks), `code-input`. The last has its own highlighting and is no candidate |
| Off the UI thread | `EventLoop.supplyAsync`/`runAsync` run on virtual threads and hand the result back to the UI thread. `ImageCache` decodes the same way |
| Natives | `:media` shows how a library of its own is done: a superbuild in `media/src/main/cmake` (`goldberry_pin`, a `checkout.cmake` commit check, an exports list), `natives-<target>` classifiers, a loader (`MediaNatives`), FFM bindings written by hand, and CI on four targets |
| Word boundaries | `DocumentWordBreakTest` covers the editor's own word motion. `html`'s `content.inline.Words` splits for justification. Neither one is a spell checker's tokenizer, which has to skip URLs, addresses, numbers and code |

**Engines, checked on 2026-11-22.**

| Engine | What it is | Licence | Cost |
|--------|------------|---------|------|
| Hunspell 1.7.6 (C++, released 2026-10-26) | The reference. LibreOffice, Firefox and Chrome on Linux use it | MPL 1.1 / GPL 2 / LGPL 2.1, the user's choice | A superbuild of its own on four targets, FFM bindings, an exports list, a native ABI, and conversion to and from each dictionary's `SET` encoding. Built size is about 0.5 MB a target. MPL 1.1 §3.2 asks that the source of what is shipped stay available |
| Lucene 10.6.0 `org.apache.lucene.analysis.hunspell` (pure Java) | A Hunspell reimplementation, tested against Hunspell's own test files. It reads the same `.aff`/`.dic`, has `Hunspell.spell`, `suggest` and a `Suggester` with a `TimeoutPolicy`. JetBrains wrote it for IntelliJ, which uses it | Apache 2.0, the toolkit's own | `lucene-analysis-common` (1.8 MB) plus `lucene-core` (4.8 MB), both JPMS modules (`org.apache.lucene.analysis.common`, `org.apache.lucene.core`). No native code, so no new natives and no CI matrix |
| The platforms' checkers (`NSSpellChecker`, `ISpellChecker`, Enchant) | — | — | Declined in D18 (a). The SPI below keeps them possible later |

## 2. What this plan delivers

- CSS: `text-decoration-style` (`solid`, `double`, `dotted`, `dashed`,
  `wavy`), `text-decoration-color`, `text-decoration-thickness`, and the full
  `text-decoration` shorthand. They are drawn along text, spans and marks
  alike. This is useful on its own: Tessera's links and the HTML view's
  `<u style="text-decoration: wavy">` both want it.
- `::spelling-error`, with a default in the theme.
- A `SpellChecker` SPI in `:core` and a checking service that works through a
  document's lines off the UI thread and caches results per line.
- `spellcheck=` on `text-input`, `text-area` and `rich-input`.
- Suggestions, *Add to dictionary* and *Ignore* at the top of `TextMenu`.
- `goldberry-spell`, a new optional module holding the engine (D1), which
  loads an application's `.aff`/`.dic` from a `Path` or a stream.
- A card on the showcase, the guide section (parked until release), and
  KDL.

**Out of scope:** grammar (`::grammar-error` is only reserved in the
pseudo-element set), auto-correct while typing, and language detection.

## 3. Decisions

### D1. The engine (taken: (a))

- **(a) Lucene's Hunspell, in Java (recommended).** It reads the same
  dictionaries and gives the same answers on Hunspell's own tests. It adds no
  native code. Its licence is the toolkit's. `TimeoutPolicy` bounds a slow
  suggestion, which matters for languages with heavy affix rules (Hungarian,
  Turkish). It costs 6.6 MB of jars, carried only by an application that adds
  `goldberry-spell`.
- (b) Hunspell in C++, a library of its own as `:media` has. This is what the
  plan's row assumed. Its output is smaller and it is the reference
  implementation, but it brings a superbuild, a CI matrix, an ABI, encoding
  conversions, and the MPL's source obligation.

D18 chose Hunspell dictionaries over the platforms' checkers. (a) keeps that
choice and changes only what reads the dictionaries. That is why this is asked
again rather than assumed.

### D2. Where the pieces live (taken: (a))

**(a) Recommended.** The SPI goes in `:core` under `dev.goldberry.text.spell`
(`SpellChecker`, `Misspelling`, `SpellCheckers` and the checking service), so
the fields in `:widgets` can depend on it. The engine goes in the new
`:spell` module (`goldberry-spell`, `dev.goldberry.spell`), provided as a
`SpellCheckerProvider` service, optional in the umbrella, as `:html` and
`:media` are. Nothing in `:core` or `:widgets` requires it.

### D3. How the squiggle is styled (taken: (a))

**(a) Recommended.** It is a `::spelling-error` pseudo-element, as CSS has
it, taking `color`, `background-color` and the `text-decoration-*`
properties. The theme's default is
`::spelling-error { text-decoration: wavy underline var(--gb-danger) }`.
(b) would be a fixed red in code.

### D4. How much of CSS text decoration (taken: (a))

**(a) Recommended.** Style (all five), colour, thickness and the full
shorthand. CSS's own rule for `wavy` is followed: the wave's height follows
the thickness. Also `text-underline-offset`, because a wavy line at the
face's own underline position touches descenders. (b) would be `wavy` alone.

### D5. Which fields check by default (taken: (b))

**(a) Recommended, as HTML does.** `text-area` and `rich-input` check, and
`text-input` does not unless it says `spellcheck=true`. Password and code
fields never check. Without an installed dictionary nothing happens at all,
so turning this on costs an application nothing until it installs one.
(b) would be off everywhere until a field asks.

### D6. Language (taken: (a))

**(a) Recommended.** `SpellCheckers.install(Locale, SpellChecker)` per
application. A field checks with the checker for its `lang=` (an attribute
that is new on fields). Without one it uses `Labels`' locale, then the
default locale. A missing language checks nothing and is logged once.

### D7. The personal dictionary (taken: (a))

**(a) Recommended.** *Add to dictionary* adds the word to an in-memory
`PersonalDictionary` shared by all checkers of a language. The application
reads it and saves it, through a listener and a `words()` snapshot, because
the toolkit has no settings directory it owns. *Ignore* lasts for the field
and is gone with it. (b) would be a file the toolkit writes under the user's
data directory.

### D8. When a word is checked (taken: (a))

**(a) Recommended.** A word is not flagged while the caret is in it or at its
end. It is checked once the caret leaves it, as every platform does. Results
are cached per hard line by the line's text and the checker's revision (which
moves when the personal dictionary changes). Unchanged lines are never asked
again. The lines in view are checked first, then the rest, on the background
executor. (b) would check everything on every edit.

### D9. What the menu holds (taken: (a))

**(a) Recommended.** On a misspelled word: up to five suggestions, each
replacing the word as one undo step. Then *Add to dictionary*, *Ignore*, a
separator, and the stock rows. With no suggestions there is one disabled row,
"No suggestions". Opening the menu with the keyboard (Shift+F10, the Menu key)
on a misspelled word offers the same rows.

## 4. Phases

Each phase ends green on `:core:check`, `:widgets:check`, and `:example:test`
where pictures change. Tests are written with the code, not after it.

### Phase 1 — CSS text decoration (D4)

- `text.flow`: `TextDecorationStyle` (five values) and a `DecorationLine`
  record (lines, style, colour or `currentColor`, thickness as `auto`,
  `from-font` or a length, underline offset). It is held by `TextFlow` and
  `ComputedStyle` in place of the bare `Set`. The `Set` accessors stay as
  views of it.
- Cascade: the four longhands and `text-underline-offset` in `Longhands`, the
  shorthand in `Shorthands`. A declaration that used to be dropped now parses.
- `Paragraph`: one `DecorationPainter`. `solid` stays `fillRect`, so no
  existing picture moves. `double` is two rects. `dotted` is round dots one
  thickness apart. `dashed` is dashes three thicknesses long. `wavy` is a
  stroked path of quadratic arcs whose phase follows the line's x, so a line
  drawn in two spans keeps one wave.
- `SpanPaint` and `MarkStyle` carry a `DecorationLine`, so a span's rule can
  have a colour of its own.
- Tests: parsing (every value, the shorthand in any order, `currentColor`,
  invalid values dropped), the propagation rule, painting goldens for each
  style at 1x and 2x, a wave continuous across two spans, RTL.

### Phase 2 — the SPI and the checking service (D2, D6, D7, D8)

- `dev.goldberry.text.spell`:
  - `SpellChecker`: `locale()`, `isCorrect(word)`, `suggest(word, limit)`,
    `revision()`.
  - `SpellCheckerProvider`, a service: load from the `.aff`/`.dic` pair.
  - `SpellCheckers`: the registry per application.
  - `PersonalDictionary`.
  - `WordScanner`, which finds the words to check and skips URLs, e-mail
    addresses, numbers, words with digits and single letters.
  - `LineChecks`, the per-line cache and the background queue, with results
    as `Misspelling(start, end)` per hard line.
- `PseudoElement.SPELLING_ERROR` and `GRAMMAR_ERROR` (the second one reserved,
  unused), and the theme's rule.
- A `WordListChecker` in the test fixtures, so nothing below waits for an
  engine.
- Tests: the scanner's table of cases, cache hits and revisions, cancellation
  when a line changes before its check comes back, the caret rule.

### Phase 3 — the fields draw it (D3, D5)

- `spellcheck=` and `lang=` on `text-input`, `text-area` and `rich-input`, in
  Java and KDL. Off by default everywhere (D5 (b)): a field checks only when
  it says `spellcheck=true`.
- Each field's editor merges its misspellings into the line's `SpanPaint`s,
  in the `::spelling-error` style.
- `rich-input` skips text under `CODE`, links and mentions.
- Damage: a check that comes back repaints only its lines.
- Tests: offscreen goldens of a field with a misspelling, the caret rule
  through key events, a password field never checked, no dictionary meaning
  no work queued.

### Phase 4 — the menu (D9)

- `TextMenu` takes rows *before* the stock ones. `SpellRows` builds them
  from the word under the press or the caret.
- A replacement is one `Editor` edit with one undo step. In `rich-input` it
  keeps the word's marks.
- Tests: `TextFieldMenuTest`-style menu cases, undo after a replacement, *Add
  to dictionary* clearing the squiggle everywhere that word appears.

### Phase 5 — `goldberry-spell` (D1)

- The module, its `module-info`, a published artifact in `PublishedModules`
  and the BOM, optional in the umbrella. `HunspellChecker.load(Path aff, Path
  dic)` and `load(InputStream, InputStream)`.
- Lucene's `Dictionary` and `Hunspell` over a temporary directory
  for its offline sort, suggestions under a `TimeoutPolicy`, and GraalVM
  reachability metadata if a native-image run asks for any.
- Tests run against a small `.aff`/`.dic` pair written for the tests, so no
  dictionary licence enters the repository. Hunspell's own affix cases are
  ported for compound words, `REP` and `KEY`.
- A benchmark in `src/benchmark`: the checks per second on a 10 000-word
  document, and the suggestion latency.

### Phase 6 — the showcase and the guide

- A "Spelling" card on the showcase's forms screen. Its dictionary is
  `en_US` from the pinned SCOWL release (permissive), fetched by `:assets`
  into the example alone, never into a published jar.
- The guide section is parked in `docs/snapshot/`. The book pictures are
  retaken.

## 5. Risks

- **The showcase dictionary's licence.** SCOWL's `en_US` is permissive, but
  each line of its README is checked before the fetch is pinned.
- **Pictures moving in phase 1.** `solid` keeps `fillRect` for exactly this
  reason, and a golden diff on the full `:example:test` run is the check.
- **Lucene's temporary files.** `Dictionary` sorts entries through a
  `Directory`. A `ByteBuffersDirectory` keeps it in memory, and that is tried
  first.
- **Rich input's marks over a replaced word.** A replacement inside a bold
  word stays bold. One across a mark boundary takes the first character's
  marks, as typing over a selection does.
- **IME composition.** A word in an open composition is never checked, by the
  same rule as the caret.

## 6. Documents kept in step

| Document | What | Status |
|----------|------|--------|
| `docs/gaps/goldberry-gaps-plan.md` §8, §10 | T-25's row and the log | plan written |
| `docs/gaps/answers-tessera.md` | T-25's answer when it lands | open |
| `docs/snapshot/adr/` | ADR-0733 (D1–D9), ADR-0734 (phase 1) | phase 1 done |
| `docs/snapshot/changelog-breaking-changes.md` | `ComputedStyle.textDecoration()`'s type, one more component on `TextFlow`, `SpanPaint` and `MarkStyle` | done (phase 1) |
| `docs/snapshot/` guide pieces | styling (`guide-styling-text-decoration.md`, done), forms (spell checking) | styling done |
| `docs/status.md` | a line per phase | phase 1 |

## 7. Status and log

| Phase | Status |
|-------|--------|
| 0. Plan | done (this document, ADR-0733) |
| 1. CSS text decoration | done 2026-11-22 (ADR-0734) |
| 2. SPI and service | todo |
| 3. Fields | todo |
| 4. Menu | todo |
| 5. `goldberry-spell` | todo |
| 6. Showcase and guide | todo |

- **2026-11-22.** Plan written from the tree at `67e807d7`. Found:
  decoration is lines only and drawn as rectangles, `SpanPaint` has no
  decoration colour, `TextMenu` takes rows only after the stock ones, and
  `PseudoElement` is the route for `::spelling-error`. Checked the engines:
  Hunspell 1.7.6 (C++, MPL/GPL/LGPL) against Lucene 10.6.0's pure-Java
  Hunspell (Apache 2.0, 6.6 MB, a JPMS module, `Suggester` with
  `TimeoutPolicy`). D1–D9 are asked.
- **2026-11-22, later.** The owner answered: D1 (a) Lucene, D5 (b) off
  everywhere until a field asks, D7 (a), and D2–D4, D6, D8 and D9 as
  recommended. Phase 1 starts.
- **2026-11-22, phase 1 done.** `text-decoration-style`, `-color`,
  `-thickness` and `text-underline-offset` are read
  (`css.text.TextDecorationParser`). The cascade holds one `TextDecorations`
  (lines and a `DecorationPaint`), so a decoration inherits whole. `TextFlow`,
  `SpanPaint` and `MarkStyle` gained a `decorationPaint`, and their former
  constructors stay. `text.decoration.DecorationPainter` draws the five
  styles. `solid` is still `fillRect`, and `:example:test` passed with no
  golden retaken. The patterns are anchored to the frame's x, and a test
  draws each one in one piece and in two and compares. A `calc()` length lost
  its spaces on the way in and read as nothing; the parser test caught it.
  Tests are at 1x only. The painter works in logical units and the frame
  scales it, so the 2x and right-to-left goldens wait for phase 3's fields.
  ADR-0733 (the decisions) and ADR-0734 (phase 1) are parked, and the guide
  piece is `docs/snapshot/guide-styling-text-decoration.md`. Next: phase 2,
  the SPI.
