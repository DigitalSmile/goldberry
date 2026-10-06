# The book: goldberry.dev/docs/

The decisions are [ADR-0511](../book/src/adr/0511-the-book-is-a-guide-first-and-the-log-is-its-last-part.md)
and [ADR-0512](../book/src/adr/0512-the-log-is-read-on-github-and-the-book-is-the-guide.md).
How the site around it is deployed is in [`site.md`](site.md). This file is the
runbook, the style guide and the status of the documentation itself.

| Path | What it is |
|---|---|
| `book/src/SUMMARY.md` | The table of contents: the guide's six parts, then the reference |
| `book/src/<part>/*.md` | The guide. One directory per part; the first chapter of a part is its `index.md` where the part has an overview |
| `book/src/adr/` | The decision log. Not listed in `SUMMARY.md`, so not built: it is read on GitHub, and `adr/README.md` is its index (ADR-0512) |
| `book/src/images/` | Pictures the guide shows: `<name>-light.webp` and `-dark.webp` per widget, `screen-<name>-*` per showcase screen, `diagram-<name>-*` per scheme, each at 2×. Taken by the tests below, not drawn by hand (ADR-0513, ADR-0515) |
| `book/diagrams/` | The schemes: `diagrams.py` describes each as notes and arrows, `draw.py` renders both themes with Pillow |
| `tools/book/visuals.py` | Places a widget's pictures under its heading and wraps a sample pair in `gb-tabs`; safe to run twice |
| `book/theme/goldberry.css` | The landing page's tokens over mdBook's `light` and `navy` themes, the header, and the few HTML blocks the guide uses. Sizes are in px: mdBook scales the root to 10 px, so a `rem` here is a tenth of what it looks |
| `book/theme/goldberry.js` | Builds the landing page's header into mdBook's menu bar at load: the leaf, the six parts, GitHub. Turns every `gb-tabs` block into tabs and remembers the reader's language (ADR-0514) |
| `book/theme/favicon.{svg,png}` | The leaf mark |
| `book/book.toml` | mdBook 0.5, which refuses an unknown key |
| `build-logic/.../site/BookTest.java` | The drift guards, in `./gradlew :build-logic:test` |
| `example/src/test/.../BookMarkupTest.java` | Every `kdl` sample in the guide inflates against the real catalogue |
| `example/src/test/.../book/pictures/` | `BookPicturesTest` renders every widget's sample in both themes and holds the pictures to the code; `ScreenPicturesTest` does the same for nine showcase screens. `-Dgoldberry.golden.update=true` retakes them |

## Preview locally

With mdBook 0.5.4 on `PATH` (CI installs the same tarball; `~/bin` is where it
lands here):

```bash
mdbook serve book --open                                     # at /, live reload
MDBOOK_OUTPUT__HTML__SITE_URL=/docs/ mdbook build book -d /tmp/_site/docs   # as CI builds it
```

The whole site, landing page included, is in `site.md`.

## Structure

The six parts the guide has, and what each is for. A reader arrives at the
part their question belongs to and should not need the others first.

| Part | Answers | Starts at |
|---|---|---|
| Overview | what Goldberry is, how it is built, what it does not do | `overview/concept.md` |
| Getting started | what to install, the first jar, the first native binary | `getting-started/requirements.md` |
| Layout | how a tree is laid out, one chapter per layout widget | `layout/index.md` |
| Components | one chapter per family of widgets, every markup name under a heading of its own | `components/index.md` |
| Performance | what start-up and a frame cost, and how to measure them | `performance/index.md` |
| Developer guide | markup, styling, input, windows, testing, diagnostics, writing a widget, weaving, native image, contributing | `applications.md` |

The three pages the landing page links by name keep their paths: `status.md`,
`native.md` and `adr/index.md`. `SiteTest` fails if they move without
`site/content.js` moving with them.

## Style

The guide is written for a Java developer who has not seen Goldberry and has a
window to build. The decision log is written for the next maintainer. The two
voices differ, and the guide's rules are these.

**Lead with what the reader does.** The first paragraph of a chapter says what
the reader will be able to do at the end of it. An example comes before the
explanation of it. A widget's chapter shows the widget before it discusses it.

**Short sentences, one idea each.** About twenty words. Active voice, present
tense. No semicolons joining clauses. Parentheses and em dashes are for the
log; the guide starts a new sentence instead.

