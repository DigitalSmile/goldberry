# Goldberry — Core Widgets

Companion to `ARCHITECTURE.md` §11. Specifies every built-in widget.

**Where they live.** Every widget in this document is in the single
`goldberry-widgets` module ([ADR-0014](../book/src/adr/0014-single-widgets-module.md)),
separated by *package* and not by artifact under
`io.github.digitalsmile.goldberry.widgets.*`
([ADR-0091](../book/src/adr/0091-one-module-a-package-per-control.md)) — the
table below is that package layout.

`:core` holds **no widgets at all**. It holds the machinery they are built from —
`Widget`, `Element`, `Attributes`, the cascade, layout, text and paint — and for a
while it also shipped `row`, `column`, `text`, `panel` and `spacer`, because the
engines needed something to prove the widget tree against before there was a
catalog to prove it with. Once there was one, those five were the only widgets in
a module that is not a widget toolkit, and they moved
([ADR-0092](../book/src/adr/0092-a-primitive-is-a-widget-like-any-other.md)).

Inside a group there is **one package per widget**, holding it and its parts —
`…controls.slider` is `Slider` plus its nine — so a part stays package-private
and unreachable from anywhere but its own control, which is what makes
"CSS-selectable and not constructible" a rule the compiler keeps.

| Package      | Contents |
|--------------|----------|
| `core`       | layout & primitives: `row`, `column`, `stack`, `spacer`, `scroll`, `canvas`, `image`, `icon`, `focus-scope`, `affix` |
| `text`       | `text` (+ inline `span` runs), `link` |
| `controls`   | `button`, `toggle`, `checkbox`, `radio`/`radio-group`, `slider`/`fader`, `knob`, `select`, `segmented`, `progress`, `spinner`, `badge` |
| `form`       | `form`, `field`, `text-input`, `text-area`, `date-picker`, `time-picker`, `color-picker`, `code-input`, autocomplete, validation model |
| `panel`      | `panel`, `card`, `group-box`, `tabs`, `split-pane`, `collapse`, `carousel`, `statistic`, `skeleton` |
| `nav`        | `breadcrumbs`, `steps`, `wizard` |
| `overlay`    | `dialog`, `toast`, `tooltip`, `popover`, `message`, `tour` |
| `menu`       | `menubar`, `menu`, `item`, `separator`, context-menu attachment |
| `shell`      | `tray-icon`, `titlebar` (CSD), window chrome |
| `collection` | `list`, `tree`, `calendar`, `timeline`, `table` (deferred) |

`nav` is the one package added since v0.1's nine. `breadcrumbs`, `steps` and
`wizard` all answer "where am I in a sequence", which is neither a surface
(`panel`) nor a control that reports a value (`controls`); folding them into
either would have made that package's name a lie. Principle 3 in
`design-system.md` §1.1: extend deliberately, do not improvise.

**Status.** Everything below is a specification. `design-system.md` §5 requires a
spec *and* a §3 metrics row *and* gallery coverage before code, in that order —
so a widget appearing here has passed the first two gates and not the third.
`book/src/status.md` tracks which are built.

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
- **`affix`** — pins its single child to an edge of the nearest `scroll` once the child would have scrolled past it: `edge="top|bottom|left|right"`, `offset` in px. The child keeps its place in layout — `affix` leaves a same-sized hole behind, so nothing below it jumps when it detaches. `:affixed` is a pseudo-class, so a sticky header can gain a shadow the moment it lifts. Not `position: sticky`: §8's CSS subset has no `position` at all, and this is a widget precisely so the subset does not have to grow one. Semantics: transparent — it adds no role, it only moves its child.

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
- **`link`** — text that does something. `action="…"` for in-app navigation or `href="…"` for an external target opened through the platform; `visited=#true` is the application's to set, because the toolkit keeps no history. Focusable and in the Tab order (unlike `text`), `Enter` activates, `:hover` underlines, `:focus-visible` takes the standard ring. External links carry a trailing 12px `external-link` icon and their accessible name says so, because "opens outside this window" is not something a colour can convey. A `link` inside a paragraph is the block-level spelling of `span class="link"`; the `span` form stays for mid-sentence use and shares the styling. Semantics: link, with `href` or the action name as its target.
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

