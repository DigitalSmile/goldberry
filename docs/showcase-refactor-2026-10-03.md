# Showcase refactor: the gallery mirrors the guide (2026-10-03)

The decision is [ADR-0549](../book/src/adr/0549-the-showcase-is-the-guide-a-screen-per-chapter-and-a-card-per-section.md).
This file is the working notes and the status of the work.

## What was asked

1. A tab or a card for every widget, option and feature the guide documents.
2. A short description on each.
3. No decision-record citations in that text.
4. A link on each that opens the guide for more.

## The shape

| Piece | Where | What it is |
|---|---|---|
| `DocLink` | `example/…/docs` | A chapter and a heading of the guide, and the URL the site serves for them |
| `ShowcaseCard` | `example/…/ui/gallery` | A card: `row.card-head` (title, spacer, `link.card-docs` "Docs"), `text.card-summary`, then the demo. `reference()` is a card with nothing to click |
| `ScreenHeader` | `ui/gallery` | A screen's heading, its chapter link, and a line of prose |
| `Wall` | `ui/gallery` | A `ScreenHeader` over a masonry of cards. `Wall.of(id, title, summary, doc, cards)` lets the window choose the column count, with no column narrower than 360 |
| `Documents` | `ui/gallery` | The `.kdl` documents, inflated on first use and cached |
| `GalleryContext` | `ui/gallery` | What a screen is built from: model, actions, documents, the plus icon, the tour |
| `Gallery.TABS` | `ui/gallery` | The 29 tabs in the book's order; `Screen.GALLERY` is its names |
| `Summaries` | `ui/gallery` | A summary is ≤ 280 characters and cites no record |
| `ShowcaseStyles` | `example` | `showcase.css` plus one `chapter-<package>.css` per screen package |
| `ShowcaseTour` | `ui/overlays` | The tour's screen and stops |

A card written in KDL is the same tree:

```kdl
card id="button-card" class="wall-card" {
  row class="card-head" {
    text class="card-title" "Buttons"
    spacer
    link class="card-docs" href="https://goldberry.dev/docs/components/buttons.html#button" "Docs"
  }
  text class="card-summary" "A button runs an action when pressed. Its variant is a class."
  // the demonstration
}
```

## What has to have a card

`BookSections.required()` reads `book/src`:

- a widget chapter (`layout/*` except `index`, `components/*` except `index`):
  every `##`;
- a feature chapter (`applications`, `guide/{markup,styling,design-system,text,input,windows,logging}`):
  every `##` and `###`;
- every other chapter in `SUMMARY.md`: any link into it.

That is 230 sections: 39 chapter links and 191 headings. `GalleryDocsTest`
lists what is missing, chapter by chapter.

## The tabs and who builds them

| # | Tab | Package | Chapter(s) | Fit | Group |
|---|---|---|---|---|---|
| 1 | layout | `ui.layout` | layout/index, row-and-column, spacer, stack, split-pane, masonry, sizing | scrolled | A |
| 2 | scrolling | `ui.layout` | layout/scroll, layout/affix | fills | A |
| 3 | text | `ui.text` | components/text, guide/text (less icons, emoji, images, clipboard, picture) | scrolled | B |
| 4 | buttons | `ui.controls` | components/buttons | scrolled | B |
| 5 | choices | `ui.controls` | components/choices | scrolled | B |
| 6 | values | `ui.controls` | components/values | scrolled | B |
| 7 | forms | `ui.forms` | components/forms | scrolled | C |
| 8 | panels | `ui.panels` | components/panels | scrolled | C |
| 9 | collections | `ui.collections` | components/collections | scrolled | D |
| 10 | navigation | `ui.navigation` | components/navigation | scrolled | D |
| 11 | menus | `ui.menus` | components/menus | scrolled | D |
| 12 | overlays | `ui.overlays` | components/overlays | scrolled | D |
| 13 | charts | `ui.charts` | components/charts | scrolled | E |
| 14 | drawing | `ui.drawing` | components/drawing, guide/text#images, #the-clipboard, #a-picture-with-no-window | scrolled | E |
| 15 | icons | `ui.sheet` | guide/text#icons | fills | E |
| 16 | emoji | `ui.sheet` | guide/text#emoji | fills | E |
| 17 | markdown | `ui.content` | components/content#markdown-view | fills | F |
| 18 | html | `ui.content` | components/content#html-view | fills | F |
| 19 | web | `ui.content` | components/content#the-web-view | fills | F |
| 20 | audio | `ui.media` | components/media | scrolled | F |
| 21 | video | `ui.media` | components/media | scrolled | F |
| 22 | gpu | `ui.gpu` | components/gpu | scrolled | E |
| 23 | styling | `ui.styling` | guide/styling | scrolled | G |
| 24 | design | `ui.styling` | guide/design-system | scrolled | G |
| 25 | input | `ui.input` | guide/input | scrolled | H |
| 26 | windows | `ui.windows` | guide/windows | scrolled | H |
| 27 | diagnostics | `ui.diagnostics` | guide/logging | scrolled | I |
| 28 | application | `ui.application` | applications, guide/markup | scrolled | I |
| 29 | guide | `ui.guide` | every chapter that only needs a link | scrolled | I |

