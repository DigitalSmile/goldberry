# Goldberry — Core Widgets

Companion to `ARCHITECTURE.md` §11. Specifies every built-in widget. All of these live in the **single `goldberry-core` Gradle module** — separated by *package*, not by artifact — under `io.github.digitalsmle.goldberry.widget.*`.

| Package      | Contents |
|--------------|----------|
| `core`       | layout & primitives: `row`, `column`, `stack`, `spacer`, `scroll`, `canvas`, `image`, `icon`, `focus-scope` |
| `text`       | `text` (+ inline `span` runs) |
| `controls`   | `button`, `toggle`, `checkbox`, `radio`/`radio-group`, `slider`/`fader`, `knob`, `select`, `progress`, `spinner`, `badge` |
| `form`       | `form`, `field`, `text-input`, `text-area`, validation model |
| `panel`      | `panel`, `card`, `group-box`, `tabs`, `split-pane` |
| `overlay`    | `dialog`, `toast`, `tooltip`, `popover` |
| `menu`       | `menubar`, `menu`, `item`, `separator`, context-menu attachment |
| `shell`      | `tray-icon`, `titlebar` (CSD), window chrome |
| `collection` | `list` (v1), `tree`/`table` (deferred) |

**The widget contract** (every widget in this document):

- **KDL node name = CSS type selector = registry name.** `button` in markup, `button { }` in CSS, `Button` record in Java. Parity enforced by test.
- **States are pseudo-classes:** `:hover :active :focus :focus-visible :disabled :checked` (plus `:invalid` for form controls — an addition to the CSS engine's pseudo set).
- **Colors and metrics only via `--gb-*` tokens** — no literal colors in widget painters; themes restyle everything.
- **Semantics:** every widget populates the semantics tree (role, name, value, state, actions) — noted per widget below.
- Disabled state propagates down the tree; a disabled container disables its descendants for input and semantics.

---

## 1. `widget.core` — layout & primitives

- **`row` / `column`** — flex containers (direction preset); all Yoga properties via CSS or attributes (`gap`, `justify`, `align`). No intrinsic painting. Semantics: generic group.
- **`stack`** — z-order layering; children positioned by alignment or absolute insets. Basis for badges-over-things and custom overlays.
- **`spacer`** — `flex-grow: 1` shorthand widget.
- **`scroll`** — one or both axes; overlay scrollbars per the design-system spec (thin, hover-widening, fade when idle); pixel-precise wheel with line fallback; keyboard (PgUp/PgDn/Home/End/arrows when focused); `scrollIntoView(widget)` API; scroll position is retained state surviving rebuilds. Repaint boundary by definition. Semantics: scrollable region with scroll actions.
- **`canvas`** — immediate-mode Blend2D painting surface: `onPaint(ctx, size)` called on invalidation; `invalidate()` to request repaint; hit-testable; the substrate for charts, meters, and app custom drawing. Escape hatch by design.
- **`image`** — sources: path, classpath, bytes, or async supplier (placeholder fill until loaded); fit modes `contain | cover | fill | none`; DPI-aware (picks raster scale by physical pixels); Blend2D codecs (PNG/JPEG/QOI); `image/svg+xml` routes to `goldberry-vector` when present. Semantics: image with alt text (required attribute for non-decorative use).
- **`icon`** — Lucide registry by name; sized in `em` (follows `font-size`) or explicit px; tinted by CSS `color`; app icon packs registrable. Decorative by default in semantics unless labeled.
- **`focus-scope`** — traversal boundary: contains Tab order, optional `autofocus` target, focus restoration on scope exit (dialogs and popovers wrap one automatically).

```kdl
row gap=8 class="toolbar" {
  icon name="search"
  spacer
  button icon="plus" "New"
}
```

## 2. `widget.text`

- **`text`** — the single text-display widget. Typography via token styles (`style="title"` → the §10.1 scale) or CSS font properties. Wrapping on by default; `wrap=#false`, `ellipsis` (end-truncation with … when constrained), `max-lines`. Alignment `start|center|end`.
- **Inline styled runs:** child `span` nodes carry class/style for mixed formatting inside one paragraph; spans may nest one level. Links inside text = `span class="link" action="…"`.
- Selection/copy for static text: deferred to the text-editing depth milestone (ARCHITECTURE §17) — same machinery, one implementation.
- Semantics: text content verbatim; heading level inferred from typography token (`title`/`heading` → heading roles).

```kdl
text style="body" {
  span "Goldberry is "
  span class="b" "fast"
  span " and themable."
}
```

## 3. `widget.controls`

All controls: focusable, full keyboard operation, `:focus-visible` ring per the design system, semantics role + state, value changes raised as events and through `bind`.

- **`button`** — variants via class: `primary | secondary (default) | ghost | danger`; content = label, icon, or both; `Space/Enter` activates; `default=#true` (Enter in dialog) and `cancel=#true` (Esc) roles. Semantics: button.
- **`toggle`** — switch; drag or click/Space. Semantics: switch.
- **`checkbox`** — binary or tri-state (`indeterminate`); label is part of the widget (click target includes label). Semantics: checkbox with mixed state.
- **`radio` / `radio-group`** — group is the focusable unit; arrow keys move selection (roving focus); exactly-one invariant held by the group. Semantics: radiogroup/radio.
- **`slider`** — horizontal default; `min/max/step`, optional tick marks and value label; keyboard arrows (step), PgUp/PgDn (large step), Home/End; **`fader`** = vertical variant with optional dB scale mapping. Semantics: slider with value text.
- **`knob`** — rotary: vertical-drag primary (circular-drag optional), wheel steps, keyboard arrows, modifier for fine adjustment, optional detents, `min/max`, arc indicator. Semantics: slider.
- **`select`** — closed control + popup list (backend popup window, so it escapes window bounds); typeahead; keyboard open (Space/Alt+Down), arrows, Enter/Esc. Option model or inline KDL `option` children. Semantics: combobox (non-editable v1).
- **`progress`** — determinate (`value/max`) and indeterminate (animated, reduced-motion aware). Semantics: progressbar.
- **`spinner`** — small indeterminate activity indicator. Decorative in semantics unless labeled.
- **`badge`** — count/status chip, typically composed inside `stack`. Semantics: text.

## 4. `widget.form`

- **`form` / `field`** — layout contract: consistent label column (or stacked labels via class), required marker, control slot, message slot below. `field` wires label→control for semantics and click-to-focus automatically.
- **`text-input`** — single line. v1 scope: caret, selection (mouse+keyboard+word ops), clipboard, undo/redo stack, placeholder, max-length, `password` mode (masking, no clipboard-out), input filters (numeric etc.). IME preedit and RTL editing deferred (ARCHITECTURE §17). Semantics: textbox.
- **`text-area`** — multi-line: soft wrap, optional auto-grow between min/max rows, scrollbar beyond. Same editing scope as `text-input`.
- **Validation model** — `Validator<T>` per field; run on blur and on submit (configurable); failures set `:invalid`, render the message slot in `--gb-danger`, and register in the form's error summary. `form.submit()` gates on validity and raises a typed event with bound values.
- **Binding** — all form controls accept `bind="model.path"` against the observable `Property<T>` model (two-way).

```kdl
form {
  field label="Name" required=#true {
    text-input bind="user.name" placeholder="Jane Doe"
  }
  field label="Bio" { text-area bind="user.bio" rows=4 }
  row class="actions" { spacer; button class="primary" default=#true "Save" }
}
```

## 5. `widget.panel`

- **`panel`** — plain surface: `--gb-surface`, border, radius tokens. The building block; no elevation.
- **`card`** — elevated surface: shadow tokens, hover-elevation optional via class. Semantics: group with optional label.
- **`group-box`** — titled border group for settings clusters.
- **`tabs`** — top placement v1; keyboard (arrows between tabs, Tab into content); closable tabs optional; lazy content instantiation; selected tab is retained state. Semantics: tablist/tab/tabpanel.
- **`split-pane`** — two children, draggable divider (keyboard-resizable when focused), min sizes, optional collapse-to-edge; divider position retained. Semantics: separator with value.

## 6. `widget.overlay`

All overlays render in the in-window overlay layer or backend popup windows as appropriate; each wraps a `focus-scope` and restores focus on close.

- **`dialog`** — modal: scrim over the window, focus trap, `Esc` = cancel-role button, `Enter` = default-role button; **platform button order** (affirmative-right on macOS/Linux; theme-controlled) applied by the dialog's action bar automatically. Sizes to content with min/max. Semantics: dialog with labelled title.
- **`toast`** — non-modal notifications: queued, timeout with hover-pause, optional action button, stacking corner configurable; announced via semantics (live region) for future screen-reader bridging.
- **`tooltip`** — attached by attribute (`tooltip="…"`) to any widget; shows on hover *and on keyboard focus* after delay; never focusable itself; plain text v1.
- **`popover`** — anchored floating panel (the `select` popup generalized): placement with flip/shift when near edges, light-dismiss on outside click/Esc; the primitive under menus, dropdowns, and date-picker-style future widgets.

## 7. `widget.menu`

- **`menubar`** — in-window horizontal bar (a global macOS menubar is a documented non-goal for v1, see ARCHITECTURE §17); `Alt`-style keyboard activation; arrows navigate.
- **`menu` / `item` / `separator`** — items: label, optional icon, accelerator (displayed right-aligned *and* auto-registered in the window's shortcut map), checkable items, disabled state, nested submenus (hover-intent timing). Rendered in backend popup windows so menus escape window bounds.
- **Context menus** — any widget takes `context-menu="menuId"`; opened by right-click or the keyboard menu key at the focused widget.
- Semantics: menubar/menu/menuitem with checked state.

## 8. `widget.shell`

- **`tray-icon`** — via SDL3's tray API: icon (theme-aware light/dark variants), tooltip, menu, activate event. Availability is platform-dependent; the API reports absence rather than failing.
- **`titlebar`** — opt-in client-side decorations: drag region, double-click maximize, window buttons with per-platform ordering, frost material variant; falls back to native decorations by default (ARCHITECTURE §4).
- Window chrome helpers: decoration mode switch, fullscreen toggle, attention request (taskbar flash / dock bounce via SDL where supported).

## 9. `widget.collection`

- **`list`** (v1) — vertical list over an observable item model with an item-factory (any widget as row); selection models: none / single / multi (Ctrl/Shift semantics); full keyboard (arrows, Home/End, type-to-select when items expose text); item context menus. **v1 renders instantiated rows** — fine into the low thousands; **virtualization/recycling is the committed v1.x follow-up** (the item-factory API is designed for recycling from day one so it's a performance upgrade, not an API break). Semantics: listbox/option.
- **`tree`, `table`** — deferred (ARCHITECTURE §17). `tree` will reuse `list`'s model + indentation/expansion; `table` awaits the virtualization work. Recorded here so their names are reserved in the registry.

---

## Cross-cutting notes

- **Retained widget state** (scroll position, selected tab, caret, splitter position, list selection) survives widget-tree rebuilds via element-tree keys — declarative rebuilds never visibly reset UI.
- **Gallery app** exercises every widget in every state in both themes; golden-image CI runs the gallery matrix. A widget isn't "done" until it's in the gallery.
- **KDL/Java/CSS parity test** walks the registry: every widget constructible from markup, from the builder, and matched by its type selector — build fails otherwise.