**The guide states what is, not how it came to be** (ADR-0567). A chapter
describes the toolkit as a reader finds it. It does not narrate a change
("is a module now", "no longer", "not yet", "used to"), does not strike an
item through, does not cite a record, a review, a gap, a milestone, a run or
a date, and does not name a working document under `docs/`. Where the toolkit
does not do something, the chapter says so in one sentence, in the present
tense, and gives the reason in words when it has one. The reference pages
`status.md` and `TODO.md` are the record by design and are exempt, and the
contributing chapters may describe the decision log and the working documents
because those are their subject. `BookTest` holds every other chapter to this.

**Every claim comes from the repository.** A KDL attribute is one the widget's
`inflate` reads. A Java constructor is one the record declares. A number is one
the repository measured. Nothing is invented to round a table out. The record
that explains a number or a limitation is found from the log's index, not from
the chapter.

**A widget's section is one shape.** Under a heading that is exactly its node
name in backticks, `## \`button\`` or `### \`radio-group\``:

1. one sentence saying what it is;
2. a `kdl` sample, then the Java that builds the same tree;
3. an *Attributes* table: attribute, type, default, what it does;
4. *Styling*: the CSS type, its parts, the pseudo-classes it matches, the
   variant classes;
5. *Keyboard*, where it takes any.

The heading is what `BookTest` looks for, so every `@Markup` name in the
repository has exactly one. A widget that has no markup name, `toast` or
`web-view`, is documented in the same shape under a plain heading.

**A Java sample closes a list on its own line.** When the arguments of a call
start on their own lines, the closing bracket is on a line of its own, at the
indent of the line that opened it, and a chained call follows it on the same
line. `BookTest` checks every `java` block for this.

```java
new Row(
        new Button("Cancel", this::dismiss),
        new Button("Delete", this::delete).styled("danger")
).styled("confirm");
```

**Samples inflate.** Every fenced `kdl` block in the guide is parsed and inflated
by `BookMarkupTest` against `Widgets.inflater()`, with nothing bound, so an
unknown node name or a refused attribute fails the build. A fragment that is
deliberately not a document, half a tree or a line of prose, is fenced as
`kdl,ignore`. Java samples are not compiled. They use the real names, and the
constructor they call is the one the record has.

**Callouts** are mdBook's GitHub-style alerts, and there are five:

```markdown
> [!NOTE]
> Context the reader may want.

> [!TIP]
> A shorter way, or a better default.

> [!IMPORTANT]
> A rule the toolkit enforces.

> [!WARNING]
> Something that fails quietly if forgotten.

> [!CAUTION]
> Something that costs a session or a build.
```

**Links** between chapters are relative and end in `.md`:
`../layout/scroll.md#keyboard`. mdBook rewrites them. The guide does not link
the log. Where a reference page has to, a record is linked on GitHub,
`https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/<file>`,
because the log is not built into the book. `BookTest` resolves every chapter
link, fragment included, refuses a relative link into `adr/`, and checks that a
GitHub record link names a file that exists.

**Pictures** are pairs. Every widget's section shows the widget before its
sample, as two files under `book/src/images/`, `<name>-light.webp` and
`<name>-dark.webp`, in one block:

```html
<div class="gb-shot"><img class="gb-light" src="../images/button-light.webp" width="363" alt="Five buttons in a row: …"><img class="gb-dark" src="../images/button-dark.webp" width="363" alt="Five buttons in a row: …"><p>The five variants.</p></div>
```

The page shows the one that matches the theme. The pictures are renders of
the section's own `kdl` sample at 2×, taken by `BookPicturesTest`
(ADR-0513), and `width` is the logical width, half the file's pixels, so the
widget is drawn at its own size. The alt text says what is in the picture,
not that it is a picture; `tools/book/visuals.py` carries one per widget and
writes the block. A whole screen has no `width` and fills the column. Five
pictures stay in one theme at 1× because they are states no sample reaches;
`BookTest` names them and refuses a sixth.

To retake every picture after a change to a stylesheet or a sample:

```bash
./gradlew :example:test --tests '*PicturesTest*' -Dgoldberry.golden.update=true
python3 tools/book/visuals.py        # places the pictures of any new widget
```

**Schemes are drawn pictures**, not box characters in a fence (ADR-0515).
A scheme is a function in `book/diagrams/diagrams.py`, rendered in both
themes by `python3 book/diagrams/draw.py` into `images/diagram-<name>-*.webp`
and shown with the same block. A listing a reader copies, a directory tree or
a timeline, stays in a fence.

