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
- Light and dark are peer themes, both shipped, switchable at runtime; `system` mode follows `prefers-color-scheme`.

### 1.3 Spacing and sizing

- **Base unit 4 px.** Legal ramp: `2, 4, 8, 12, 16, 20, 24, 32, 40, 48, 64`. No off-ramp values.
- Component padding defaults **8 / 12**; gap between related controls **8**; between groups **16**; window content margins **16** (compact windows) / **24** (regular).
- Hit targets **≥ 32×32** logical px even when the visual is smaller (checkbox glyph 16px, hit area 32).
- **Density:** `--gb-density` `regular` (default) | `compact` — control heights 32 / 28, list rows 32 / 26. A user preference applied app-wide; token-conformant apps adapt with zero code.

### 1.4 Typography

Exactly two shipped typefaces — **Inter** (UI) and **JetBrains Mono** (code) — plus the routed emoji slot (`goldberry-emoji`). **No fallback chain**: missing glyphs render `.notdef` by design. All sizes logical px.

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
- `1` raised: menus, cards, popovers — raised surface or frost, shadow `0 2px 8px rgba(0,0,0,.25)`.
- `2` overlay: dialogs — shadow `0 8px 32px rgba(0,0,0,.35)` + veil scrim.

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
- **Explicit = the `Animation` API.** `AnimationController` (forward/reverse/repeat/stagger) on the same frame clock — used internally by indeterminate progress, spinner, toast reflow, and available to apps for `canvas` work; also drives Lottie in `goldberry-vector`.
- **Enter/exit lifecycle.** Overlays (menu, popover, tooltip, dialog, toast) run `opening → open → closing → removed`: the element stays mounted through `closing`, **input is disabled the instant closing starts** (no ghost clicks), removal fires on animation end. Interruptions reverse from current progress, never restart.
- **Frame-rate independent and testable.** Animations are functions of the frame timestamp, not frame counts; the headless backend uses a virtual clock (`clock.advance(160)`), so golden-image tests can snapshot any mid-animation frame deterministically. The frame loop is fully idle when no animation is active — no polling, no battery cost.

**Rules (testable, gallery-enforced):**

1. **Input feedback is instant.** Press states apply in 0ms (release fades out in `fast`); drags (slider, knob, fader, splitter, scroll) track the pointer 1:1 — animation never lags input.
2. **Exits are faster than enters.** Every exit uses a shorter duration and `ease-exit`.
3. **Focus is never delayed.** The focus ring appears instantly; only its disappearance may fade.
4. **Nothing loops** except explicit continuous indicators (indeterminate progress, spinner) and Lottie content.
5. **Motion is meaning** — enter/exit, state confirmation, spatial continuity; never idle decoration.
6. **`prefers-reduced-motion`:** all transitions collapse to 0ms; loops become opacity pulses; Lottie renders its final frame; programmatic scroll jumps.

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
- **"Always show scroll bars"** app/user setting swaps to a classic reserved **12px gutter** — components must survive the gutter appearing (layout, not overlay).
- Pixel-precise wheel/trackpad deltas with line fallback; track-click pages; keyboard per `scroll` spec in `core-widgets.md`.
- **Hard edges, no overscroll bounce.** Scroll-chaining: inner scroller consumes until its edge, then chains to the ancestor — but never chains out of a menu or popover.
- Nested same-axis scrollers are banned in the canon.

---

## 3. Component metrics

Behavior and API live in `core-widgets.md`; GDS pins the numbers. Metrics ship as **component-token defaults** (`--gb-button-height` etc.); app stylesheets may override component tokens, never structure. Heights at `regular` density (compact in parentheses).