- **`button`** — variants via class: `primary | secondary (default) | ghost | danger`, plus **`outlined`** (transparent fill, 1px `--gb-border`, text in `--gb-text`; composes with the semantic variants, so `button.outlined.danger` is a danger outline). Shape via class: default radius 8, **`square`** for radius 0 where buttons butt against each other or against a field, and **`circle`** for a `full` radius. **A button given an icon and no label is a circle by default** — an icon-only button is a disc everywhere else in the canon, and requiring `class="circle"` to get the obvious result is the kind of improvisation Principle 3 exists to prevent; `class="square"` overrides it. Icon-only buttons still require an accessible name (§1.6), which is the `aria-label`-equivalent attribute `name=`.
  **`float=#true`** lifts the button out of layout and pins it to a window corner — the floating action button — at `--gb-window-margin` from both edges, `corner="bottom-end"` by default, elevation 1. It is a class of *placement*, not of appearance, so it composes with every variant and shape above; a floating button that is not `circle` is legal and unusual.
  Content = label, icon, or both; `Space/Enter` activates; `default=#true` (Enter in dialog) and `cancel=#true` (Esc) roles. Semantics: button.
- **`toggle`** — switch; drag or click/Space. Semantics: switch.
- **`checkbox`** — binary or tri-state (`indeterminate`); label is part of the widget (click target includes label). Semantics: checkbox with mixed state.
- **`radio` / `radio-group`** — group is the focusable unit; arrow keys move selection (roving focus); exactly-one invariant held by the group. Semantics: radiogroup/radio.
- **`slider`** — horizontal default; `min/max/step`, optional tick marks and value label; keyboard arrows (step), PgUp/PgDn (large step), Home/End; **`fader`** = vertical variant with optional dB scale mapping. Semantics: slider with value text.
- **`knob`** — rotary: vertical-drag primary (circular-drag optional), wheel steps, keyboard arrows, modifier for fine adjustment, optional detents, `min/max`, arc indicator, and a **pointer** on the dial saying which way it is turned. **Clicking the ring positions the value** at the angle clicked (the ring is a track); clicking the dial grabs it for a drag and does not jump. Semantics: slider.
- **`select`** — closed control + popup list (backend popup window, so it escapes window bounds); typeahead; keyboard open (Space/Alt+Down), arrows, Enter/Esc. Option model or inline KDL `option` children. `multiple=#true` renders the selection as `badge` chips inside the closed control, each with a remove affordance.
  **`autocomplete=#true`** makes the closed control an editable `text-input`: typing filters the options, the popup stays open and narrows, `Esc` restores the last committed value rather than clearing, and a free-typed value is refused unless `free=#true`. The distinction that matters is that filtering is the *application's*: `select` raises the query and renders whatever options it is handed back, so a remote-backed autocomplete is the same widget with a slower model. Semantics: combobox, editable when `autocomplete`.
  **`tree=#true`** takes a `tree`'s model instead of a flat option list, so the popup is a `tree` and a selection is a node. Selecting a parent is `checkable="leaf|any|cascade"` — leaf-only by default, because "Europe" is usually a heading and not an answer. Semantics: combobox owning a tree.
