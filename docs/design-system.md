# Goldberry Design System (GDS) — v0.1

Companion to `ARCHITECTURE.md`, `core-widgets.md`, `content-widgets.md`. Defines the visual and interaction language every built-in widget implements and every Goldberry app inherits by default.

**Relationship to the Macaw Design System (MDS).** GDS is MDS restated at toolkit scope. Same tokens, materials, metrics, and rules; what's removed is OS-only territory (system dialogs, shell surfaces, portal IPC). On Scarlet Macaw OS, the theme layer maps `--os-*` alias tokens onto `--gb-*` one-to-one, so OS apps and standalone Goldberry apps are pixel-identical. GDS versions together with the KDL markup schema; alias tokens and component contracts are the stable tier.

---

## 1. Foundations

### 1.1 Principles

1. **Desktop-only.** Pointer + keyboard first. Hit targets sized for a mouse (≥ 32×32 logical px), no touch profile.
2. **Opaque-first.** Every surface is designed opaque; frost is an enhancement layer that can always fall back.
3. **Token or extend.** If a screen needs a value that isn't a token or a component that isn't in the canon, the system gets extended deliberately — the screen doesn't improvise.
4. **Keyboard-complete.** Every interaction reachable and operable without a pointer; `:focus-visible` always legible.
5. **Deterministic.** Embedded fonts, CPU raster, token-driven color — the same markup renders identically on every machine. Golden images are the arbiter.

### 1.2 Color

Raw palette: Nord, exposed as theme-invariant `--nord0…--nord15`. Widgets never consume raw palette — only **semantic alias tokens** (the `--gb-*` table in `ARCHITECTURE.md` §10: bg, surface, surface-2, text, text-muted, border, accent, focus, danger, warning, success, info, selection).

Rules:

- Aurora hues (`nord11–15`) appear **only with semantic meaning** (danger/warning/success/info, chart series) or in expressive surfaces (about pages, empty-state art) — never as decoration on controls.
- Every text/surface pair meets **WCAG 4.5:1** (3:1 for large text ≥ 20px). Contrast is validated in CI against both themes, including the frost worst-case floor (§1.5).
- **Every non-text pair meets 3:1**, and a semantic hue has three ranks rather than two: `--gb-danger` is what danger *is*, `--gb-danger-fill` is what you may put words on top of, and `--gb-danger-line` is what you may draw a glyph or a border with **on** a surface. The third rank exists because the first measurement of the second sentence disproved it — five of the eight hue/surface pairs were below this floor, `--nord13` on the light theme worst at 1.28:1, in a rule both themes had documented for months ([ADR-0175](../book/src/adr/0175-a-banner-says-its-kind-twice.md)). The rank is what makes the icon in the rule above a *carrier* of meaning rather than a claim to be one, and `ContrastTest` sweeps it beside the 4.5:1 pairs. **The non-text sentence was measured next and had fifteen exceptions of its own** ([ADR-0239](../book/src/adr/0239-a-mark-is-measured-against-the-box-it-is-drawn-in.md)): twelve of them were an unchecked `checkbox` or `radio` whose edge was `--gb-border`, a divider colour chosen to be subtle, holding a control up at 1.17:1 — so the controls have `--gb-checkbox-border` now, a value derived into the gap Nord leaves between `--nord3` and `--nord4` because the palette has nothing there that is neither invisible nor a white ring ([ADR-0258](../book/src/adr/0258-the-edge-a-measurement-chose.md)). **One exception is left and it is arithmetic**: the light theme's slider track sits between a white thumb and a dark accent fill, and clearing 3:1 against both needs its luminance at once ≤ 0.300 and ≥ 0.688. What that asks for is a sentence §3 does not contain about what a light-theme thumb is — a border, or a fill that is not white — and until it does, `MARKS_BELOW_FLOOR` carries it alone.
- Light and dark are peer themes, both shipped, switchable at runtime; `system` mode follows `prefers-color-scheme`.

### 1.3 Spacing and sizing

- **Base unit 4 px.** Legal ramp: `2, 4, 8, 12, 16, 20, 24, 32, 40, 48, 64`. No off-ramp values.
- Component padding defaults **8 / 12**; gap between related controls **8**; between groups **16**; window content margins **16** (compact windows) / **24** (regular).
- Hit targets **≥ 32×32** logical px even when the visual is smaller (checkbox glyph 16px, hit area 32).
- **Density:** `--gb-density` `regular` (default) | `compact` — control heights 32 / 28, list rows 32 / 26. A user preference applied app-wide; token-conformant apps adapt with zero code.