**A sample pair is a tab group** (ADR-0514). The `kdl` sample and the Java
that builds the same tree, and the CSS that reaches it when there is one, are
wrapped in a `gb-tabs` block with blank lines around each fence:

````markdown
<div class="gb-tabs">

```kdl
button "Save"
```

```java
new Button("Save");
```

</div>
````

The script turns it into tabs and remembers the reader's language across the
book. `BookTest` holds every adjacent `kdl` and `java` pair in the catalogue
to it. A pair in the developer guide that is two things, a document beside
the class that loads it, is not tabbed.

**HTML blocks** are the ones `goldberry.css` defines and no others:
`gb-lede`, `gb-cards`/`gb-card`, `gb-shot`, `gb-tabs`, `gb-pill`, `gb-stats`,
`gb-steps`. Markdown inside an HTML block is not rendered, so a card's text is
plain; a `gb-tabs` block is the exception, because blank lines close the HTML
block before each fence.

## Found while writing

What the writers found when they read the sources against the specifications
and the records. None of it is fixed here; each is a line for the next pass.
Code first, documents after.

**Code**

- `KdlInflater` accepts a property nothing reads, so `row gap=8` draws a row
  with no gap. Recorded in `TODO.md` under *The catalog* (ADR-0511).
- `MediaPlayerView`, `VideoView`, `AudioPlayer`, `MediaControls` and `Canvas3d`
  wrap `wiring.handle(...)` in `requireNonNull`, so a `player=` or `renderer=`
  node refuses a preview with nothing bound, unlike `form` without a
  `controller=`. The guide fences those samples `kdl,ignore`.
- `Table` passes `null` for `text` and `itemMenu` to `ListView`, so a table has
  no type-ahead and no item menus, though `Table.java`, ADR-0214 and
  `core-widgets.md` §10 say it inherits them. `Table` has no no-argument
  `virtualized()`, so it cannot follow `--gb-list-row-height`.
- `Statistic.inflate` passes `null` for the sparkline, so a `sparkline` child
  in markup is dropped silently.
- `item checked="true"` with a string makes a checkable row that is off, silently.
- `hud readings="fps frame paint"` throws: there is no `frame` reading. The
  sample is in `Hud.java`, `core-widgets.md` and ADR-0101.
- A menu `item`'s accelerator is drawn as typed, so `Primary+S` shows literally.
- `step:hover { color: var(--gb-text) }` overrides `step.error`'s red on
  read-only steps. `tab:disabled` is dead CSS. Classes set by code with no rule:
  `tab-pager.start/.end`, `carousel.rotating`, `carousel-dot.current`,
  `collapse.open`, `column.accordion`.
- `controls.css` has no `chip:disabled` rule in the disabled-opacity list, so a
  disabled chip stops responding but does not fade.
- `Steps.resolvedCurrent()` gives `-1` for a non-numeric bound value and
  `Wizard.resolvedCurrent()` gives `0`. `Tours.start` returns `null` for an
  empty list. `Toasts.at` called twice leaves two stacks.
- Nothing focuses a tour when it opens, so its `Right`, `Left` and `Escape` may
  only work after a click inside the card. Worth a run.
- `widgets/.../form/textarea/TextAreaBox.java.orig` is checked in beside the source.
- `:example:blessGoldens` does not set `goldberry.native.library`, so it skips
  every golden on a machine where `test` finds the library through
  `hostLibrary`; `:example:test -Dgoldberry.golden.update=true` is what works.
- The guide's samples name an icon `grid` that Lucide calls `layout-grid`, and
  `home` that it calls `house`; `PreviewValues` maps both for the pictures.

**Documents**

- `README.md`: "72 widgets behind 74 markup names" was stale and is fixed here;
  "there is no `img` widget yet" in *Images* is stale (`image`, ADR-0358); the
  canvas `Editor` note "IME inline composition is the one piece not there yet"
  is closed by ADR-0289; the layers fade numbers are in ADR-0072, not ADR-0071.
- `docs/core-widgets.md`: §2 describes `text` attributes `wrap=`, `ellipsis`,
  `max-lines` and `span` children that `Text.inflate` does not read; §3 says
  `button` has `default=#true` and `cancel=#true`, which `Button.inflate` does
  not read; §4 still says `time-picker` is not built; §5 says the carousel's
  focus-within pause and the statistic's sparkline are unbuilt; §8 says bare
  `Alt` is not an activation key (ADR-0223); §10 lists `table` as deferred and
  writes `checkable=` as a KDL attribute; the crumb attribute is `press=`.
