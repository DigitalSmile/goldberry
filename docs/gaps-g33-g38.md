# Closing G33–G38

Working notes for the sixth batch of `docs/gaps.md` entries — the six that were open when
`docs/gaps.md` was last written (2026-09-16). One ADR per entry, in `book/src/adr/`.

Status legend: **done** — code, tests and ADR landed. **in progress** — being built now.
**open** — not started.

| Gap  | What it asks for | ADR | Status |
|------|------------------|-----|--------|
| G33  | `onPointerEnter` / `onPointerExit` on any node | [0327](../book/src/adr/0327-a-hover-is-a-node-property-not-a-menus.md) | done |
| G34  | a read-only field opens at the head of its value | [0326](../book/src/adr/0326-a-value-you-cannot-type-into-opens-at-its-beginning.md) | done |
| G35a | GIF and WebP decode | [0329](../book/src/adr/0329-two-more-codecs-one-fetched-and-one-written.md) | done |
| G35b | `Window.onFileDrop` | [0330](../book/src/adr/0330-a-dropped-file-arrives-somewhere.md) | done |
| G36  | a `chip`'s dot takes a colour | [0328](../book/src/adr/0328-a-dots-colour-is-data.md) | done |
| G37  | a line-number gutter on `text-area` | [0331](../book/src/adr/0331-a-gutter-numbers-hard-lines-at-soft-positions.md) | done |
| G38  | an editing seam an application can talk to | [0332](../book/src/adr/0332-an-editor-is-handed-the-caret.md) | done |

## What each one touched

### G33 — hovering, for something that is not a menu item

- `core` `widget/attr/Attributes` — two new components, `onPointerEnter` and `onPointerExit`,
  with withers; the six-argument constructor is kept so no existing call site moves.
- `core` `widget/attr/Attributed` — the two chainable defaults.
- `core` `input/PointerRouter#hook` — run beside the widget's own `Handles.onPointer`, off the
  same derived `ENTERED`/`EXITED` that `:hover` is moved by, so a hook is about the **subtree**.
- Tests: `core` `input/HoverHookTest`.
- **Not** in markup: a `Runnable` is not a KDL value and `Attributes.of(KdlNode)` has no `Wiring`.

### G34 — a long value in a read-only field opens scrolled to its end

- `core` `text/edit/TextEdit#atStart` — `of`'s mirror.
- `widgets` `form/textinput/TextInputState#opening` — read-only starts at 0, on mount and on a
  value the application changes later. No new widget API, which was the proposal's second and
  better option.
- Tests: `widgets` `form/textinput/ReadOnlyCaretTest`.

### G35a — no codec for GIF or WebP

- `natives` CMake superbuild fetches **libwebp** and builds its `webpdecoder` target only;
  `WebPGetInfo`, `WebPDecodeRGBA` and `WebPFree` join the export list, bound with no C glue.
- `natives` `webp/Webp` binds them; the `MemorySegment` never leaves the call.
- `core` `image/gif/GifDecoder` — a **pure-Java** GIF decoder (first frame, LZW), because GIF's
  format is small and a second native dependency is not worth one.
- `core` `image/ImageFormat` sniffs the bytes and routes; `Image.decode` is unchanged for callers.
- Tests: `core` `image/CodecTest` — the same 32x32 picture written five ways, so the new
  codecs are asserted against the one that already worked rather than against a transcribed
  table. The ABI version goes to 11.

### G35b — a file dropped on a window raises nothing

- `core` `render/event/BackendEvent` gains `FileDropped` and `FileDropCompleted`; the SDL3
  backend translates the four `SDL_EVENT_DROP_*` types and remembers the last position.
- `core` `Window#onFileDrop(Consumer<FileDrop>)` returning a `Subscription`, and
  `core` `input/drop/FileDrop`. The gesture is assembled in `Window`, not in the backend.
- Tests: `core` `FileDropTest` (headless, no SDL), `natives` `sdl/SdlDropEventTest`.

### G36 — a `chip`'s dot takes no colour

- `widgets` `controls/chip/Chip#withDot(int)` plus a `dotColor` component; `ChipDot` paints it.
- `widgets` `markup/Wiring#colour(KdlNode, String, String)` so `dot-colour=`/`dot-color=` work.
- Tests: `widgets` `controls/chip/ChipDotColourTest`.

### G37 — a line-number gutter on `text-area`

- `widgets` `form/textarea/TextArea#gutter(boolean)`, `text-area gutter=#true` in markup.
- `widgets` `form/textarea/TextAreaGutter` is the strip; the numbers are drawn by `TextAreaBox`
  as **one** paragraph with a blank line per wrap, so they cannot drift from the text. Child
  number *nodes* would be a frame behind on every keystroke that changed the line structure.
- `controls.css`: `text-area-gutter` for the strip, `--gb-gutter-color` and `--gb-gutter-gap`
  on `text-area` for the ink and the room (§8 has no per-edge border, so no rule down the edge).
- Tests: `widgets` `form/textarea/TextAreaGutterTest`.

### The showcase

Two of the seven are visible in `:example` rather than only in a test:

- **Canvas → "Five formats, one picture"** (`CanvasScreen.paintCodecs`). The same 96×64
  picture decoded from PNG, QOI, WebP, GIF and JPEG and drawn side by side, each labelled
  with what `ImageFormat.of` read out of the file's **first twelve bytes** — never its
  name. The five tiles are meant to look identical; GIF (quantized to 255 colours) and
  JPEG (lossy) are the two that do not quite, and the card says so.
  `ImageFormat` gained a `QOI` case here: PNG and JPEG were already named without being
  re-routed, so QOI's absence was an inconsistency that showed up as `UNKNOWN` on a card
  whose whole point is that the bytes decide.
- **Forms → "Numbered lines"** (`forms.kdl`, `#gutter-card`). A `mono` `text-area` with
  `gutter=#true` over a document written so that **one hard line wraps** — the long
  paragraph takes one number and three lines' height, and the numbers below it are where
  the wrap put them. A document of short lines would show a column of numbers and prove
  nothing.

The `gallery-forms` golden is 1200×1500 rather than 1200×900, because the gutter card sits
below the fold at 900 and a golden that stopped there would not photograph the one thing it
exists to show.

### G38 — an editing seam an application can talk to

- `widgets` `form/textarea/TextArea#onEdit(Consumer<TextEdit>)` and `#edit(TextEdit)`.
- `change=` is untouched; `onEdit` fires beside it, on caret moves and selections as well as on
  text changes.
- Tests: `widgets` `form/textarea/TextAreaEditSeamTest`.