- **`segmented`** — a row of mutually exclusive options rendered as one bar, sharing `radio-group`'s model and invariant exactly: one Tab stop, arrows rove, exactly one selected, `change` reports the value. **The arrows are `Left`/`Right` only**, unlike a radio group's: a bar has an axis of its own where a group's is its stylesheet's, so `Up`/`Down` stay with whatever is above it ([ADR-0078](../book/src/adr/0078-a-focus-scope-has-an-axis.md)). It is `radio-group` with a different drawing, and it is a separate widget rather than `radio-group.segmented` because the two are not substitutable in a layout — a segmented control is a fixed-width bar that belongs in a toolbar, a radio group is a list that belongs in a form. Options are inline `option` children with a label, an icon, or both; icon-only segments require `name=`. **Segments are equal width** — each exactly 1/n of the bar, which is what lets the selection indicator travel between them rather than fade in place; the bar therefore takes the width it is given and fills its parent when nothing gives it one ([ADR-0099](../book/src/adr/0099-an-indicator-travels-on-a-grid.md)). Semantics: radiogroup/radio.
- **`progress`** — determinate (`value/max`) and indeterminate (animated, reduced-motion aware). Semantics: progressbar.
- **`spinner`** — small indeterminate activity indicator. Decorative in semantics unless labeled.
- **`badge`** — count/status chip, typically composed inside `stack`. Semantics: text.

## 4. `widget.form`

- **`form` / `field`** — layout contract: consistent label column (or stacked labels via class), required marker, control slot, message slot below. `field` wires label→control for semantics and click-to-focus automatically.
- **`text-input`** — single line. v1 scope: caret, selection (mouse+keyboard+word ops), clipboard, undo/redo stack, placeholder, max-length, `password` mode (masking, no clipboard-out), input filters (numeric etc.). IME preedit and RTL editing deferred (ARCHITECTURE §17). Semantics: textbox.
- **`text-area`** — multi-line: soft wrap, optional auto-grow between min/max rows, scrollbar beyond. Same editing scope as `text-input`.
- **Validation model** — `Validator<T>` per field; run on blur and on submit (configurable); failures set `:invalid`, render the message slot in `--gb-danger`, and register in the form's error summary. `form.submit()` gates on validity and raises a typed event with bound values.
- **`date-picker` / `time-picker`** — a `text-input` that parses, plus a `popover` holding a `calendar` (date) or an hour/minute/second column set (time). `date-picker range=#true` selects a pair and reports it as one value. The **typed field is the source of truth**, not the popup: a date picker you cannot type into is unusable for a birthday, and every keyboard user reaches the field before the grid. Parsing and formatting take a `java.time` formatter the application supplies, defaulting to the locale's short form — the toolkit does not invent a date syntax. Value type is `LocalDate` / `LocalTime` / `LocalDateTime`; `min`, `max` and a `disabled` predicate gate both the field and the grid, so an unreachable date cannot be typed either. Keyboard: `Alt+Down` opens, arrows move within the grid, `PgUp`/`PgDn` change month, `Esc` reverts. Semantics: combobox owning a grid, with the formatted date as its value text.
- **`color-picker`** — a swatch button opening a `popover` with a saturation/value plane, a hue slider, an optional alpha slider, a hex `text-input`, and an application-supplied palette of preset swatches. The hex field is the source of truth for the same reason the date field is. Value is the toolkit's `CssColor`, so a picked colour is directly usable in a stylesheet; the model carries OKLCH internally because that is what §1.7 interpolates in and what the ramp utility uses, and round-trips to hex without drift. `alpha=#false` (the default) hides the alpha slider and refuses translucent values. Keyboard: arrows move the plane cursor by 1, `Shift`+arrows by 10. Semantics: combobox with the hex as its value text — a colour name is not something the toolkit can honestly produce.
- **`code-input`** — the one-time-code field: `length=6` separate single-character boxes over one value, `type="digits|alnum"`. It exists as its own widget rather than a styled `text-input` because its *editing model* is different, and that is the whole of the specification: typing advances, `Backspace` on an empty box moves back and clears the previous one, **a paste of the full code fills every box at once** (the thing users actually do), and focus lands wherever the first empty box is. `complete` fires when the last box fills, which is what lets a form submit without a button. `mask=#true` for authenticator-style secrecy. Semantics: a single textbox with the whole code as its value — six boxes are a drawing, not six fields, and announcing them separately would be a lie.
- **Autocomplete** — `text-input autocomplete=#true` attaches a `popover` of suggestions to the field: the widget raises the query, the application supplies the list, and the field's text is never rewritten without the user choosing. See `select autocomplete` for the combobox form; this is the free-text one, where the suggestions are a convenience and any value is legal.
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
- **`collapse`** — a header and a body that folds away. `open` is retained state; `accordion=#true` on a containing `column` makes siblings mutually exclusive. The header is one Tab stop, `Enter`/`Space` toggles, `Left`/`Right` close and open. **The body is unmounted while closed, not hidden**: a collapsed section that kept a live subtree would keep its subscriptions, its images and its scroll position alive for content nobody can see, and "cheap to rebuild" is what the widget tree is for (ADR-0004). The chevron rotates on `base`; the body does not animate its height, because height is not on §1.7's whitelist and never will be. Semantics: disclosure button plus region.
- **`carousel`** — one child visible at a time out of a list, with previous/next controls and a dot indicator. `loop=#false` by default. **Nothing advances on its own unless `interval` is set**, and when it is, the rotation pauses on hover, on focus anywhere inside, and entirely under reduced motion — §1.7 rule 4 says nothing loops except explicit continuous indicators, and a carousel that moves while being read is the canonical violation. Arrows move between slides when the strip is focused; slides are one Tab stop and their content is reachable inside. Semantics: a list with the current position announced.
- **`statistic`** — a labelled number: `value`, `label`, optional `unit`, optional `delta` with `direction="up|down"` rendered in `--gb-success`/`--gb-danger`, optional `sparkline` from a `canvas`. Formatting is the application's — the widget takes a string, for `slider`'s reason: a locale-aware number formatted inside the toolkit makes a golden image that cannot be reproduced on another machine. Semantics: text; the label and the value are one accessible name so they are never read apart.
- **`skeleton`** — the placeholder a widget shows while its data loads: `shape="text|title|circle|rect"` and `lines` for the text form, sized from the typography token it stands in for so the layout does not jump when the real content arrives. **A shimmer is a loop**, so §1.7 rule 4 applies: it is a `linear` opacity pulse on `--gb-motion-overlay`×5, it is the only decoration in the canon allowed to loop, and under reduced motion it holds still at its dimmest. Semantics: `busy`, with no text — a skeleton announcing "loading" once per placeholder would announce it thirty times.

