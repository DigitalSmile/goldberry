# Documents held back until the release

The guide and the site are not adjusted between releases. Everything a change
would have put into `book/` goes here instead, and moves into the book in the
release commit. This folder holds the three batches that closed the Gwent clone's issue list:
GB-005 to GB-012, written 2026-10-05 and 2026-10-06, GB-013 to GB-018,
written 2026-10-06, and GB-019 to GB-031, written 2026-10-08. It also holds the
unread-property check of 2026-10-10 (ADR-0587), which answers a `TODO.md` entry
rather than an issue, and GB-032 and GB-033, written 2026-10-10 (ADR-0588 and
ADR-0589), and the CI change of 2026-10-10 that builds only the landing
page for a change to `site/` alone (ADR-0590).

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