| Component | Metrics |
|-----------|---------|
| `button` | height 32 (28); padding-x 12; icon+label gap 6; radius 8; `body-strong` |
| `text-input` / `select` | height 32 (28); padding-x 8; radius 4; `body` |
| `text-area` | min-height 64; padding 8; radius 4 |
| `checkbox` / `radio` | glyph 16; hit ≥32; label gap 8 |
| `toggle` | track 36×20; thumb 16; travel 16 |
| `slider` | track 4; thumb 16 (`full` radius); hit ≥32 cross-axis |
| `knob` | diameters 32 / 48; arc 270° (travel starts at 7:30); dial inset 5 from the ring; pointer line 0.35→0.78 of the dial radius, 2px; value drag 200px per full range, ×0.1 with fine modifier; click on the ring positions the value, click on the dial grabs it — see ADR-0090 |
| `menu` row | height 28 (24); padding-x 12; icon column 20; accelerator right-aligned `caption` |
| `tooltip` | padding 6/8; radius 4; `caption`; delay 500ms show / 100ms move-between |
| `dialog` | padding 24; title `title`; action bar gap 8, top margin 24; min width 320, max 80% window |
| `toast` | width 360; padding 12/16; radius 8; timeout 5s default, hover-pauses |
| `tabs` | tab height 36; padding-x 16; 2px active indicator in `--gb-accent` |
| `panel` / `card` | padding 16; radius 8; card = elevation 1 |
| `list` row | height 32 (26); padding-x 12; selection = `--gb-selection` full-row |
| `progress` | track height 4; radius `full` |
| `badge` | height 20; padding-x 8; radius `full`; `caption`; filled variants pin their own foreground per §1.2's 4.5:1 floor — see ADR-0087 |
| `level-meter` (mic) | segment width 3, gap 1; peak-hold 1.5s |
| `link` | `body`; underline on hover and always in `:focus-visible`; external icon 12 with gap 4 |
| `segmented` | height 32 (28); segment padding-x 12; radius 8 outer, 0 between; 1px divider in `--gb-border`; `body-strong` |
| `button.outlined` | 1px `--gb-border`; transparent fill; `button` metrics otherwise |
| `button.square` / `.circle` | radius 0 / `full`; a circle is `--gb-button-height` square |
| `button[float]` | offset 24 from both window edges; elevation 1; icon-only ⇒ 48 square |
| `date-picker` / `time-picker` | field = `text-input`; popup radius 12, padding 8; day cell 32 square, radius `full` |
| `calendar` | day cell 32 (28) square; header row `caption` in `--gb-text-muted`; grid gap 0; radius `full` on the selected day, range ends only |
| `color-picker` | swatch 24, radius 4; plane 200×160; hue/alpha sliders `slider` metrics; preset swatch 20, gap 4 |
| `code-input` | box 40×48 (36×44); gap 8; radius 4; `title`, centred; group gap 16 at the midpoint when `length` is even |
| `breadcrumbs` | height 24; `body`; separator = `chevron-right` 16 in `--gb-text-muted`, gap 4; overflow menu after 4 crumbs |
| `steps` | marker 24 (`full` radius); connector 2px; label `body-strong`, description `caption`; gap 12 horizontal / 8 vertical |
| `wizard` | `steps` on top with 24 below; action bar = `dialog`'s (gap 8, top margin 24) |
| `collapse` | header height 40 (36); padding-x 12; chevron 16; body padding 12; 1px `--gb-border` between siblings |
| `carousel` | dot 8, gap 8, active `--gb-accent`; prev/next = `button.ghost.circle`; content padding 0 |
| `statistic` | value `display`, label `caption` in `--gb-text-muted`, delta `body-strong`; gap 4; sparkline 64×24 |
| `skeleton` | radius 4 (`full` for `circle`); text line height = its token's line-height, last line 60% width; pulse 1.2s `linear` |
| `message` | padding 12/16; radius 8; icon 20 with gap 12; 1px border and a 4% tint of its `kind` colour |
| `tour` | popover radius 12, padding 16, max width 320; veil per §1.5; target cut-out inset −4 with radius 8 |
| `tree` row | `list` row metrics; indent 20 per level; chevron 16 in the indent gutter |
| `timeline` | marker 12 (`full`); axis 2px in `--gb-border`; row gap 16; timestamp `caption` |
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
| `split-pane` | drag: 1:1 · collapse/expand: instant in v1 |
| frost surfaces | fade in/out as whole layers with their component; **blur radius never animates** |
| `camera-view` / meters | live content is data, not motion — permission-state placeholders cross-fade fast |
| `link` | underline: `opacity` fast — the underline is always laid out, so nothing reflows |
| `segmented` | selection indicator `translate`+width between segments, base — `tabs`' effect, same controller |
| `button[float]` | in: `opacity` + `scale` 0.9→1, base · out: reverse, fast |
| `date-picker` / `color-picker` / autocomplete popup | as `popover` |
| `calendar` | month change: content `opacity` cross-fade fast — **never a slide**, because the grid is the same shape and sliding it implies the days moved |
| `code-input` | focus moves between boxes: ring is instant per §1.7 rule 3; no travel effect |
| `steps` | state change: marker `background-color` + `color` fast; connector fill `transform: scaleX` base |
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