## 6. `widget.nav`

Three widgets answering one question — where am I in a sequence — and sharing one
model: an ordered list of steps, a current index, and which of them are reachable.

- **`breadcrumbs`** — the path to here: `crumb` children with a label, an optional icon and an `action`; the last is the current page and is **not** a link. Overflow collapses the middle into a `…` that opens a `menu` of the hidden crumbs, rather than eliding characters — a truncated folder name is worse than a hidden one, because it looks like a name. Separator is a `chevron-right` icon in `--gb-text-muted`, not a character, so it never joins the text run. Semantics: navigation landmark containing links, current page marked.
- **`steps`** — a progress indicator over an ordered list: each step has a label, an optional description, and a state of `done | current | upcoming | error`. `direction="horizontal|vertical"`. Standalone it is a read-only picture of where a process is; `clickable=#true` raises a `change` for a step the application says is reachable, and refuses the rest — the widget never decides reachability itself, because only the application knows whether step 3 is valid yet. Semantics: a list with the current item marked; states are in each item's accessible name, since colour alone cannot carry `error`.
- **`wizard`** — `steps` plus a content area plus an action bar, which is the whole of it: it owns *no* validation, no navigation policy and no data. Back/Next/Finish raise events the application answers by moving the index, so a wizard that refuses to advance is an application that did not move it. `steps` is a child widget rather than a drawing, so a wizard's indicator is the standalone one and cannot drift from it. The content area is a `focus-scope`: advancing moves focus to the new step's first control, because a keyboard user who pressed Next and stayed on the button has not moved. Semantics: group with the step count and position; the action bar follows §2.3's platform button order like a dialog's.

## 7. `widget.overlay`