- `docs/design-system.md` row 234 says the steps connector animates
  `background-color`; ADR-0356 made it `scaleX`.
- `docs/charts.md` §3.1 marks the built rows as "v1".
- `docs/content-widgets.md` §1.3 shows `src=` on both views, which is not built.
- `docs/testing.md` §2 says SpotBugs is not wired, and §6 counts 34 of 93
  packages as null-marked; both are stale against ADR-0497 and the conventions
  plugin.
- `docs/ARCHITECTURE.md` §1 gives the GPU device as 20 ms; ADR-0506 has
  190–320 ms on NVIDIA's Vulkan driver.
- `book/src/status.md` credits the 3.13 ms median and 4.28 ms p95 to ADR-0047,
  which has neither figure.
- `.github/workflows/release.yml` cites ADR-0426 for the bump job; it is ADR-0421.
- `Row`'s and `Column`'s javadoc samples write `gap=8`, which nothing reads.
  `Scroll`'s javadoc and the `scrollbar` comment in `controls.css` still say the
  reserved gutter is not built (ADR-0364 built it). `Menus.java` and `Menu.java`
  say `Escape` closes the whole stack; ADR-0233 steps out one menu. `Panel.java`
  says the panel sets no background; ADR-0166's rule does. `Skeleton.java` uses
  pre-ADR-0414 class names. `Statistic.java` suggests a `statistic.up` selector
  nothing sets. `overlays.kdl` cites ADR-0192 for the menu bar; that record is
  about chips.
- `docs/ARCHITECTURE.md`: §6.3 says an `Icon` must be closed exactly once
  (stale since ADR-0277); §7.2 says arrow-key composites are not implemented
  (ADR-0073); §10 says contrast is specified and not implemented
  (`ContrastTest`); §13 says text scale is not gallery-enforced
  (`GalleryTextScaleTest`).
- `docs/design-system.md` §3 metrics disagree with `controls.css`: menu row
  28/24 against 32/28, card padding 16 against 12, tab height 36 against 32.
- `README.md` *Density* says `density-compact.css` is three custom properties;
  it declares eight.
- `Paints.Context.paragraph` says RTL text throws; ADR-0218 approximates it.
  `TestFrames` says `Frame.end()` is package-private; it is public.
- An application cannot select the `headless` backend: `GoldberryRuntime.install`
  is package-private. `textScale` lives on `WidgetRenderer` and the launcher
  does not expose it. Both are stated in the guide as they are.

## Status

| Step | State |
|---|---|
| `SUMMARY.md` restructured into the six parts, the reference and the log | done 2026-10-01 |
| `book.toml`: `light`/`navy` with the page's tokens, fold | done 2026-10-01 |
| The log out of the book, read on GitHub; record links point there | done 2026-10-01 (ADR-0512) |
| Full width, type in px, the landing page's header | done 2026-10-01 (ADR-0512) |
| `theme/goldberry.css`, favicon | done 2026-10-01 |
| Overview (3), Getting started (4) | done 2026-10-01 |
| Layout (9) | done 2026-10-01 |
| Components (16) | done 2026-10-01 |
| Performance (4) | done 2026-10-01 |
| Developer guide (10 new, 3 existing) and Contributing (6) | done 2026-10-01 |
| `BookTest`: chapters, links, headings for every `@Markup` name | done 2026-10-01 |
| `BookMarkupTest`: every `kdl` sample inflates | done 2026-10-01 |
| `site/content.js` links point at the new chapters | done 2026-10-01 |
| Pictures: every widget from its sample, both themes, 2×, held to the code | done 2026-10-01 (ADR-0513): 72 widgets, 9 screens; 8 headings not pictured, each with its reason in `BookPicturesTest` |
| Sample pairs as tabs | done 2026-10-01 (ADR-0514): 91 groups, remembered per reader |
| Schemes as drawn pictures | done 2026-10-01 (ADR-0515): the layers, the frame loop, the flow of values, the scroll nodes |
| Compiled Java samples | not built. A sample is a fragment; compiling one means a harness that supplies its imports and its surrounding class, and the names in it are checked by reading, not by `javac` |
| Links inside the book checked on the built site (`lychee`) | not built; `BookTest` checks the sources, which is the same set of links |
| The guide neutral: no record citations, no change narrative, no struck items, no gaps or working documents in a reader chapter; `BookTest` holds it | done 2026-10-06 (ADR-0567): notes in `doc-neutral-2026-10-06.md` |
