# ADR-0605: The site is user documentation, and the development record stays in the repository

- **Status:** Accepted
- **Date:** 2026-10-11
- **Relates to:** ADR-0509, ADR-0511, ADR-0512, ADR-0513, ADR-0567; `docs/doc-polish-2026-10-11.md`

## Context

The user reviewed goldberry.dev/docs after the 2026.2 release and asked for
eight changes. The guide read in places as a log rather than as documentation:

- The *Reference* part published `status.md` (8,000 lines) and `TODO.md`
  (4,000 lines), the development record, as two chapters of the guide.
- Tables and sentences reported verification: "written, unverified", "not
  verified on a Windows desktop", "measured on one machine", "in progress".
- Chapters named the libraries the toolkit is built on (Yoga, Blend2D, SDL,
  FFmpeg, md4c and others) to explain behaviour a reader only experiences.
- *Model weaving* spent 370 lines on bytecode mechanics before saying who needs
  it, and its name described the mechanism rather than the reader's problem.
- Screenshots sat centred in a full-width frame, read as page decoration rather
  than as the application's UI, and could not be opened at full size. The
  media chapter's only picture was a 1× golden of a 160×90 test pattern.

ADR-0567 made the guide neutral about its history. This record extends that to
its audience: the site is for the developer using the toolkit.

## Decision

**The site carries user documentation only.**

1. `status.md` and `TODO.md` move from `book/src/` to `docs/`. They are the
   development record and are read on GitHub. The contributing chapters link
   them there. `BookTest.Summary.holdsNoRecord` fails if either returns to the
   book.
2. No chapter reports a verification state. `GuideVocabulary.VERIFICATION`
   lists the phrases, and `BookTest.Neutral.reportsNoVerificationState` holds
   every chapter to it. A missing capability is stated as a plain fact.
3. Only the overview names the libraries the toolkit is built on, and the
   landing page lists them under *Built on*. `GuideVocabulary.LIBRARY` lists the
   names, and `BookTest.Neutral.namesLibrariesOnlyInTheOverview` holds the rest
   of the guide to it. Inline code, link targets and HTML tags are not read: a
   Maven classifier, a quoted error message or a URL is something the reader
   types or follows. What the reader installs or binds (GraalVM, the zlib
   development package, SLF4J and Logback, the Linux web engine) may be named.
4. *Model weaving* becomes **Ahead-of-time binding**
   (`ahead-of-time-binding.md`), a short chapter that opens with who needs it
   and why. The tool keeps its name, the weaver, and its artifact
   `goldberry-weaver`. Every doc comment and showcase link moves with it.
5. A `gb-shot` is a window: a title bar with the three lights and the word
   *Screenshot*, the picture, the caption. The frame hugs the picture and sits
   at the start of the column. A picture never stretches past its own pixels.
   `book/theme/lightbox.js` opens any picture at full size on a click, Enter or
   Space, and closes on a click, Escape or the close button, returning focus.
   `book/test/lightbox.test.mjs` runs it against a fake document in the Pages
   workflow beside the consent test.
6. The media widgets' pictures are taken at 2× in both themes by
   `MediaPicturesTest` in `:media`, from `guide-clip.mkv`: our own still of a
   lake at dusk with a subtitle track, made by `make-guide-clip.sh`.
7. The theme gets a reading column of 860 px, chapter titles without numbers
   in the sidebar, quieter heading links, tinted code blocks and small-caps
   table headers.
8. The README presents the released toolkit: what it is, how to install the
   current release, a complete first application, testing, and the documentation
   map. It no longer links the record.

## Alternatives considered

- **Keep status and TODO in the book, unlisted.** mdBook builds only listed
  pages, so they would vanish from the site, but `book/src` is what readers
  browse on GitHub as "the docs". The record belongs with the other working
  documents under `docs/`.
- **Keep library names and add a glossary.** It would explain the names but
  still make the reader learn them. The overview already says what the toolkit
  is built on, once.
- **Rename the weaver itself.** The artifact, the Gradle plugin and the main
  class are published API. The chapter and the concept change, the tool's name
  stays.
- **A lightbox library.** One more dependency on a page that has none, for
  ninety lines of script.

## Consequences

- A chapter that cites a library outside the overview, or says "unverified",
  fails `./gradlew :build-logic:test`. Doc comments, the showcase and the
  landing page are not held by those two guards.
- Links into the record from Java sources now name `docs/TODO.md`. Those were
  repository paths before and remain so.
- `media-player-subtitles.png` leaves the book. The media module's own golden
  of it stays.
- The four other one-shade pictures (`gallery-gpu.png`, `gallery-drawing.png`,
  `affix-pinned.png`, `toast-dark.png`) are still 1×. They are no longer
  stretched, and they open at full size.