All overlays render in the in-window overlay layer or backend popup windows as appropriate; each wraps a `focus-scope` and restores focus on close.

**The in-window layer exists** ([ADR-0100](../book/src/adr/0100-a-window-has-a-layer-above-its-application.md)): every window's tree is rooted at a `window-root` whose children are the application's root and whatever is floating over it, pinned to a `Corner` and out of flow, so an overlay takes no space from the content and is painted after it. `host.overlay(widget, corner)` puts one there and hands back the handle that removes it. The popup-window half waits for the backend SPI (`ARCHITECTURE.md` §4), which is what `menu`, `tooltip`, `popover` and `select` are waiting on; `toast`, `dialog`'s scrim and `hud` want the in-window layer and not a second platform window.

- **`hud`** — the frame loop, on top of the window it is running: `fps` and `paint` by default, `frame` (the interval) on request, as `readings="fps frame paint"`. Each reading is a `hud-reading` part with its own class, so a stylesheet can dim the units or colour a paint time that has run out of budget. **It never asks for a frame**: a frame-rate display that requested one so it could show a fresh number would falsify §1.7's "the frame loop is fully idle when no animation is active" for every window with one in the corner, and would then be reporting a rate it had caused. So it reports the frames that were already happening — watch it during a resize or a drag — and on an idle window the numbers freeze with the loop. A HUD with no frame loop over it draws dashes rather than zeroes, because a zero is a measurement. Numbers are formatted in the root locale for §5's reason, which applies with more force here: these are the toolkit's own numbers. Decorative in semantics — a screen reader announcing a frame rate sixty times a second is not an accessibility feature ([ADR-0101](../book/src/adr/0101-a-diagnostic-must-not-be-the-thing-it-measures.md)).

- **`dialog`** — modal: scrim over the window, focus trap, `Esc` = cancel-role button, `Enter` = default-role button; **platform button order** (affirmative-right on macOS/Linux; theme-controlled) applied by the dialog's action bar automatically. Sizes to content with min/max. Semantics: dialog with labelled title.
- **`toast`** — non-modal notifications: queued, timeout with hover-pause, optional action button, stacking corner configurable; announced via semantics (live region) for future screen-reader bridging.
- **`tooltip`** — attached by attribute (`tooltip="…"`) to any widget; shows on hover *and on keyboard focus* after delay; never focusable itself; plain text v1.
- **`popover`** — anchored floating panel (the `select` popup generalized): placement with flip/shift when near edges, light-dismiss on outside click/Esc; the primitive under menus, dropdowns, `date-picker`, `color-picker` and autocomplete.
- **`message`** — an inline banner stating something about the region it sits in: `kind="info|success|warning|danger"`, an icon, text, optional action links, optional dismiss. **Not a `toast`**, and the distinction is the reason both exist: a toast is transient, floats over the window and is about something that just happened; a message is part of the layout, persists until the condition does, and is about the thing next to it. A form's error summary is a `message`; "Saved" is a `toast`. The icon is not decorative — §1.2 forbids colour as the only carrier of meaning, so `kind` sets an icon *and* a colour. Semantics: status for info/success, alert for warning/danger, which is what decides whether it interrupts.
- **`tour`** — a guided sequence of `popover`s over real widgets: each stop names a target by id, a title, a body, and Back/Next/Skip. The window dims outside the target with a `veil` cut to its rect, so the thing being described is the only lit thing on screen. It **scrolls the target into view and waits for the frame** before positioning, because a popover anchored to a rect that is about to move points at nothing. `Esc` skips the whole tour, not one stop. A target that is not in the tree is skipped with a warning rather than throwing — a tour is documentation, and documentation going stale must not take the window down. Semantics: dialog per stop, with the target as its described object.

## 8. `widget.menu`

