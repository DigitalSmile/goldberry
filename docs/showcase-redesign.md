# The showcase redesign

Working notes for the showcase's new shell: a grouped sidebar instead of a
thirty-tab strip, search over every screen, card and command, and a larger type
scale. Asked for on 2026-10-11 ("the showcase app became monstrous"); the plan
was agreed the same day with three answers: the menu bar folds into an overflow
menu, body text is 15px, and the screens are grouped into the eight sections
below.

The decisions are ADR-0624 (the shell), ADR-0625 (search), ADR-0626 (the
type scale and the reading size) and ADR-0627 (a stale masonry wraps rather
than squeezes), parked in `docs/snapshot/adr/` until the release.

## Status — 2026-10-11

All five phases are done, and the second round below; committed to master on 2026-10-11.

| Phase | Piece | State | Where |
|---|---|---|---|
| 1 | Type scale 15/22, reading size S/M/L as a token sheet | **done** | `ui/shell/ReadingSize`, `app.reading`, `Showcase.stylesheets()` |
| 1 | One top bar: sidebar toggle, brand, search field, light, overflow menu | **done** | `ui/shell/TopBar`, `AppMenu.bar` (one **Menu** heading over File, Edit, View, Help) |
| 1 | Footer: the run's readings | **done** | `statusbar.kdl` |
| 1 | Wider walls, prose at body size, a line-length cap | **done** | `Wall.COLUMN_WIDTH` 440, `Wall.wide`, `showcase.css` |
| 2 | Sections, icons, the sidebar and its rail mode | **done** | `ui/gallery/Section`, `GalleryTab`, `ui/shell/Sidebar` |
| 2 | Home screen | **done** | `ui/home/HomeScreen` |
| 2 | History, next and previous screen | **done** | `ui/shell/NavHistory`, `ui/shell/PageHead`, `ShowcaseModel.Actions` |
| 3 | Search index, built by a test and shipped as a resource | **done** | `ui/search/SearchIndex`, `search/index.tsv`, `SearchIndexTest` |
| 3 | Matcher and ranking | **done** | `ui/search/FuzzyMatcher`, `SearchRanking` |
| 3 | The palette, `Ctrl+K`, and the jump to a card | **done** | `ui/search/CommandPalette`, `SearchPalette`, `ui/shell/CardSpotlight` |
| 4 | Polish: "On this page", hover, selection, ring | **done** | `ui/shell/JumpBar`, `chapter-shell.css`, `chapter-search.css`, `chapter-home.css` |
| 5 | Goldens retaken, ADRs parked, notes closed | **done** | 35 gallery goldens (one on the GPU lane), 5 card and tour goldens and 18 guide screen pictures retaken; `ShellGoldenTest` added; ADR-0624..0626 in `docs/snapshot/adr/` |

### Tests

New: `NavHistoryTest`, `ReadingSizeTest`, `SidebarTest`, `CardSpotlightTest`,
`ShellGoldenTest` (`ui/shell`); `FuzzyMatcherTest`, `SearchRankingTest`,
`SearchIndexFormatTest`, `SearchIndexTest`, `RecentPicksTest`,
`CommandPaletteTest` (`ui/search`); `HomeScreenTest`; `GallerySectionsTest`;
`ShowcaseNavigationTest`; then `WebViewFirstFrameTest` (`:widgets`) and
two masonry cases in `MasonryTest`. `GuideChapterTest` became
`HomeScreenTest.guide`. Updated: `ShowcaseShellTest` (overflow menu, seven
handlers, View, the new bands), `ShowcaseTypographyTest`,
`ShowcaseDocumentsTest`, `GalleryTextScaleTest`, `IconsScreenTest`.
`./gradlew :example:check` is green (881 tests, and the GPU lane's
`gallery-gpu-drawn`, retaken on lavapipe and passing on NVIDIA too), as are
`SourceDocsTest` and `BookTest`. `:example:run` on the module path with the dummy driver opens every
screen; it logs `search covers 247 cards on 23 screens` (after the second
round below; it was 271 on 24 while the Guide screen existed).

## The sections

