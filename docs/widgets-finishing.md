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
| Column resizing | drag a header edge | 0361 | done |
| Sticky header | the header stays while the rows scroll | 0360 | done |
| Cell focus | arrow keys between cells | — | answered: §10's table is a grid of rows; a spreadsheet is another widget (ADR-0214) |
| Horizontal virtualization | columns off screen are not built | — | answered: pays past about fifty columns, past where a table is the right widget (ADR-0214) |

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
| Field width | as wide as the widest option, not the current one | 0359 | done |
| Menubar `Left`/`Right` | move between menus while one is open | — | open |

## `affix`

| Item | What it asks for | ADR | Status |
|------|------------------|-----|--------|
| Pushed out by the next | a sticky header yields to the one after it | 0360 | done |
| Both axes | pin on a row and a column at once | — | open |

## Controls

| Item | What it asks for | ADR | Status |
|------|------------------|-----|--------|
| `knob` circular drag | §3's "rotary" mode | — | open |
| `toggle` thumb follows the pointer | during the drag | — | answered: design-system §1.7 lists the 1:1 drags and a toggle is not one; §3.1 asks for a thumb `translate` instead |
| `toggle` compact density | the thumb and track shrink | — | answered: §3's `toggle` row gives no compact value, unlike every row that has one |
| `code-input` arrow keys | move between boxes | — | answered: §4 makes a code one textbox with one insertion point, so there is nowhere for an arrow to go |
| `code-input` copy and cut | the whole code | — | answered: §4 gives a masked code no way out, and copy is one |

## Markup

| Item | What it asks for | ADR | Status |
|------|------------------|-----|--------|
| `list` | a KDL element | — | open |
| `table` | a KDL element | — | open |
| `tree` | a KDL element | — | open |
| `tour` | a KDL element | — | open |
| `toast` | a KDL element | — | open |
| Autocomplete | on `text-input` and `select` from KDL | — | open |

## Decisions taken without an answer

Asked on 2026-09-17 and not answered, so the recommended option was taken and
can be reversed:

- The five items above marked **answered** are not built, because the design
  system or the spec records a decision against each.
- A tab's content stays lazy by default; keeping it is an opt-in `keep-alive`.
- An affix on two axes, dragging tabs to reorder, and a reveal that scrolls one
  axis first are opt-in options with the defaults unchanged.

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

### `select` field width

- `widgets` `controls/select/SelectValue` — `widths`, shaped in `render`;
  `SelectField` carries them; `SelectState.widths`.
- Tests: `SelectWidthTest`. Five `select-*` goldens re-blessed.

### `affix` inside its container, and the table's sticky header

- `core` `input/handler/Located#located(self, clip, container)`;
  `input/PointerRouter.notifyLocated` finds the nearest ancestor with a region.
- `widgets` `core/affix/AffixState` clamps its travel; `AffixSlot` passes the
  container. `panel/table/Table` wraps its head and rule in an `Affix`.
- Tests: `AffixTest` "inside a section"; `TableStickyHeaderTest`.

### Table column resizing

- `widgets` `panel/table/Column#resizable`, `Table#resized`; `TableHead.Resize`,
  `TableHead.MINIMUM_WIDTH`, `TableHeader` answers the anchor and is `Measured`;
  new `TableHeaderCell` and `TableGrip`. `controls.css`: `table-grip`, and
  `table-header` without `overflow: hidden`.
- Tests: `TableResizeTest`.
- `example` `ui/Collections` — the Name column is resizable; `gallery-collections`
  re-blessed.