- **`menubar`** — in-window horizontal bar (a global macOS menubar is a documented non-goal for v1, see ARCHITECTURE §17); `Alt`-style keyboard activation; arrows navigate.
- **`menu` / `item` / `separator`** — items: label, optional icon, accelerator (displayed right-aligned *and* auto-registered in the window's shortcut map), checkable items, disabled state, nested submenus (hover-intent timing). Rendered in backend popup windows so menus escape window bounds.
- **Context menus** — any widget takes `context-menu="menuId"`; opened by right-click or the keyboard menu key at the focused widget.
- Semantics: menubar/menu/menuitem with checked state.

## 9. `widget.shell`

- **`tray-icon`** — via SDL3's tray API: icon (theme-aware light/dark variants), tooltip, menu, activate event. Availability is platform-dependent; the API reports absence rather than failing.
- **`titlebar`** — opt-in client-side decorations: drag region, double-click maximize, window buttons with per-platform ordering, frost material variant; falls back to native decorations by default (ARCHITECTURE §4).
- Window chrome helpers: decoration mode switch, fullscreen toggle, attention request (taskbar flash / dock bounce via SDL where supported).

## 10. `widget.collection`

- **`list`** (v1) — vertical list over an observable item model with an item-factory (any widget as row); selection models: none / single / multi (Ctrl/Shift semantics); full keyboard (arrows, Home/End, type-to-select when items expose text); item context menus. **v1 renders instantiated rows** — fine into the low thousands; **virtualization/recycling is the committed v1.x follow-up** (the item-factory API is designed for recycling from day one so it's a performance upgrade, not an API break). Semantics: listbox/option.
- **`tree`** — a hierarchical list: an observable node model with a children supplier (lazy, so a node's children are fetched when it first expands), indentation per level, a chevron on nodes that have or may have children, and `list`'s selection models. Keyboard is the part that has to be right: `Right` expands or moves to the first child, `Left` collapses or moves to the parent, `Home`/`End` go to the first and last **visible** rows, `*` expands every sibling, and type-to-select matches across visible rows only. `checkable="none|leaf|any|cascade"` adds a checkbox per node; `cascade` propagates down and shows `indeterminate` upward, which is the one place the tri-state checkbox is not a decoration. Expansion state is retained across rebuilds by node id, not by index — a tree that collapsed itself when its model reordered would be the same defect list keys exist to prevent. It shares `list`'s item-factory so it inherits the virtualization work when that lands. Semantics: tree/treeitem with level, position and expanded state.
- **`calendar`** — a month grid over a date model: single date, multiple dates, or a range; `min`/`max` and a `disabled` predicate; per-day decoration from the application (a dot, a `badge`, a background) so an agenda or a heat map is the same widget with a different cell renderer. Week starts and month/day names come from the `Locale` the application supplies — the toolkit ships no calendar data of its own beyond `java.time`. Keyboard: arrows move a day, `PgUp`/`PgDn` a month, `Shift+PgUp`/`PgDn` a year, `Home`/`End` the week. The grid is one Tab stop with a roving day (§2.2). Semantics: grid with each cell's full date as its name, because "14" is not a date.
- **`timeline`** — an ordered list of events along an axis: each entry has a marker (dot, icon or `badge`), a label, an optional timestamp and optional body content. `direction="vertical|horizontal"`, `align="start|alternate"`. `pending=#true` renders a trailing unfilled marker for "and then what happens next", which is what distinguishes a timeline from a list with dots. Semantics: an ordered list — the connecting line is a drawing and is not announced.
- **`table`** — deferred (ARCHITECTURE §17); it awaits the virtualization work. Recorded here so the name is reserved in the registry.

---

## Cross-cutting notes

- **Retained widget state** (scroll position, selected tab, caret, splitter position, list selection) survives widget-tree rebuilds via element-tree keys — declarative rebuilds never visibly reset UI.
- **Gallery app** exercises every widget in every state in both themes; golden-image CI runs the gallery matrix. A widget isn't "done" until it's in the gallery.
- **KDL/Java/CSS parity test** walks the registry: every widget constructible from markup, from the builder, and matched by its type selector — build fails otherwise.