# Documents held back until the release

The guide and the site are not adjusted between releases. Everything a change
would have put into `book/` goes here instead, and moves into the book in the
release commit. This folder holds the two batches that closed the Gwent clone's issue list:
GB-005 to GB-012, written 2026-10-05 and 2026-10-06, and GB-013 to GB-018,
written 2026-10-06.

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
