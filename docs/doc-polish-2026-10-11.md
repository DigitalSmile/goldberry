# Documentation polish, 2026-10-11

The user's review of goldberry.dev/docs after the 2026.2 release, eight items.
The decision is ADR-0605, parked in `docs/snapshot/adr/`. The edits went
straight into `book/`, `site/` and the README because the user asked for the
site to change. This file is the working notes and the status.

## Status

| # | Request | State | Where |
|---|---|---|---|
| 1 | Remove status and TODO from the site docs | done | `git mv` to `docs/status.md` and `docs/TODO.md`; *Reference* part gone from `SUMMARY.md` and the showcase's Guide screen (`GuideParts`); contributing chapters link them on GitHub; landing page footer and `llms.txt` no longer point at `docs/status.html`; `BookTest.Summary.holdsNoRecord` |
| 2 | Describe weaving briefly, maybe another term | done | `book/src/ahead-of-time-binding.md`, *Ahead-of-time binding*, 180 lines where *Model weaving* had 370. Opens with a "do I need it" table. The tool keeps its name (`goldberry-weaver`). Links moved in 18 Java and Gradle sources and the showcase |
| 3 | README: released, not pre-release | done | `README.md` rewritten: tagline, badges, showcase picture in both themes, why, install (Gradle and Maven), a complete first application, testing, the showcase, a documentation map. No status/TODO links. `ReleaseNotesRepositoryTest` still finds the badge and the BOM version |
| 4 | No "not verified", "not tested" text | done | Three parallel passes over every chapter; `GuideVocabulary.VERIFICATION` and `BookTest.Neutral.reportsNoVerificationState`. `overview/limitations.md` lost its status pills and four rows that only reported verification |
| 5 | No third-party library names outside the overview | done | Same passes; `GuideVocabulary.LIBRARY` and `BookTest.Neutral.namesLibrariesOnlyInTheOverview`. `## Flexbox, from Yoga` is `## Flexbox`; `## What is measured, and what is not yet` in `components/gpu.md` is `## What it costs`; links moved with them |
| 6 | Screenshots left aligned, obviously UI, clickable for full view | done | `goldberry.css`: a window frame (three lights, *Screenshot*, *Click to enlarge*) that hugs the picture at the start of the column; `book/theme/lightbox.js` with `book/test/lightbox.test.mjs` (7 tests), run by `pages.yml`; `SiteTest` and `BookTest.Configuration` hold the wiring |
| 7 | Awful screenshots in Audio and Video | done | `MediaPicturesTest` in `:media` takes `media-player` and `audio-player` at 2× in both themes from `guide-clip.mkv`, our own still made by `make-guide-clip.sh`. `media-player-subtitles.png` (1×, 160×90 test pattern) left the book |
| 8 | Prettier look and feel | done | 860 px reading column, unnumbered sidebar (`no-section-label`), quieter in-chapter headings in the sidebar, section rules between `##`, tinted code blocks, small-caps table headers, callout titles, softer shadows; no picture is stretched past its pixels |

## Judgement calls

- **Allowed names.** What the reader installs, binds or types stays named:
  GraalVM, the zlib development package, SLF4J and Logback, WebKitGTK as a Linux
  install requirement, Maven classifiers such as `ffmpeg-linux-x64`, logger
  names and quoted log lines. The guard reads prose only.
- **Overview keeps the names.** `overview/concept.md` and `architecture.md`
  still say what the toolkit is built on, as the user asked. The heading
  "Binding and weaving" in `architecture.md` stays, because doc comments link it.
- **Other 1× pictures.** `gallery-gpu.png`, `gallery-drawing.png`,
  `affix-pinned.png` and `toast-dark.png` are still one shade at 1×. They are no
  longer stretched and open at full size. The GPU one needs a device to retake,
  and the showcase card in it still says "what is not yet".
- **Showcase text.** The showcase's own cards (for instance the GPU screen's
  "on one machine") are application text, not documentation, and were not
  changed.
- **A guide sample fixed on the way.** `first-java-application.md` imported
  `dev.goldberry.css.CascadeLayer`; the class is in `dev.goldberry.css.cascade`.

## Verification

- `./gradlew :build-logic:test`: green, including `GuideVocabularyTest` (7)
  and the new `BookTest` checks.
- `node --test "site/test/*.test.mjs" "book/test/*.test.mjs"`: 17 of 17.
- `:example:test` for `*Book*`, `*DocLink*`, `*Guide*`, `*PicturesTest*`,
  `*Gallery*`, `*Showcase*`: green. `gallery-guide.png` re-blessed, because the
  renamed chapter card shows at the fold.
- `:media:test --tests 'dev.goldberry.media.view.pictures.*'`: 5 of 5.
- The built book was checked in Firefox in both themes, with the lightbox open.

## Follow-ups

- Retake the four remaining 1× pictures at 2× in both shades.
- The CSS batch in the tree at the same time (ADR-0600 to 0604) parks
  `docs/snapshot/guide-styling-css-subset.md` for `guide/styling.md`, which
  this pass edited; merge by heading at release.
