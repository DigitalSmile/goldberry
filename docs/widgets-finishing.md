# Finishing the widgets

Working notes for the `image` widget and for the parts of built widgets that
`book/src/TODO.md` still listed as missing on 2026-09-17. One ADR per decision,
in `book/src/adr/`.

Status legend: **done** means code, tests and ADR have landed. **in progress**
means it is being built now. **open** means not started. **answered** means
decided not to build, with the reason in the ADR.

## The new widget

| Item | What it asks for | ADR | Status |
|------|------------------|-----|--------|
| `image` | §1: path, classpath, bytes or async source; `contain \| cover \| fill \| none`; DPI variants; loading and error states; alt text | 0358 | done |

## Scrolling

| Item | What it asks for | ADR | Status |
|------|------------------|-----|--------|
| `text-area` scrollbar | §4's "scrollbar beyond" the maximum rows | — | open |
| Reserved gutter | §2.4's "always show scroll bars", and something to switch it | — | open |
| A reveal glides | §3.1's `scroll` motion when a row is scrolled into view | — | open |
| A reveal moves one axis at a time | a wide table revealing a cell | — | open |

## `table`

| Item | What it asks for | ADR | Status |
|------|------------------|-----|--------|
| Column resizing | drag a header edge | — | open |
| Sticky header | the header stays while the rows scroll | — | open |
| Cell focus | arrow keys between cells | — | open |
| Horizontal virtualization | columns off screen are not built | — | open |

## `tabs`

| Item | What it asks for | ADR | Status |
|------|------------------|-----|--------|
| Overflow chevrons | a strip wider than its tabs scrolls by buttons | — | open |
| Reordering | drag a tab to a new place | — | open |
| Content kept | a tab selected again is not rebuilt | — | open |

## `select` and `menubar`

| Item | What it asks for | ADR | Status |
|------|------------------|-----|--------|
| Tree-select typeahead | typing inside the open tree list | — | open |
| Field width | as wide as the widest option, not the current one | — | open |
| Menubar `Left`/`Right` | move between menus while one is open | — | open |

## `affix`

| Item | What it asks for | ADR | Status |
|------|------------------|-----|--------|
| Pushed out by the next | a sticky header yields to the one after it | — | open |
| Both axes | pin on a row and a column at once | — | open |

## Controls

| Item | What it asks for | ADR | Status |
|------|------------------|-----|--------|
| `knob` circular drag | §3's "rotary" mode | — | open |
| `toggle` thumb follows the pointer | during the drag | — | open |
| `toggle` compact density | the thumb and track shrink | — | open |
| `code-input` arrow keys | move between boxes | — | open |
| `code-input` copy and cut | the whole code | — | open |

## Markup

| Item | What it asks for | ADR | Status |
|------|------------------|-----|--------|
| `list` | a KDL element | — | open |
| `table` | a KDL element | — | open |
| `tree` | a KDL element | — | open |
| `tour` | a KDL element | — | open |
| `toast` | a KDL element | — | open |
| Autocomplete | on `text-input` and `select` from KDL | — | open |

## What each one touched

### `image`

- `widgets` `core/image/` — `ImageView` (`@Markup("image")`), `ImageSource`,
  `Variant`, `Fit`, `ImageLoader`, and the package-private `ImageCache`,
  `ImageState`, `ImageLoad`, `ImagePaint`, `ImageBox`, `ImageFigure`,
  `ImageAlt`, `ImageErrorIcon`. `module-info` exports the package;
  `Primitives.builtInTypes()` gains `image`.
- `controls.css` — the `image` section.
- Tests: `FitTest`, `VariantTest`, `ImageCacheTest`, `ImagePaintTest`,
  `ImageViewTest`, `ImageGoldenTest` (`image-dark`, `image-light`).
- `example` `ui/CanvasScreen` — "The image widget" card; `showcase.css`;
  `gallery-canvas` re-blessed.