### 1.4 Typography

Exactly two shipped typefaces — **Inter** (UI) and **JetBrains Mono** (code) — plus the routed emoji slot (`goldberry-emoji`), which is **routed for real** since ADR-0393: an emoji in a line of prose is split out during itemization and drawn from OpenMoji's colour build. **No fallback chain beyond that**: a missing glyph that is not an emoji renders `.notdef` by design. All sizes logical px.

Inter ships as **four faces**: 400 and 600, each upright and italic. A weight is a face here rather than a variable-font axis (ADR-0066) and an italic is a face for the same reason one step on — Inter's italic is drawn, not slanted ([ADR-0323](../book/src/adr/0323-an-italic-is-a-face-and-the-matrix-closes.md)). The tokens below name the two weights; `font-style: italic` is written where emphasis is meant, and `font-style: oblique` is refused because nothing here shears a glyph. **`text-decoration: underline | line-through`** is in the subset too, drawn at the face's own position and thickness ([ADR-0321](../book/src/adr/0321-a-rule-under-text-belongs-to-the-face.md)).

| Token         | Font              | Size/Line | Use |
|---------------|-------------------|-----------|-----|
| `display`     | Inter 600         | 28/34     | Large titles, empty states |
| `title`       | Inter 600         | 20/26     | Window/page titles |
| `heading`     | Inter 600         | 15/20     | Section headers |
| `body`        | Inter 400         | 13/18     | Default UI text |
| `body-strong` | Inter 600         | 13/18     | Emphasis, buttons |
| `caption`     | Inter 400         | 11/14     | Secondary metadata |
| `mono`        | JetBrains Mono 400| 13/18     | Code, terminal |

Global **text-scale token 90–150%**; every component must survive 150% without clipping (gallery-enforced). Switching to system fonts (`FontSource.system`) voids pixel-exact metrics — documented, intentional.

### 1.5 Shape, elevation, materials

**Radii:** `4` (inputs, small controls) · `8` (buttons, cards) · `12` (dialogs, popovers, frost panels) · `full` (pills, badges, scrollbar thumbs).

**Elevation:** three levels only —
- `0` flat: inline content.
- `1` raised: menus, cards, popovers — raised surface or frost, shadow `0 2px 8px`.
- `2` overlay: dialogs — shadow `0 8px 32px` + veil scrim.

**The shadows are theme tokens, and a rule never writes the numbers.** `box-shadow` is built ([ADR-0310](../book/src/adr/0310-a-shadow-is-a-stack-of-rectangles.md)) and each theme ships `--gb-elevation-1`, `--gb-elevation-2` and `--gb-elevation-3` as whole shadow values: a rule says `box-shadow: var(--gb-elevation-2)` and chooses nothing. The **geometry** above is the same in both themes — an object 8px off the page throws the same shape whatever colour the page is. The **alpha** is not, and that is the reason these are tokens at all: black at 16% is a clear soft edge on nord-light's `#eceff4` and very nearly nothing on nord-0, so the dark theme runs roughly two and a half times heavier. The single `rgba(0,0,0,.25)` / `.35` this line used to pin was one theme's answer written as if it were both. `--gb-elevation-3` is a level above the two here, for something the pointer is dragging.

The blur is drawn as a stack of nested rounded-rectangle fills rather than a real Gaussian: the rasterizer has no blur, and a one-pixel band is a gradient. The shadow is painted **under** the box rather than knocked out of it, which differs from CSS only where a background is translucent — see the ADR.

**What wears which** ([ADR-0312](../book/src/adr/0312-the-catalog-puts-the-two-new-properties-on.md)): `card` level 1, lifting to level 2 on `card.interactive:hover`; `affix:affixed` level 1; `dialog`, `tour-card` and `toast` level 2. **`popover`, `menu` and `tooltip` are an edge and not a shadow** — they are drawn in popup windows sized to the panel, so a shadow would fall outside the window and be clipped; that is a platform limit rather than a design one, and it is why the elevation *edge* stays part of the system rather than being a stand-in that went away. The edge stays on the shadowed surfaces too: a shadow cast onto another card falls on that card's own colour and says almost nothing, where a rim says it exactly. `--gb-elevation-3` is defined in both themes and used by nothing — it is the level for something the pointer is dragging.

**Materials — three, only three:**