| Section | Screens |
|---|---|
| Basics | Layout, Scrolling, Text, Styling, Design system |
| Controls | Buttons, Choices, Values, Forms |
| Structure | Panels, Collections, Navigation, Menus, Overlays, Windows |
| Graphics & media | Charts, Drawing, Icons, Emoji, GPU, Audio, Video |
| Content | Markdown, HTML, Web view |
| Platform | Input, Diagnostics, Application |
| Demos | Lookalike |

Home sits above them and is not one of the gallery's screens: it has no
chapter, no digit and no row in Edit ▸ Go to. The guide's reading chapters,
which were the Guide screen, are links at the bottom of Home.

## Shortcuts

| Keys | What |
|---|---|
| `Ctrl+K` | Search |
| `Ctrl+B` | Sidebar: full or rail |
| `Alt+←` / `Alt+→` | Back / forward |
| `Ctrl+PgUp` / `Ctrl+PgDn` | Previous / next screen |
| `Ctrl+1` … `Ctrl+0` | The first ten screens, in sidebar order |
| `Ctrl+=` / `Ctrl+-` | Larger / smaller reading size |
| `Ctrl+L` | March a league (was `Ctrl+K`) |

## Decisions taken on the way

- **The card list is a resource, not a runtime walk.** A card exists only once
  its screen is built, and the window builds one screen at a time. The test
  that builds every screen writes the list and fails when it is stale
  (ADR-0625).
- **The light switch left the footer for the Choices screen's theme card.** The
  top bar has a sun/moon button. `toggle bind="app.light"` is a markup
  demonstration the documents test asks for, so it stays in a document, beside
  the three pickers that read the same light by name.
- **`NavHistory` is in `ui/shell`, not `ui/navigation`.** `ui/navigation` is the
  Navigation chapter's package, and history belongs to the window.
- **`Ctrl+0` is Panels now**, because the screens are listed section by
  section. "March a league" moved from `Ctrl+K` to `Ctrl+L`.
- **The text-scale audit runs against the Small reading size**, the toolkit's
  own scale, which is what the 90%–150% promise is about (ADR-0626).
- **Collections and Navigation use `Wall.wide`** (columns of at least 560px).
  Their two fixed columns cut the table and the steps beside the sidebar.
  **Scrolling** takes 300px columns, and its viewports are 200px tall, so its
  three cards fit a 1280×800 window.
- **The icon sheet opens at six columns**, which is what fits beside the
  sidebar at the restored size.
- **The Markdown and HTML panes' captions no longer shrink**: they were drawn
  over each other once the preview's viewport asked for every pixel.

## Second round — 2026-10-11

Asked for after the first look, with a log of seven overflow warnings from the
Styling screen.

| Piece | State | Where |
|---|---|---|
| The overflow warnings: a masonry one frame stale squeezed its columns after the window narrowed | **done** | `MasonryBox` holds columns at their minimum and wraps (ADR-0627); `MasonryTest` |
| Wider cards for text areas | **done** | `ShowcaseCard.wide()`, `WideCard`, class `wide-card`; `Wall` deals them into a second wall at 560px columns. Forms (2), Text (1), Styling (3), Application (4) |
| A bigger video player | **done** | `#video-player` is `60vh`, at least 360px (`chapter-media.css`) |
| The web view did not open on the first visit | **done** | `WebView` asks for the frame that finds its box, up to eight times (`waitForABox`); `WebViewFirstFrameTest` |
| The Guide screen moves to Home, as links rather than cards | **done** | `HomeScreen.guide()`, `GuideParts` moved to `ui/home`; `ui/guide` and `chapter-guide.css` deleted; `GalleryDocsTest` counts Home's links; search offers "Guide: …" |

The warnings were not reproducible in an offscreen session at any fixed size,
density, scroll-bar setting or reading size. They need a width that shrinks
between two frames. The Styling log lines came from un-maximizing to
1280×800, which the masonry's own documentation describes as one frame late.
The web view waited on the same kind of frame: its box is read from the
previous frame, and an idle window never painted a second one.

## Loose ends

- `book/src/components/overlays.md` has a tour sample whose last stop is
  "The gallery strip". It is listed in `docs/snapshot/README.md` for the
  release, because the guide is not edited between releases.
- A window narrower than about 900px keeps the full sidebar until `Ctrl+B`.
  Switching to the rail automatically would need the window's width, which
  this shell does not measure.
- The floating button on the Buttons screen sits in the window's corner,
  over the footer's last badge.