## Where the old screens' cards go

The old screens are read-only sources. A card moves to the group whose chapter it
shows. Files used by more than one group are **copied from, never edited**, and
are deleted at integration: `Basic.java`, `basic.kdl`, `Notifications.java`,
`Panes.java`, `Overlays.java`, `overlays.kdl`, `Forms.java`, `forms.kdl`,
`panels.kdl`, `Navigation.java`.

| Old card | Goes to |
|---|---|
| basic: type-card | G, design#type |
| basic: button-card, shapes-card, road-card (icon-only button), tag-card, chip-card, badge-card | B, buttons |
| basic: theme-card (radio-group, segmented, select on one value) | B, choices |
| basic: switch-card | B, choices (checkbox, toggle) |
| basic: value-card, report-card | B, values |
| basic: prose-card | B, text#paragraphs |
| basic: links-card | B, text#link |
| basic: FileDialogsCard | H, windows |
| panels: surface-card, group-boxes, numbers, loading, carousel, collapse, accordion | C, panels |
| panels: split-card | A, layout#split-pane |
| overlays: layer-card, Notifications (message kinds, summary, stack) | D, overlays |
| overlays: menubar-card, context-card | D, menus |
| forms.kdl, Choosers (multiple, autocomplete, tree select) | C forms; Choosers to B, choices#select |
| TextStylingCard | G, styling#typography |
| Navigation: TabsDemo | C, panels#tabs |
| Navigation: trail, WizardDemo | D, navigation |
| Navigation: Scrolling, Console | A, scrolling |
| Navigation: tour-card | D, overlays#tours |
| Collections, Chronicle (timeline) | D collections; Chronicle to C, panels#timeline |
| Charts, CanvasScreen, GpuScreen, sheet/* | E |
| Markdown/Html/WebScreen, samples, MediaScreen | F |
| MotionScreen, motion/* | G, styling#transition-and-animation |

## Status

| Step | State |
|---|---|
| Framework: `DocLink`, `ShowcaseCard`, `Wall`, `ScreenHeader`, `Documents`, `Gallery`, stubs | done |
| Tests: `DocLinkTest`, `BookSectionsTest`, `ShowcaseCardTest`, `SummariesTest`, `DocumentCardsTest`, `GalleryDocsTest`, `GalleryOrderTest` | done, green: all 230 sections have a card |
| ADR-0549 | written |
| Group A (layout, scrolling): 19 cards | integrated |
| Group B (text, buttons, choices, values): 32 cards | integrated |
| Group C (forms, panels): 24 cards | integrated |
| Group D (collections, navigation, menus, overlays): 22 cards | integrated |
| Group E (charts, drawing, icons, emoji, gpu): 25 cards | integrated |
| Group F (markdown, html, web, audio, video): 18 cards | integrated |
| Group G (styling, design): 39 cards | integrated |
| Group H (input, windows): 30 cards | integrated |
| Group I (diagnostics, application, guide): 57 cards | integrated |
| Legacy screens, their documents and their tests deleted | done |
| `ShowcaseDocumentsTest`, `GalleryGoldenTest`, `GalleryTextScaleTest` over every tab | done |
| Goldens, the guide's `screen-*` pictures, `gallery-drawing.png`, `gallery-gpu.png` (GPU lane, NVIDIA under X11) retaken | done |
| Book: introduction shows the Buttons screen; alt texts; masonry and contributing chapters | done |
| Dead `showcase.css` rules for ids no screen builds | removed |

## Found on the way

- **`TextScaleAudit` measured text without its flow.** It laid every paragraph
  out breaking between words only, so a paragraph under `overflow-wrap: anywhere`
  or `word-break: break-all` was reported cut at 150% although the render tree
  breaks it. It now lays out with the flow, as `RenderObject` measures;
  `TextScaleAuditTest` holds it.
- **150% text on narrow cards.** A row of buttons placed on a card wraps
  (`.wall-card > row`), the Docs link never shrinks, and Collections and
  Navigation keep two wide columns (`Wall.inColumns`) for their table and steps.
- **A masonry tie is scale-dependent.** Two columns ending level hand the next
  card to whichever rounding favours, which differs at 2x; the Buttons screen's
  order avoids the tie.
- **The Overlays golden shows the message cards empty.** It did before this work
  too: the banners are photographed mid fade-in. Not fixed here.
- **Unwoven models do not notice a direct Java call.** A card that calls an action
  from Java and subscribes to the result calls `Models.refresh` after it
  (`Swept`, `TrailCard`), as `Showcase` already does for its background job.

## Still to do

- Nothing: `check`, `-p build-logic check`, the GPU lanes (under X11 here) and `:example:run` on every screen are green.