| Material | Dark recipe | Light recipe | Used by |
|----------|-------------|--------------|---------|
| `opaque` | solid alias surfaces | solid alias surfaces | default for all content |
| `frost`  | backdrop blur 24px + nord1 @ 78% + saturation ×1.1 | blur 24px + white @ 72% + saturation ×1.1 | menus, popovers, opt-in sidebars, opt-in `titlebar` |
| `veil`   | black @ 40% | black @ 30% | scrim behind elevation-2 overlays |

Frost is exposed to widgets solely as `panel-material: frost` on overlay-capable components. Text on frost uses `--gb-text` and must meet 4.5:1 against the worst-case backdrop — the tint opacities above are the enforced floor.

**Fallback ladder (mandatory, automatic):** frost → opaque raised surface whenever backdrop sampling is unavailable, power-save is active, or reduce-transparency is on. In toolkit terms: the frost layer's 3-pass box blur (Vector API) runs on a downsampled backdrop copy, clipped to the rounded rect, cached while static; when the backdrop layer can't be sampled, the material resolves to `opaque` before paint. Components are designed opaque-first; frost is never load-bearing.

### 1.6 Iconography

Lucide, 24×24 grid, 2px stroke. Display sizes **16 / 20 / 24**, tinted by `color` (inherits text color). Icons beside text are optically centered on the cap height; icon-only buttons require an accessible name. App icon packs must match the grid and stroke to sit in the canon.

### 1.7 Motion

**Tokens.** Three durations, exposed as CSS variables and used by every built-in transition:

| Token | Value | Used for |
|-------|-------|----------|
| `--gb-motion-fast`    | 100ms | state feedback: hover, check, selection, value steps |
| `--gb-motion-base`    | 160ms | component transitions: popover, tooltip, tabs, toggle |
| `--gb-motion-overlay` | 240ms | overlays: dialog, toast, programmatic scroll |

**Easing keywords** (the CSS subset accepts these, not raw beziers): `ease-enter` = `cubic-bezier(0.2, 0, 0, 1)` (decelerate), `ease-exit` = `cubic-bezier(0.4, 0, 1, 1)` (accelerate), `linear` (continuous indicators only). No bounce or overshoot in system components.

**How it works** (mechanics; details in ARCHITECTURE §5):

- **Implicit = CSS transitions.** When style resolution changes a whitelisted property on a node whose style declares a `transition` for it, the value doesn't snap — a transition (start, target, duration, easing, start time) registers with the frame clock. Animated values live in a per-node **animation overlay** applied at paint time, never written back into computed style, so style recomputation and animation can't fight. Retargeting mid-flight starts from the *current animated value* — values never jump.
- **Whitelist = compositor-cheap properties only:** `opacity`, `transform` (translate/scale/rotate), `background-color`, `border-color`, `color`. **Layout properties never transition** — animating width/height would run Yoga per frame; the few sanctioned movement effects (tab indicator, toast reflow) are done with transforms.
- **Layer promotion.** A node animating `opacity`/`transform` is promoted to a repaint-boundary layer for the animation's duration: content rasterizes once, per-frame cost is compositing only — this is what makes animation cheap on a CPU renderer. Color transitions repaint their (small) layer per frame.
- **Interpolation:** colors in **OKLCH** (no gray dead zones, matches the ramp utility); lengths linear; transforms interpolated per component.
- **Entering = `@starting-style`.** An element's first styled frame starts no transition, so a window does not fade every control in as it opens, unless a `@starting-style` rule matches it. Then its declared transitions run from that style. `button[float]` enters this way ([ADR-0352](../book/src/adr/0352-an-element-enters-from-its-starting-style.md)).
- **Authored = `@keyframes`.** A named sequence run by `animation`, with CSS's delay, iteration, direction and fill, on the same frame clock and whitelist, beneath transitions. For applications: rule 4 below still keeps the toolkit's own sheets free of loops, and reduced motion drops keyframe animations entirely ([ADR-0353](../book/src/adr/0353-a-stylesheet-may-name-keyframes.md)).
- **Explicit = asking the host for frames.** There is no `AnimationController`. ADR-0081 refused a per-element controller for `spinner` and indeterminate progress — a loop that never ends has nothing to remember, and a controller would put two spinners permanently out of phase — and ADR-0178 refused one for a toast's reflow, where the interruption turned out to be three lines of arithmetic. What survived both refusals is `Departure`, which owns a timer and an ordering rather than a value or a clock, and `isAnimating()`, by which a widget asks the frame clock to keep coming. An application animating a `canvas` does the same thing.
- **Enter/exit lifecycle.** Overlays (menu, popover, tooltip, dialog, toast) run `opening → open → closing → removed`: the element stays mounted through `closing`, **input is disabled the instant closing starts** (no ghost clicks), removal fires on animation end. Interruptions reverse from current progress, never restart.
- **Frame-rate independent and testable.** Animations are functions of the frame timestamp, not frame counts; the headless backend uses a virtual clock (`clock.advance(160)`), so golden-image tests can snapshot any mid-animation frame deterministically. The frame loop is fully idle when no animation is active — no polling, no battery cost.

