# Documents held back until the release

The guide and the site are not adjusted between releases. Everything a change
would have put into `book/` goes here instead, and moves into the book in the
release commit. This folder holds the three batches that closed the Gwent clone's issue list:
GB-005 to GB-012, written 2026-10-05 and 2026-10-06, GB-013 to GB-018,
written 2026-10-06, and GB-019 to GB-031, written 2026-10-08. It also holds the
unread-property check of 2026-10-10 (ADR-0587), which answers a `TODO.md` entry
rather than an issue, and GB-032 and GB-033, written 2026-10-10 (ADR-0588 and
ADR-0589), and the CI change of 2026-10-10 that builds only the landing
page for a change to `site/` alone (ADR-0590), and GB-034, written 2026-10-10
(ADR-0598).

## What is here, and where it goes

| File | Destination at release | Note |
|---|---|---|
| `adr/0561-…` to `adr/0568-…` | `book/src/adr/` | Eight records. `DecisionLogTest` wants the numbers contiguous from the highest in the log at that time: renumber if records landed in between, and fix the cross-references between them (0562 ↔ 0563, 0563 ↔ 0566) |
| `adr/README-lines.md` | appended to `book/src/adr/README.md` | One line per record, in order |
| `components-gpu.md` | `book/src/components/gpu.md`, before `## What is measured, and what is not yet` | Three sections: the texture model (with sample counts and a sampler per slot), compute and storage, rendering offscreen (with sessions and strips). The Java samples pass `BookTest`'s bracket rule as written |
| `performance-measuring.md` | `book/src/performance/measuring.md` | One sentence and one table row for `--capture=` |
| `adr/0568-…` | `book/src/adr/` | Metal shader bindings follow SDL's order; the fix for the macOS GPU lane of 2026-10-06 |
| `adr/0567-…` | `book/src/adr/` | The guide is neutral: the rewrite itself went into `book/` on 2026-10-06 at the user's request, with the `BookTest` guard; only this record waits here |
| `adr/0569-…` to `adr/0574-…` | `book/src/adr/` | Six records for GB-013 to GB-018. Contiguous with 0568; renumber with the rest if records landed in between, and fix the references 0571 → 0564, 0573 → 0562 and 0566, 0574 → 0563 |
| `guide-writing-a-widget.md` | `book/src/guide/writing-a-widget.md`, in `## The three shapes` | One paragraph and a sample: `State.context()` from `initState` |
| `adr/0575-…` to `adr/0586-…` | `book/src/adr/` | Twelve records for GB-019 to GB-031 (GB-019 and GB-020 share 0577). Contiguous with 0574; renumber with the rest if records landed in between, and fix the references 0575 → 0265 and 0272, 0580 ↔ 0581, 0584 ↔ 0579, 0585 ↔ 0584 |
| `guide-input-accelerators.md` | `book/src/guide/input.md`, `## Accelerators`, after the owner paragraph | `Repeat.IGNORE` for toggles, and `session.hold` |
| `components-rich-text.md` | `book/src/components/text.md`, between `## \`text\`` and `## \`link\``; two rows and the card list in `components/index.md`; one sentence in `### Wrapping and cutting`; the `Rich text` row of `overview/limitations.md` | GB-021. `BookTest` reads its headings and catalogue rows from here until it moves, and fails on a name documented in both places, so delete it with the folder. `:example:test` takes `rich-text-{light,dark}.webp` once it is in the book. The doc comments of `RichText` and `Run` switch from `#text` to `#rich-text` then |
| `styling-images.md` | `book/src/guide/styling.md` (`### Backgrounds and gradients`, end of `### Border, outline and shadow`) and `book/src/components/drawing.md` (`## image`, `src` row) | GB-024 to GB-026, three pieces, each says where it goes: `url()` layers with size and repeat, `border-image`, regions of a sheet |
| `adr/0587-…` | `book/src/adr/` | The inflater refuses a property nothing reads. Contiguous with 0586; renumber with the rest if records landed in between |
| `guide-markup-unread-properties.md` | `book/src/guide/markup.md`, end of `### Strict by default`; `book/src/layout/row-and-column.md`, the `> [!WARNING]` box under the `row` table | Two pieces, each says where it goes. The warning box says `row gap=8` "parses and does nothing", which stopped being true on 2026-10-10 |
| `adr/0588-…` and `adr/0589-…` | `book/src/adr/` | GB-032 (a painter's nine-slice) and GB-033 (cursor pictures, a drag following its holder's cursor, native ABI 22). Contiguous with 0587; renumber with the rest if records landed in between. 0589 links ADR-0057 as a sibling, which resolves once it is in `book/src/adr/` |
| `components-drawing-nine-slice.md` | `book/src/components/drawing.md`, `### The painter`, after the stroke paragraph | GB-032: `Frame.drawNineSlice` and `NinePatch` |
| `guide-input-cursors.md` | `book/src/guide/input.md` (`## The cursor`), `book/src/overview/limitations.md` (the *Custom image cursors* row), `book/src/status.md` (one phrase) | GB-033, three pieces, each says where it goes. The guide's paragraph says custom image cursors are not built, which stopped being true on 2026-10-10 |
| `adr/0590-…` | `book/src/adr/` | A change to `site/` alone runs `pages.yml` only, which runs `SiteTest`; `book/` is never ignored. Contiguous with 0589; renumber with the rest if records landed in between |
| `contributing-testing-ci-matrix.md` | `book/src/contributing/testing.md`, `## The CI matrix` | Five trigger cells, the `pages.yml` row, and one paragraph after the per-OS paragraph |
| `guide-logback-version.md` | `book/src/getting-started/installing.md` and `book/src/guide/logging.md`, the `runtimeOnly` line in each | Logback 1.6.3 → 1.6.5 in the two snippets, for the MDC path-traversal CVE whose 1.6.3 fix was incomplete |
| `adr/0591-…` | `book/src/adr/` | G50: a drag from one widget onto another. Contiguous with 0590; renumber with the rest if records landed in between, and fix the cross-reference 0591 ↔ 0592 |
| `guide-input-dragging.md` | `book/src/guide/input.md`, a new `## Dragging between widgets` after `## Dropped files and text`; a `:drag-over` row in the pseudo-class table of `book/src/guide/styling.md`; the *Platform drag and drop* row of `book/src/overview/limitations.md` | G50, three pieces, each says where it goes |
| `adr/0592-…` | `book/src/adr/` | G51: real widgets placed over a `canvas`. Contiguous with 0591; fix the cross-references 0592 → 0575 and 0591 |
| `components-drawing-canvas-overlay.md` | `book/src/components/drawing.md`, under `## canvas`: a new `### Widgets over the drawing` after `### Input`, and one sentence each at the end of `### Styling` and `### Keyboard` | G51, three pieces. It links `../guide/input.md#dragging-between-widgets`, which exists once `guide-input-dragging.md` has moved in |
| `adr/0593-…` | `book/src/adr/` | G52: the current crumb gives way with an ellipsis. Contiguous with 0592 |
| `components-navigation-crumb-ellipsis.md` | `book/src/components/navigation.md`, `## breadcrumbs`, after the folding paragraph | G52 |
| `adr/0594-…` | `book/src/adr/` | G55: Lottie read in Java and drawn by the toolkit's painter; the `webm` half investigated. Contiguous with 0593 |
| `components-drawing-vector-animation.md` | `book/src/components/drawing.md`, a new `### Vector animations` under `## image`, after its `### Keyboard` and before `## qr-code` | G55. `VectorAnimation` and `AnimationView`; no markup heading, because there is no markup node |
| `adr/0595-…` | `book/src/adr/` | G57: a fallback face chosen by its `cmap`. Contiguous with 0594; renumber with the rest if needed. It links ADR-0393 as a sibling |
| `guide-text-fallback-faces.md` | `book/src/guide/text.md`: the last sentence of `### The bundled faces`, and a new `### Fallback faces` after `### Shipping a face` | G57, two pieces. The guide's "no fallback cascade beyond the emoji slot" stopped being true on 2026-10-10 |
| `adr/0596-…` | `book/src/adr/` | G58: a rounded clip. Contiguous with 0595 |
| `layout-sizing-rounded-clip.md` | `book/src/layout/sizing.md`, replacing "`overflow: hidden` clips." | G58 |
| `components-gpu-rounded-clip.md` | `book/src/components/gpu.md`, the `canvas3d` `### Styling` sentence | G58: inside a rounded clip it shows its no-GPU fallback |
| `adr/0597-…` | `book/src/adr/` | G59: a list whose rows vary. Contiguous with 0596 |
| `components-collections-measured-rows.md` | `book/src/components/collections.md`, `## list`, after the "Rows are virtualized by row height" paragraph, and one row of the Java table | G59 |
| `adr/0598-…` | `book/src/adr/` | GB-034: `text-align: left` and `right` read as sides of the box, `justify` refused with its reason. Contiguous with 0597 |
| `guide-styling-text-align.md` | `book/src/guide/styling.md`, `### Text flow` | GB-034: replaces the two lines that say `left` and `right` are refused |
| `adr/0599-…` | `book/src/adr/` | G55's `webm` half: the alpha decoded beside the picture, drawn on the CPU, played by `AnimationView` through `MovingPicture`. Contiguous with 0598; it relates to 0594 |
| `components-media-video-sticker.md` | `book/src/components/media.md`, a new `### A video sticker` after `### Bringing a codec`, and one paragraph for the `AnimationView` section parked in `components-drawing-vector-animation.md` | G55 `webm`, two pieces, each says where it goes |
| (no file) | `book/src/TODO.md`, the font-fallback entry and the `progress` cap that squares off | G57 and G58 answer them: a line each, citing ADR-0595 and ADR-0596. Back the file up first |
| (no file) | `book/src/TODO.md` | The entry **The inflater accepts a property nothing reads**, under *The catalog: specified and unbuilt*, moves to *Answered* with a closing paragraph citing ADR-0587. Back the file up first |

## Doc comments that point at the guide

`OffscreenGpu` and its `package-info` say "Read more: The GPU canvas" with the
`#canvas3d` anchor for now. When `components-gpu.md` goes in, the anchor
becomes `#rendering-offscreen`, which is the section written for them.
`SourceDocsTest` and `tools/book/guide_links.py` check the links then.

## Order of operations at release

1. Move the ADRs in, renumbering if needed; append the index lines; run
   `./gradlew -p build-logic test --tests '*DecisionLogTest'`.
2. Paste the guide sections, the measuring additions and the widget-guide
   paragraph; run the `BookTest` and `SourceDocsTest` guards from the same
   task.
3. Switch the two doc-comment anchors; run `:gpu:spotlessApply`.
4. Delete this folder.