**Rules (testable, gallery-enforced):**

1. **Input feedback is instant.** Press states apply in 0ms (release fades out in `fast`); drags (slider, knob, fader, splitter, scroll) track the pointer 1:1 — animation never lags input.
2. **Exits are faster than enters.** Every exit uses a shorter duration and `ease-exit`.
3. **Focus is never delayed.** The focus ring appears instantly; only its disappearance may fade.
4. **Nothing loops** except explicit continuous indicators (indeterminate progress, spinner) — and, when `goldberry-vector` lands (`content-widgets.md` §7), Lottie content.
5. **Motion is meaning** — enter/exit, state confirmation, spatial continuity; never idle decoration.
6. **`prefers-reduced-motion`:** all transitions collapse to 0ms; `@keyframes` animations do not run; loops become opacity pulses; programmatic scroll jumps. Lottie, when it arrives, renders its final frame.

---

## 2. Interaction

### 2.1 States

Every control renders all of: rest, `:hover`, `:active` (pressed), `:focus-visible`, `:disabled`, and where applicable `:checked` / `:invalid`. Hover states change surface (one surface step) not text color; pressed states darken/compress; disabled is 45% opacity on the whole control, never color-remapped.

### 2.2 Focus

- One focus owner per window. `:focus` ≠ `:focus-visible` — the **focus ring (2px `--gb-focus`, 2px offset, follows the control's radius)** renders only for keyboard focus.
- Tab order = document order unless `tab-index` overrides; composites (radio groups, menus, lists, tabs) are one Tab stop with roving arrow-key focus inside.
- Overlays wrap a `focus-scope`: trap while open, restore on close.

### 2.3 Keyboard and platform conventions

- Accelerators use the **platform primary modifier** (`Cmd` on macOS, `Ctrl` elsewhere) via one `Shortcut` abstraction; menus display the platform's notation.
- **Dialog action order** is theme-controlled per platform: affirmative-right on macOS/Linux, affirmative-left available for Windows convention; `Enter` = default-role button, `Esc` = cancel-role.
- Text editing follows platform bindings (Home/End vs `Cmd`+arrows), supplied by the backend's key translation.

### 2.4 Scrolling and scrollbars

- **Overlay auto-hiding scrollbars** by default: 6px thumb → 10px with visible track on hover, accent color while dragging, fade after 800ms idle. `full`-radius thumb.
- **"Always show scroll bars"** app/user setting swaps to a classic reserved **12px gutter** — components must survive the gutter appearing (layout, not overlay). Built as `Scrollbars.ALWAYS`, a token stylesheet beside the density ([ADR-0364](../book/src/adr/0364-always-shown-scroll-bars-are-a-token-sheet.md)).
- Pixel-precise wheel/trackpad deltas with line fallback; track-click pages; keyboard per `scroll` spec in `core-widgets.md`.
- **Hard edges, no overscroll bounce.** Scroll-chaining: inner scroller consumes until its edge, then chains to the ancestor — but never chains out of a menu or popover.
- Nested same-axis scrollers are banned in the canon.

---

## 3. Component metrics

Behavior and API live in `core-widgets.md`; GDS pins the numbers. Metrics ship as **component-token defaults** (`--gb-button-height` etc.); app stylesheets may override component tokens, never structure. Heights at `regular` density (compact in parentheses).

| Component | Metrics |
|-----------|---------|
| `button` | height 32 (28); padding-x 12; icon+label gap 6; radius 8; `body-strong` | `outlined` is a 1px `--gb-border` on no fill in `--gb-text`, taking the accent or the danger line with `.primary` / `.danger`; `square` radius 0; `circle` a `full` radius on a box `--gb-button-height` wide; `float` a 1px `--gb-border-strong` edge, which is this sheet's elevation ([ADR-0347](../book/src/adr/0347-an-icon-only-button-is-a-circle-and-float-is-a-place.md)) |
| `text-input` / `select` | height 32 (28); padding-x 8; radius 4; `body`; fill `--gb-surface-sunken` — a field is a **well**, and the token is an alpha so it is one step below the page, a `panel` or a `card` alike (ADR-0168); caret and selection are one **line** tall, not one control tall; placeholder `--gb-text-placeholder`, not `--gb-text-muted`, which is two rungs from `--gb-text` and invisible inside a filled field |
| `text-area` | min-height 64; padding 8; radius 4 |
| `image` | natural size unless given one; loading and error fill `--gb-surface-2`, radius 4; error: min 40×40, padding 8, gap 4, Lucide `image-off` 20 over the alt text in `caption` and `--gb-text-muted` ([ADR-0358](../book/src/adr/0358-an-image-loads-off-the-frame-and-is-its-own-size.md)) |
| `checkbox` / `radio` | glyph 16; hit ≥32; label gap 8 |
| `toggle` | track 36×20; thumb 16; travel 16 |
| `slider` | track 4; thumb 16 (`full` radius); hit ≥32 cross-axis |
| `knob` | diameters 32 / 48; arc 270° (travel starts at 7:30); dial inset 5 from the ring; pointer line 0.35→0.78 of the dial radius, 2px; value drag 200px per full range, ×0.1 with fine modifier; click on the ring positions the value, click on the dial grabs it — see ADR-0090 |
| `menu` row | height 28 (24); padding-x 12; icon column 20; accelerator right-aligned `caption` |
| `tooltip` | padding **8/12**; radius 4; `caption`; delay 500ms show / 100ms move-between. The padding was `6/8` and **6 is not on §1.3's ramp**, which that section introduces with "no off-ramp values" — so the row could not be implemented without breaking a rule one section above it, and the shipped `8/12` is two legal steps ([ADR-0263](../book/src/adr/0263-three-numbers-in-one-row-and-nothing-watching.md)). The radius and the type rank departed for two hundred ADRs and **ship as this row says** now: the counter-argument for `body` — a tooltip is the only text on screen when it is read — is preserved in `controls.css` and is §1.4's to answer ([ADR-0380](../book/src/adr/0380-the-tooltip-row-is-what-ships.md)) |
| `dialog` | padding 24; title `title`; action bar gap 8, top margin 24; min width 320, max 80% window |
| `toast` | width 360; padding 12/16; radius 8; timeout 5s default, hover-pauses |
| `tabs` | tab height 36; padding-x 16; 2px active indicator in `--gb-accent` |
| `panel` / `card` | padding 16; radius 8; card = elevation 1 |
| `list` row | height 32 (26); padding-x 12; selection = `--gb-selection` full-row |
| `table` | header height 36 (30); row = `list` row metrics; cell padding-x 12; gap between columns 0 — a cell's padding is the gutter; 1px `--gb-border` under the header and none between rows; sort caret 12 in the header, trailing the label |
| `progress` | track height 4; radius `full` |
| `badge` | height 20; **min-width 20**; padding-x **4**; radius `full`; `caption`; filled variants pin their own foreground per §1.2's 4.5:1 floor — see ADR-0087. The minimum *is* the height, because equal width and height inside a `full` radius is what makes a one-digit chip a circle rather than a stadium; the padding is 4 and not §1.3's component default of 8 because `8 + a caption digit + 8` is 23 in a 20-tall box, so a badge with the default padding can never be round however large its minimum ([ADR-0259](../book/src/adr/0259-a-badge-with-one-digit-is-a-circle.md)) |
| `chip` | height **24**; padding-x 8; radius `full` (12); `body`; gap 6; dot **6**; dismiss 12. Four taller than a `badge` and one type rank up, and both numbers are the same claim: a badge is a plate you read and a chip is a target you hit, so §2.2's 24 applies and a filter you choose is content rather than metadata. It shares the badge's fill tokens — one row of numbers for a stadium with words in it, and two sets that have to be kept agreeing is how they stop agreeing. `flex-shrink: 0`, because a chip that gave width back would ellipse the word a user is choosing between, and the answer to a narrow row is a wrapped one. **A dot takes the foreground its own fill guarantees contrast against**, and the hue itself only when there is no fill: the first golden of `chip.success dot` came out with no dot at all, green on green ([ADR-0305](../book/src/adr/0305-a-chip-is-a-badge-you-can-press.md)) |
| `level-meter` (mic) | segment width 3, gap 1; peak-hold 1.5s |
| `link` | `body`; underline on hover and always in `:focus-visible`; external icon 12 with gap 4. **Built** ([ADR-0346](../book/src/adr/0346-a-link-is-a-word-and-the-desktop-opens-the-rest.md)) — the underline is `text-decoration` on `:hover`, the icon is 12 in the link's own ink after a 4 gap, and `visited` takes `--gb-text-muted`. Not the same thing as `button.link`: this is §2's *text* widget — a word inside a sentence, where the underline is WCAG's "never by colour alone" and therefore not optional. §8's subset **has** `text-decoration` now — `underline` and `line-through`, resolved, inherited and drawn at the face's own position and thickness ([ADR-0321](../book/src/adr/0321-a-rule-under-text-belongs-to-the-face.md)) — so what is left is the widget rather than the property |
| `segmented` | height 32 (28); segment padding-x 12; **radius 8 outer, 0 between**; 1px divider in `--gb-border`; `body-strong`. The bar carries the radius and a 1px edge, its padding is that edge's own width so the segments meet the inside of it, and the segments and the travelling indicator carry 8 less that border at the two ends of the row and nothing between. **Segments are equal — each exactly 1/n of the bar**, which is what lets the indicator travel by a percentage and is why the bar **takes the width it is given** and fills its parent with none. The hairlines beside the selection fade out, because the boundary the selection is does not need drawing twice. This row was amended to describe an inset pill while per-corner radii did not exist and is amended back with [ADR-0217](../book/src/adr/0217-a-segmented-control-is-joined-again.md); see also [ADR-0097](../book/src/adr/0097-a-selection-that-travels-needs-a-geometry.md), [ADR-0099](../book/src/adr/0099-an-indicator-travels-on-a-grid.md) and [ADR-0216](../book/src/adr/0216-a-corner-is-four-numbers-and-a-lint-reads-values-too.md) |
| `button.link` | transparent fill; ink `--gb-button-link-text`; padding-x **4**, not 12; `body`, not `body-strong`; `button` height and radius otherwise. **Built** ([ADR-0293](../book/src/adr/0293-a-button-that-reads-as-a-link.md)). The ink is a token of its own because `--gb-accent` is chosen as a *fill* and fails §1.2's 4.5:1 as ink on `--gb-surface-2` — 4.31 dark, 3.45 light. Hover is the overlay wash, like `.ghost`, because §2.1 says hover moves the surface and never the text colour; **no underline**, which is the row below's business and not this one's |
| `button.outlined` | 1px `--gb-border`; transparent fill; `button` metrics otherwise |
| `button.square` / `.circle` | radius 0 / `full`; a circle is `--gb-button-height` square |
| `button[float]` | offset 24 from both window edges; elevation 1; icon-only ⇒ 48 square |
| `date-picker` / `time-picker` | field = `text-input`; popup radius 12, padding 8; day cell 32 square, radius `full` |
| `time-picker` wheels | column width 48; cell height = `--gb-list-row-height` 32 (26), radius 4; five rows a column, the middle one filled with `--gb-accent` and the neighbours `--gb-text-muted`; gap 4 between columns. **Added by [ADR-0275](../book/src/adr/0275-a-wheel-is-a-column-that-wraps.md)**, because the row above gives the two pickers one line and every number on it is the *date* half — a day cell is not a wheel. Two digits in a 32-tall box would be square, and three columns of squares reads as a calculator, so the width is the column's own |
| `calendar` | day cell 32 (28) square; header row `caption` in `--gb-text-muted`; grid gap 0; radius `full` on the selected day, range ends only |
| `calendar-header` | month row `--gb-control-height`; label `body-strong` centred; prev/next 32 (28) square with a `full` radius, `--gb-text-muted` and an `--gb-overlay-hover` wash. **Added by [ADR-0274](../book/src/adr/0274-a-calendar-is-told-what-day-it-is.md)**, because §10 gives a calendar only a keyboard for changing month — `PgUp`/`PgDn` — and the row above is the *weekday* row: a calendar a mouse cannot page is not one. Neither arrow is a Tab stop, because §10 says the grid is one |
| `color-picker` | swatch 24, radius 4; plane 200×160; hue/alpha sliders `slider` metrics; preset swatch 20, gap 4. Two notes from building it ([ADR-0276](../book/src/adr/0276-a-plane-is-hsv-and-the-hex-is-the-value.md)): the ramps are **12** tall rather than a `slider`'s 4, because a ramp is something you read as well as drag and a 4-point rainbow is a line; and the plane has **no compact value**, because a colour space is not denser on a small screen — a compact plane would give a dragged colour a coarser resolution rather than a tidier layout |
| `code-input` | box 40×48 (36×44); gap 8; radius 4; `title`, centred; group gap 16 at the midpoint when `length` is even |
| `breadcrumbs` | height 24; `body`; separator = `chevron-right` 16 in `--gb-text-muted`, gap 4; overflow menu after 4 crumbs. **Built** ([ADR-0306](../book/src/adr/0306-the-last-crumb-is-where-you-are.md)). A crumb is 20 tall with padding-x 4 and a 4 radius, so the hover wash reads as a target rather than a highlighted word; 4 is §1.3's legal value below the component default, the one `badge` and `button.link` already reach for. The separator is **square** at 16 — a chevron is a mark drawn to fill its box, so 16×24 would draw a stretched one, which is `tab-new`'s lesson. The current crumb is the strong weight in full ink with **no fill and no hover**: §6 says it is not a link, and a filled current crumb reads as a button. The row does not wrap — a path wrapped to two lines puts where-you-are under where-you-started — which is why there is an overflow menu at all |
| `steps` | marker 24 (`full` radius); connector 2px; label `body-strong`, description `caption`; gap 12 horizontal / 8 vertical | **Built** ([ADR-0344](../book/src/adr/0344-a-list-of-steps-writes-where-each-one-stands.md)). The disc is 24 with a 2px ring; current fills with the accent, done with `--gb-success` and a tick, error with `--gb-danger` and a cross, upcoming is the ring alone. The label is a cell as tall as the disc with its text centred, so it sits on the disc's centre at every density. A vertical list's connector is 16 tall and 11 in from the edge — half of 24 less half of 2 |
| `wizard` | `steps` on top with 24 below; action bar = `dialog`'s (gap 8, top margin 24) | **Built** ([ADR-0344](../book/src/adr/0344-a-list-of-steps-writes-where-each-one-stands.md)): `wizard-content` grows with 24 above it and 12 between its children; `wizard-actions` is `dialog-actions`' row |
| `collapse` | header height 40 (36); padding-x 12; chevron 16; body padding 12; 1px `--gb-border` between siblings |
| `carousel` | dot 8, gap 8, active `--gb-accent`; prev/next = `button.ghost.circle`; content padding 0 |
| `statistic` | value `display`, label `caption` in `--gb-text-muted`, delta `body-strong`; gap 4; sparkline 64×24 |
| `skeleton` | radius 4 (`full` for `circle`); text line height = its token's line-height, last line 60% width; pulse 1.2s `linear` |
| `message` | padding 12/16; radius 8; icon 20 with gap 12; 1px border and a 4% tint of its `kind` colour |
| `tour` | popover radius 12, padding 16, max width 320; veil per §1.5; target cut-out inset −4 with radius 8 |
| `tree` row | `list` row metrics; indent 20 per level; chevron 16 in the indent gutter |
| `timeline` | marker 12 (`full`); axis 2px in `--gb-border`; row gap 16; timestamp `caption` | **Built** ([ADR-0345](../book/src/adr/0345-a-timeline-is-a-list-whose-line-goes-on.md)). The rail is a 20 column and the dot sits centred in a cell one `--gb-line-body` tall, so it is on the label's centre at every density; an entry with an icon has a 20 disc; the pending marker is a 2px ring in `--gb-border-strong`. The 16 between rows is the body's bottom padding rather than a gap, because a gap would break the line |
| `affix` | no metrics of its own; `:affixed` adds elevation 1 |

---

### 3.1 Component animation specs

Durations reference §1.7 tokens (`fast`/`base`/`overlay`); enters use `ease-enter`, exits `ease-exit` unless noted. Anything not listed does not animate.

| Component | Trigger → what animates |
|-----------|--------------------------|
| all controls | hover: `background-color` fast · press: **instant in**, fast out · disabled: `opacity` fast |
| `checkbox` / `radio` | check/dot: scale 0.6→1 + `opacity`, base · color fast |
| `toggle` | thumb `translate` base; track color base (same clock — they arrive together) |
| `slider` / `knob` / `fader` | drag: **1:1, no animation** · keyboard/programmatic value step: fast |
| focus ring | in: **instant** · out: `opacity` fast |
| `tooltip` | in (after 500ms delay): `opacity` + 2px rise, fast · out: `opacity` 80ms · move-between: instant reposition, never slides |
| `popover` / `menu` / `select` popup | in: `opacity` 0→1, `translateY` −4→0, `scale` 0.98→1 from anchor origin, base · out: `opacity` fast |
| `dialog` | in: veil `opacity` overlay + panel `opacity` & `scale` 0.96→1, overlay · out: base, reverse |
| `toast` | in: slide 16px from edge + `opacity`, overlay · out: `opacity` base · **siblings reflow via `translate`** base (explicit controller — the one sanctioned movement effect, transforms not layout) |
| `tabs` | active indicator `translate`+width between tabs, base · panel: `opacity` cross-fade fast |
| `progress` (determinate) | value change: fill base |
| `progress` (indeterminate) | sweep loop 1.2s `linear` · reduced-motion: opacity pulse 1.2s |
| `spinner` | rotation 900ms `linear` loop · reduced-motion: opacity pulse |
| `scroll` | wheel/drag: direct · `scrollIntoView` / programmatic: overlay duration |
| `list` | selection/hover: `background-color` fast · item add/remove: none in v1 (deferred with virtualization) |
| `table` | `list`'s row transitions · sort change: none — the rows are re-ordered by the application and a row that travelled would be a row the model no longer has in that place · column resize: 1:1, like `split-pane`'s drag |
| `split-pane` | drag: 1:1 · collapse/expand: instant in v1 |
| frost surfaces | fade in/out as whole layers with their component; **blur radius never animates** |
| `camera-view` / meters | live content is data, not motion — permission-state placeholders cross-fade fast |
| `link` | underline: `opacity` fast — the underline is always laid out, so nothing reflows |
| `segmented` | selection indicator `translate` between segments, base · selected label `color`, base (**same clock — they arrive together**) · hover/press wash, fast. The `width` half of this row is gone and cannot come back: it is not on §1.7's whitelist, and on a grid every cell is the same size so there is nothing for a width to move ([ADR-0099](../book/src/adr/0099-an-indicator-travels-on-a-grid.md)) |
| `button[float]` | in: `opacity` + `scale` 0.9→1, base · out: reverse, fast |
| `date-picker` / `color-picker` / autocomplete popup | as `popover` |
| `calendar` | month change: content `opacity` cross-fade fast — **never a slide**, because the grid is the same shape and sliding it implies the days moved |
| `code-input` | focus moves between boxes: ring is instant per §1.7 rule 3; no travel effect |
| `steps` | state change: marker `background-color` + `color` + `border-color` fast; connector fill `background-color` base — **not** `scaleX`, which needs a transform origin §8's subset does not express ([ADR-0344](../book/src/adr/0344-a-list-of-steps-writes-where-each-one-stands.md)) |
| `collapse` | chevron `rotate` base; body **does not animate** — height is not on the whitelist (§1.7) and the body is unmounted while closed |
| `carousel` | slide change: `translate` base, `ease-enter` · auto-advance suspended on hover, on focus within, and under reduced motion |
| `skeleton` | opacity pulse 1.2s `linear` loop — the one decoration allowed to loop (§1.7 rule 4) · reduced-motion: holds at its dimmest |
| `message` | in: `opacity` + 2px rise, base · out: `opacity` fast · siblings reflow via `translate`, like `toast` |
| `tour` | stop change: veil cut-out `translate`+size base, popover as `popover`; the target scrolls into view *before* the popover moves |
| `tree` | expand/collapse: chevron `rotate` base; rows do not animate in or out (as `list`) |
| `affix` | detach/attach: `opacity` on the elevation shadow, fast — the child itself never animates position |

## 4. Accessibility baseline

Contrast per §1.2 including the frost floor; full keyboard operability per §2 and per-widget maps in `core-widgets.md`; text scale to 150% without clipping; reduce-motion (§1.7); **reduce-transparency** (all materials → opaque); "always show scroll bars" (§2.4); **high-contrast theme as an alias swap** (black/white surfaces, 2px strokes) — proving the token architecture, not a special code path; hit targets ≥32. The semantics tree (ARCHITECTURE §13) carries role + name for every widget by construction.

---

## 5. Governance

GDS versions with the KDL markup schema. Stable tier: alias tokens, the three materials, component contracts and metrics tables. Visual details may evolve within a major version. A new widget enters the canon only with a `core-widgets.md`-format spec (behavior, states, keyboard, semantics) **and** a §3 metrics row **and** gallery coverage in both themes — before code. The rule that keeps the system alive is Principle 3: token or extend, never improvise.