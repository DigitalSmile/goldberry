# Status

What is built, milestone by milestone, against the ladder in
`docs/ARCHITECTURE.md` §16.

**What is *not* built is in [TODO.md](TODO.md)** — deferred items, known gaps,
specified-and-unbuilt surface, and the questions each of them is waiting on. This
page is the other half: it says what works and what it cost to find out.

| Milestone | State | In one line |
|---|---|---|
| [Foundation](#foundation) | **done** | The build, the module graph, the toolchain and the decision log |
| [M0 — Skeleton](#m0--skeleton) | **done** | One native library on four targets, two backends, a window at the right fractional DPI |
| [M1 — Vertical slice](#m1--vertical-slice) | **started** | Blend2D rasterizes, HarfBuzz shapes, text lays out, and a frame's cost is measured |
| [M2 — Widgets & style](#m2--widgets--style) | **done** | CSS, KDL, the three trees, input, motion — and every §3 control, `select` included |
| [M3 — Shell](#m3--shell) | **started** | **The whole of §7**, §9's `tray-icon`, `menubar`, §5's containers, the whole `scroll` family and §4's fields — with the clipboard, text input, a focus trap and a third rank of every semantic hue that nothing had asked for. The showcase is a menu bar, a bar and seven walls of cards, in a window that opens maximized |
| [M4 — GPU](#m4--gpu) | not started | `canvas3d`, GPU composition |
| [M5 — Hardening](#m5--hardening) | not started | Text editing depth, AccessKit bridge, IME preedit, docs, 0.1 release |
| [Content modules](#content-modules) | not started | Eleven optional artifacts in `docs/content-widgets.md`; nothing exists, nothing scheduled |

## Foundation

**Done.**

- Multi-module Gradle (Groovy DSL), version catalog, convention plugins, JPMS module
  graph, JDK 25 toolchain, JUnit 6, licence disclosure, decision log.

## M0 — Skeleton

**Done.** The bindings, the backends and a window, on every target.

### The native library

- **The superbuild links on all four targets.** Blend2D, AsmJit, SDL3, Yoga and HarfBuzz
  statically combine into one `libgoldberry` exporting exactly the symbols on the export
  list and nothing else — both Linux targets in CI's manylinux containers,
  `macos-aarch64` on an Apple Silicon runner, and **`windows-x64` under MSVC**. The
  layout probe passes against the real library, and Yoga's measure callback crosses in
  both directions including the `YGSize` struct-by-value return
  ([ADR-0017](adr/0017-proving-the-struct-by-value-upcall.md)), so the hand-written
  binding mechanism is proven end to end.
- **Windows closed the milestone**: `goldberry.dll` builds, `:natives:test` passes
  against it with `goldberry.native.required=true` so nothing skips, and the golden
  images match — which answers the MSVC `/INCLUDE:` and `.def` branch of the export
  machinery and Win64's 4-byte `long` at the same time
  ([ADR-0041](adr/0041-three-platforms-four-artifacts-two-backends.md))

### The bindings and the backends

- **Yoga's node API is bound**, and the callback is now driven by real layout passes
  rather than by a C probe written for the purpose
  ([ADR-0029](adr/0029-yogas-node-api-and-who-owns-a-node.md)). SDL3's lifecycle, error
  and version calls are bound and tested against the real library
  ([ADR-0018](adr/0018-sdl-conventions-stop-at-the-boundary.md)). The backend SPI, the
  `headless` backend and the `sdl3` backend are in `:core`, with fractional DPI correct
  by construction ([ADR-0019](adr/0019-the-backend-spis-first-cut.md)) and background
  work on virtual threads that completes on the UI thread
  ([ADR-0020](adr/0020-one-ui-thread-and-virtual-threads-behind-it.md)).
- **The showcase opens a window and presents frames**
  ([ADR-0021](adr/0021-the-example-is-a-separate-build.md)), through a `Window` front
  door that names no backend and builds no event loop
  ([ADR-0022](adr/0022-window-is-the-front-door.md)).

## M1 — Vertical slice

**Started.** Everything in the pipeline exists and runs; what is unfinished is
the *breadth* of the 60 fps claim, not the pipeline.

### Rasterizing and shaping

- **Blend2D rasterizes the frame, HarfBuzz shapes the text.** `Frame` no longer writes
  pixels by hand: it wraps the platform's own buffer in a `BLImage` without copying it,
  scales the context by the display factor so coordinates stay logical and fractional
  edges antialias rather than snap, and blends with alpha that now means something
  ([ADR-0031](adr/0031-blend2d-and-the-borrowed-buffer.md)). The showcase paints through
  it. Shaping takes UTF-16 straight from a Java `String`, so the cluster indices point
  back into the caller's own text
  ([ADR-0032](adr/0032-shaping-is-utf16-in-glyphs-out.md)).
- **Text draws.** Blend2D's font chain is bound and a `GlyphRun` reaches the rasterizer:
  `Font` in `:core` owns a HarfBuzz font and a Blend2D one over the same bytes, shapes
  in design units and puts the size on the Blend2D font alone, so the font matrix is the
  only thing that converts
  ([ADR-0034](adr/0034-one-size-and-the-design-unit-crossing.md)). The showcase draws
  two lines of Inter, and the tests assert *where* the ink landed — the inked span
  matches the measured width, which fails by a factor of 128 if either side of that
  crossing is wrong.
- **Yoga and Blend2D now meet**: `BoxPainter` lays a flexbox tree out and fills the
  result, setting Yoga's point scale factor from the display scale so computed edges
  land on physical pixels — the first code for which the fractional-DPI claim is a
  mechanism rather than an intention. Inter, JetBrains Mono, OpenMoji and Lucide's 1544
  icons are fetched at build time, pinned by checksum, and packaged into
  `goldberry-core`
  ([ADR-0033](adr/0033-assets-are-fetched-and-compiled-not-committed.md))

### Text in a layout

- **Text takes part in layout.** A `Paragraph` shapes once and wraps with arithmetic
  over that one `GlyphRun`, so its measure function answers Yoga from inside a layout
  pass without shaping again
  ([ADR-0036](adr/0036-the-paragraph-is-shaped-once-and-wrapped-many-times.md)). A `Box`
  with text is a measured leaf: the showcase's body wraps to whatever width the sidebar
  leaves it, and its siblings are positioned against the height that comes back. Two
  numbers are written down in that layout — the bar's height and the padding — and
  everything else comes from content.
- **The cache and the benchmarks are done**
  ([ADR-0037](adr/0037-what-the-text-path-costs.md)): `./gradlew benchmark` measures the
  text path, and the numbers say the upcall crossing is ~0.3 µs, a memoised wrap 0.02
  µs, and shaping 56 µs — so `ParagraphCache` caches shaping and nothing else.

### Threads, layers and damage

- **Painting is now multithreaded, and icons draw.** Blend2D rasterizes a frame across
  up to four workers on any surface over 400×300, which takes a 960×640 paint from 0.47
  ms to 0.34 ms and a 4K one from 6.0 ms to 2.3 ms; a threaded frame is asserted
  pixel-identical to a synchronous one at every worker count
  ([ADR-0042](adr/0042-blend2ds-workers-and-how-many.md)). Blend2D's path API is bound
  and Lucide's 1544 icons reach the screen as stroked paths, all of them asserted to
  parse ([ADR-0043](adr/0043-icons-are-stroked-paths.md)). And a typeface is loaded once
  rather than once per size: `FontFace` holds the shaper and Blend2D's face, so a second
  size costs 4.4 µs instead of 681 and no second copy of the file
  ([ADR-0044](adr/0044-one-face-many-sizes.md)).
- **Four symbols were added to the export list**, the first since it caught its third
  local-symbol bug: `bl_context_blit_image_d` and `bl_context_set_global_alpha` for
  layers, then `bl_context_clip_to_rect_d` and `bl_context_restore_clipping` for the
  partial repaint. Nothing else was needed — the offscreen pixels are a `PixelBuffer`
  allocated in Java and wrapped with the already-exported `bl_image_init_as_from_data`,
  which is the principle the export list states in its own comment. `BlendLayerTest` is
  seven pixel assertions that cannot pass unless both really exported, and the ELF, MSVC
  `.def` and Mach-O branches are answered by the next CI run rather than by argument. —
  [ADR-0071](adr/0071-a-layer-is-a-subtrees-raster.md),
  [ADR-0018](adr/0018-sdl-conventions-stop-at-the-boundary.md)

### What a frame costs

- **The 60 fps claim now holds at the tail, not just the median.** A 960×640 frame with
  a wrapped paragraph used to run at a 7.86 ms median and a 14.18 ms p95 — a factor of
  two in hand on the median and none at the tail. Pacing the loop to the display
  ([ADR-0047](adr/0047-a-frame-nobody-sees-costs-full-price.md)) took that to a **3.13
  ms median and a 4.28 ms p95**, which is 3.9× of headroom where there was effectively
  none; the old numbers reproduce exactly when the pacer is turned off with
  `-Dgoldberry.frame.rate=0`, which is what they were measuring. Two thirds of that
  frame was work thrown away on frames the display never scanned out.
- **What remains of the claim is breadth, not budget**: it is still one machine, and
  that machine is a VirtualBox VM. The milestone asks for Linux, macOS and Windows.
- **A window was laying its tree out twice per frame**, once to paint and once to find
  out where it had painted, and nobody had noticed. `HitTest.capture` took a frame and a
  box tree and built a whole second Yoga tree to answer. `HitTest.capture(RenderTree)`
  reads the pass `update` already ran. —
  [ADR-0069](adr/0069-the-render-tree-is-retained.md),
  [ADR-0054](adr/0054-hit-testing-runs-against-the-painted-frame.md)
- **Painting is not what a frame spends its time on.** Measured on linux-x64 at 960×640
  under Wayland, over sixty frames: acquiring the buffer costs 130–400 µs, **painting
  0.6–3.6 ms** (typically ~1.3), and **presenting 1.5–21 ms** (typically ~10). Present
  dominates by roughly an order of magnitude. **What it is doing is now known** — and it
  is not, as this entry used to say, mostly waiting on the compositor. SDL's Wayland
  driver implements no window surface, so `SDL_GetWindowSurface` falls back to a hidden
  `SDL_Renderer`: every present is a copy into a streaming texture, a render pass, and a
  swapchain wait. At 960×640 that splits about 1.05 ms of copy, 0.7 ms of
  render-and-present, and 4.8 ms of blocking — three quarters of present is a block
  rather than work ([ADR-0046](adr/0046-what-present-actually-does.md)). **The largest
  of those is fixed**: the loop was running at ~105 fps into a 59.96 Hz panel and
  throwing two frames in five away. Goldberry now asks SDL to hold each present until
  vertical blank, and where that request is ignored the loop paces itself to the refresh
  rate read off the window's current display — `SDL_GetDisplayForWindow` and
  `SDL_GetCurrentDisplayMode`, with `SDL_DisplayMode` verified against the compiled
  library by the layout probe. Paced, present falls from 5.51 ms to 1.20 ms — the block
  does not shrink, it disappears, leaving exactly the CPU that was always underneath —
  paint falls with it from 2.25 ms to 1.61 ms, and the UI thread spends 165 ms of each
  second in the frame path instead of 862, showing the same frames
  ([ADR-0047](adr/0047-a-frame-nobody-sees-costs-full-price.md)). What is left: **damage
  tracking**, now worth under a millisecond a frame; and **owning the renderer**, the
  only route to the zero-copy path
  ([ADR-0031](adr/0031-blend2d-and-the-borrowed-buffer.md)) was believed to have.
  Blend2D's `thread_count` is a fourth, and only matters if paint ever becomes the
  bottleneck. — [ADR-0031](adr/0031-blend2d-and-the-borrowed-buffer.md),
  [ADR-0046](adr/0046-what-present-actually-does.md),
  [ADR-0047](adr/0047-a-frame-nobody-sees-costs-full-price.md)
- **Rasterization is now the frame, and there is nothing else of consequence left.**
  With Blend2D pinned to one thread a 960×640 frame is about 320 µs, essentially all of
  it painting; threaded, it spreads over four workers. Two rounds of removing CPU work
  have made damage tracking and layer promotion the honest next target rather than one
  option among several. —
  [ADR-0070](adr/0070-the-cascade-resolves-invalidated-nodes.md),
  [ADR-0069](adr/0069-the-render-tree-is-retained.md)
- **Painting now dominates a frame, and half of that reversal is a driver change.** Over
  119 frames at 960×640 with text: buffer 0.18 ms, **paint 5.10 ms**, present 1.92 ms,
  total 7.86 ms median, 14.18 ms at p95, and 3 frames of 119 over the 16.67 ms budget.
  ADR-0031 had paint at ~1.3 ms and present at ~10 ms and concluded present dominated by
  an order of magnitude. Text is what moved paint; **X11 rather than Wayland is what
  moved present**, since these frames were measured on X11 after the Wayland run crashed
  the compositor. The like-for-like Wayland measurement is still owed, and nothing here
  made present faster. ~~Blend2D's `thread_count` was parked in ADR-0031 as "only
  matters if paint ever becomes the bottleneck"; on these numbers it has.~~ **Taken.**
  Up to four workers, on any surface over 400×300. —
  [ADR-0037](adr/0037-what-the-text-path-costs.md),
  [ADR-0031](adr/0031-blend2d-and-the-borrowed-buffer.md),
  [ADR-0042](adr/0042-blend2ds-workers-and-how-many.md)

## M2 — Widgets & style

**Engines done; the catalog is complete except `select`,** whose popup list
belongs with M3's overlays. Every other widget in `docs/core-widgets.md` §3 is
built, and each was finished against both specification documents rather than
merely made to appear.

### The engines

- **The CSS engine is done, end to end.** A hand-written tokenizer and parser for the §8
  subset, matching right-to-left with backtracking, the four fixed cascade layers,
  custom properties and `var()` — ending at a `ComputedStyle` that carries typed values
  and nothing else ([ADR-0049](adr/0049-the-css-engine-stops-at-computedstyle.md)).
  `Box.style(ComputedStyle)` is the join the property split was stated for: layout
  properties land on the fields Yoga reads, paint properties on the ones Blend2D reads.
- **Nord light and dark ship** as custom-property layers — two files whose only selector
  is `:root`, so switching a theme repaints widget rules that never mention a colour
  (§10).
- **Golden-image CI runs on all three platforms**: six scenes driven through the whole
  pipeline, compared with a per-channel *and* an area tolerance, because Blend2D JITs
  its pipelines per CPU and bit-equality across AVX2 and NEON is not a promise anyone
  made ([ADR-0050](adr/0050-golden-images-have-a-tolerance.md)).
- **KDL 2.0 parses and inflates**, including the §9 example document as a test, with a
  registry that refuses unknown nodes by position; and **hot reload works for
  stylesheets and markup alike** — strict on first load, forgiving on every reload,
  because a file being edited is broken more often than it is whole
  ([ADR-0051](adr/0051-kdl-is-parsed-here-and-reloading-is-forgiving.md)).
- **All three trees now exist.** Widgets are immutable records; the element tree
  persists across rebuilds and is what the cascade talks to, so `:hover` survives a
  parent re-describing its child; state lives on the element, `setState` mutates
  immediately and defers the rebuild, and ten calls in one handler cost one build
  ([ADR-0052](adr/0052-state-lives-on-the-element-and-rebuilds-are-deferred.md), which
  closes the gap ADR-0004 left open). The render tree is materialized as a `Box` tree
  per frame rather than retained
  ([ADR-0053](adr/0053-the-render-tree-is-a-box-tree-for-now.md)).
- **Five primitives ship** — `text`, `row`, `column`, `panel`, `spacer` — and **the
  parity invariant of §11 is enforced**: each is a Java record, a KDL node and
  CSS-selectable by type, id and class, with a test asserting the Java-built and
  KDL-built values are equal. A golden image runs the whole stack, KDL to pixels.

### The paint layer, the cascade and the layout properties

- **The paint layer can now draw what the design system asks for.** `border-radius`,
  `border`, `outline` and `opacity` reach `Box`, and a rounded rectangle is built from
  four cubics through the already-exported `bl_path_cubic_to` rather than from a new
  Blend2D symbol — so the corner works on every target on the first CI run instead of
  the one after the export list found out
  ([ADR-0064](adr/0064-a-rounded-rectangle-is-four-cubics.md)).
- **The cascade inherits, which closed a bug and a gap at once.** A checkbox's label
  rendered black on the dark theme, because `StyleResolver` inherited custom properties
  and nothing else: the label is a `text` child element no rule names, so it resolved to
  `ComputedStyle.INITIAL`'s black. `button` had never shown it, because it copies
  `style.color()` onto its child boxes by hand and bypasses the cascade. `color` and the
  typography now inherit down the element tree — and `cursor` deliberately does not,
  because it already inherits through the stack of painted rectangles (ADR-0057), and
  two mechanisms for one property disagree the first time a box has no element behind
  it. `WidgetRenderer` resolves styles on the way down and builds boxes on the way up,
  which is the shape inheritance forces. **§1.4's type scale ships**, and it was the
  blocker's other half: every typography token is a size, a line height and a weight,
  and all three inherit. `font-family`, `font-size`, `font-weight` and `line-height`
  reach `ComputedStyle`; a `Fonts` book caches faces by family+weight and fonts by
  (face, size), because a widget tree is re-rendered every frame and a heading at 20px
  would otherwise re-parse Inter sixty times a second.
- **A weight is a face, not an axis**: Inter ships as a variable file *and* as its
  SemiBold static instance, because instancing `wght` needs symbols in both HarfBuzz and
  Blend2D and therefore three new export branches — the machinery that has caught the
  same local-symbol bug three times — while §1.4 specifies exactly two weights and
  Principle 3 forbids improvising a third
  ([ADR-0066](adr/0066-a-weight-is-a-face-and-color-inherits.md)).
- **`position` and `inset` reached the cascade** — §8 has listed them and `YogaNode` has
  bound them since the beginning, and nothing had needed a box that sits *over* its
  siblings rather than beside them.
- **`flex-basis` was implemented and taken back out**: `flex-basis: 0` gives equal cells
  and makes Yoga compute the track's content size as *zero*, so an unconstrained bar
  collapses to its padding — explicit percentages are the form that works in both
  directions, and a property with no consumer had no business staying. And **a segment's
  hover became a wash**: an opaque fill would paint over the pill, because segments are
  drawn after it, and clicking a new segment would paint the destination fill instantly
  and beat the animation to it — so the `--gb-overlay-*` tokens `button.ghost` uses do
  both states on both backgrounds, which took four tokens out of each theme. The cost is
  stated in §3's row rather than discovered: **a segmented control now has no width of
  its own** and fills its parent when nothing gives it one, because its cells are
  proportions ([ADR-0099](adr/0099-an-indicator-travels-on-a-grid.md)). Still to come:
  `select` and custom image cursors

### Binding — §9's other half

- **`bind` is done, which closes the second half of §9's wiring.** A `Property<T>`
  is a cell with listeners and nothing else — `get`, `set`, `subscribe` — and `set` does
  nothing when the value is unchanged, which is what makes two properties mirroring each
  other settle instead of recursing. `Bindings` is the third registry beside `Actions`
  and `Icons` and is deliberately the same shape: markup names a path, the registry
  resolves it, strict by default.
- **A path is `prefs.frost` and nothing else** — the §17 fork is settled at dotted
  paths, enforced by the registry, so `bind="!prefs.frost"` fails at inflation with the
  text quoted rather than producing a control that silently never updates
  ([ADR-0062](adr/0062-bind-is-a-path-and-nothing-else.md)). The binding lives on the
  widget and the subscription on its element, so a bound node has no wrapper element and
  `panel > text` styles it exactly like an unbound one; a change marks the element dirty
  by the same route `setState` does, so three changes in one frame cost one build. `text
  bind="user.name"` works from KDL and from Java, with the parity test extended to cover
  it, and the showcase's sidebar carries a line that follows a property nothing in the
  tree owns — set from a virtual thread, redrawn without anything reaching into the
  widgets.
- **Binding is one-way, which is a change to §9**: a widget is handed the read-only
  `Observable` half of a property, so markup can read a value and not write it, and what
  the user did travels back up as an action — `checkbox bind="prefs.frost"
  change="toggleFrost"`. A control is therefore controlled in the React sense: the tick
  moves when the application sets the property, not when the pointer lands. §9's
  "one/two-way" is amended to say one-way, deliberately and on the record
  ([ADR-0063](adr/0063-data-flows-down-events-flow-up.md)).

### Input, the pointer and the cursor

- **Pointer input routes.** A box carries an opaque owner tag, so a rectangle on screen
  leads back to its element; hit testing runs against the snapshot taken while painting
  rather than a fresh layout, because a pointer event is about what the user can see.
  Dispatch is capture → target → bubble with `consume()`, `:hover` moves along the whole
  ancestor chain and only where it differs, `:active` follows the press, and focus walks
  up to the nearest focusable ancestor with `:focus` and `:focus-visible` kept distinct
  ([ADR-0054](adr/0054-hit-testing-runs-against-the-painted-frame.md)). The sdl3 backend
  translates all of it — motion, buttons, wheel, keys and committed text — and
  `GoldberryRuntime` drives the router from a real window. **§7's remaining gaps are
  closed.** The wheel arrives in lines, fractional and positive down, with SDL's
  away-from-the-user sign and the "natural scrolling" inversion both undone at the
  boundary, so a widget never sees either
  ([ADR-0056](adr/0056-the-wheel-is-lines-and-the-sign-is-ours.md)).
- **A press captures the pointer** until the release, so a drag that leaves a widget
  still reaches it and `:active` cannot get stuck; an explicit capture outlives the
  release, for a gesture that does
  ([ADR-0058](adr/0058-a-press-captures-the-pointer.md)).
- **The cursor rides on the painted box**: `cursor: pointer` resolves through the
  cascade onto the rectangle, and hit testing reads it back off whatever the pointer is
  over — so inheritance is the stack of rectangles rather than the element tree, and it
  freezes during a drag ([ADR-0057](adr/0057-the-cursor-rides-on-the-painted-box.md)).
  And **accelerators are bound per window**, `router.shortcut("Ctrl+S", ...)`, fired
  after the focused chain declines the key so a text field keeps its own `Ctrl+A`;
  letters and digits joined `Key` for exactly this, since a modified letter produces no
  text event anywhere. Tab and Shift+Tab traverse in document order.

### Motion

- **The controls move.** §1.7's motion language ships: a frame `Clock`, the three
  duration tokens, the two easing keywords with a bezier solver that cannot overshoot,
  and CSS `transition` resolved by the cascade like any other property. Animated values
  live in a **per-node overlay applied at paint and never written back into computed
  style** — the sentence the whole design hangs off, because a cascade that saw the
  halfway colour as the node's real one would diff *that* against the target and start
  again from it, giving a control that approaches its hover colour and never arrives.
  Retargeting starts from the current animated value, so a pointer leaving a button
  halfway through a fade returns from where the colour is rather than jumping. The
  whitelist is a **closed enum** — `opacity`, `background-color`, `border-color`,
  `color` — and `transition: width 200ms` is a *dropped declaration with a warning
  naming it* rather than a rule that silently never fires, because animating a width
  would run Yoga every frame of every transition. Colours interpolate in **OKLCH**,
  which is measurable rather than decorative: Nord's danger red and success green have a
  channel spread of 54 at their sRGB midpoint and 109 at their OKLCH one. §1.7's "press
  applies in 0ms, release fades out" needed no new mechanism — the timing that applies
  is the one on the style being moved *to*, so a zero duration on `:active` and a fade
  on the resting rule is the whole of it.
- **The frame loop stays idle**: `renderer.isAnimating()` is what an application asks
  another frame on, so a window at rest costs nothing and nothing polls. And the virtual
  clock is what makes any of it testable — `button-hover-midway.png` is three buttons
  showing the start, the middle and the end of one transition in a single frame, which
  is a picture no wall clock can take
  ([ADR-0067](adr/0067-motion-is-an-overlay-on-a-frame-clock.md)).

### Density, and the metrics that turned out not to be fixed

- **`--gb-density` ships, at four controls rather than at thirteen** — the cost is
  per control, so it is three edits now and ten later. Every control sizes itself from
  `--gb-control-height`; `density-compact.css` is a three-token `:root` block in the
  theme layer, because that layer is defined by what it holds rather than by what it is
  called and a fifth would differ from the fourth in name alone. `Density.REGULAR` ships
  **no stylesheet**: a default is the absence of an override, and a
  `density-regular.css` restating 32 would be one number in two files. Padding, gap and
  radius stay literal and are asserted to, because §1.3's density row names heights and
  nothing else. Compact is **below §1.3's own 32×32 hit-target floor** and that is the
  trade rather than an oversight — bounded by the glyph staying 16px, so it costs margin
  around the target and not a smaller target
  ([ADR-0074](adr/0074-density-is-a-token-swap-and-regular-is-no-stylesheet.md)).
- **Every metric in §3 is now actually fixed.** Reported as "the knob is outside the
  pill when I resize the window", and it was not a toggle bug: Yoga runs with CSS's
  defaults, so **every node had `flex-shrink: 1`** and a `width: 36px` was a *preferred*
  width a cramped row could take back. §8 lists `flex-grow/shrink/basis` and only grow
  was implemented, so there was no way to say otherwise. Measured at 40px of room: a
  switch's pill 36 → **16** while its 16px thumb did not move, a checkbox's glyph 16 →
  **10**, a radio's the same and drawn as an *ellipse* since `border-radius` follows the
  box, and in a short column a control's hit target 32 → **13** — §1.3's 32×32 floor
  gone. The reported symptom was the only one of the four visible at a glance.
  `flex-shrink` is implemented now with **no native symbol and no new binding** —
  `YGNodeStyleSetFlexShrink` was already exported and bound, so the gap was in the CSS
  engine alone — and the controls declare `flex-shrink: 0` once over a type list,
  because the rule is "a control's metrics are fixed" and a copy per control is how that
  stops being true.
- **The label deliberately still shrinks**: text is the one thing in a control that
  should give, and a `text` that refused would push the glyph out of the window rather
  than ellipsing. Six golden scenes were **sized by the bug** — 300×132 for content
  needing 136, which fitted only because the options were being squashed. The test
  frames are deliberately absurd, because a regression here is a function of window size
  and a test at a plausible size is the one that cannot fail
  ([ADR-0076](adr/0076-a-glyph-does-not-negotiate.md)).

### The catalog, control by control

#### `button`

- **The catalog has started.** `button` ships in `:widgets` — a Java record, a KDL
  node and a CSS type, with a test asserting the first two produce equal values;
  variants are classes because that is the one spelling Java, KDL and CSS can all use;
  the metrics are the design system's in the toolkit-base layer and the colours are
  component tokens in each theme, because a hover lightens on Nord dark and darkens on
  Nord light ([ADR-0059](adr/0059-a-control-is-a-record-a-node-and-a-rule.md)). It
  activates on a **click** — a synthetic event the router raises only when a press and
  its release land on the same node, so dragging off to cancel works — and on
  `Space`/`Enter`, ignoring repeats. The `action` half of §9 is wired: markup names an
  action and an `Actions` registry resolves it, strict by default so a typo fails at
  inflation rather than producing a button that silently does nothing. `padding` grew
  CSS's 1–4 value shorthand and its four longhands on the way, because `padding: 0 12px`
  is the button's own metric.
- **`button` is finished, not started**: label, icon, or both — an icon is a `Box` now,
  which closes the question ADR-0043 left open, and it turned out to need no measure
  function because an icon is built at a size and that size *is* its intrinsic one.
  `disabled` refuses every route to the action, drops the button out of the Tab order
  and matches `:disabled`, which is the one pseudo-class a widget owns rather than the
  router. Markup names an icon against a registry for the same reason it names an
  action: an `Icon` owns native memory, and a document reloaded on every keystroke would
  leak one per reload.
- **Four golden images** cover the variants on both themes, the five states side by
  side, and the icon layout — the check that catches a padding on the wrong edge, which
  no value assertion can.
- **`button` complies with its own metrics row** (§3): radius 8, the design system's
  focus ring — 2px `--gb-focus` at a 2px offset, following the radius, written once for
  every control rather than per control — and `:disabled` as **45% opacity rather than a
  colour remap** (§2.1), so a disabled `danger` button still reads as dangerous where
  eight muted tokens had made every disabled button look alike. Removing the remap
  exposed that a disabled control still lightened under the pointer; CSS would spell the
  fix `:not(:disabled):hover` and `:not()` is not in §8's subset, so `PointerRouter`
  refuses to *set* `:hover` or `:active` on a disabled widget — one choke point, every
  control, forever.
- **`button` is now fully compliant with its §3 row**: `body-strong` was the last of the
  four things `controls.css` said it could not express. The theme tokens were also wrong
  and are now §1.4's exactly — `heading` was 16 where the table says 15, `body` was 14
  where it says 13, there were no line-height tokens at all, and `docs/ARCHITECTURE.md`
  §10.1 carried a *different* table with a `label` token at weight 500 that no shipped
  face can draw; §1.4 won and §10.1 records that it did.

#### `checkbox`

- **`checkbox` ships**: three states with `:indeterminate` as its own pseudo-class,
  because two cannot describe three and folding mixed into `:checked` makes every rule
  that meant "the tick is showing" silently wrong; a tick and a dash drawn by the
  painter rather than by an `Icon`, since a widget is a value and an `Icon` owns native
  memory; a click target that includes the label; `Space` and deliberately not `Enter`,
  which belongs to a dialog's default action. Its glyph is the first **part** —
  `check-indicator` is CSS-selectable and **not** KDL-constructible, a stated exception
  to the parity invariant rather than an oversight in it, because a part has no
  existence outside its parent and one `ComputedStyle` cannot carry two backgrounds
  ([ADR-0065](adr/0065-a-part-is-styleable-and-not-constructible.md)). The value is
  **controlled** in the sense ADR-0063 settled: a click on a bound checkbox whose
  handler does nothing moves neither the property nor the tick, and a test asserts
  exactly that.

#### `radio` / `radio-group` — the first composite

- **The first composite ships, which closes §7.2.** `radio` and `radio-group` are
  the third and fourth controls, and the first widget that is a *set* rather than a
  control — so three things that were trivially true for `button` and `checkbox` stop
  being true.
- **Traversal:** a group of six options is **one Tab stop** with the arrow keys roving
  inside it, which is what `docs/design-system.md` §7.2 asks for and what nothing could
  express, since `moveFocus` collected every focusable node in document order and a
  radio is one. `Handles.focusScope()` is the whole opt-in, and both halves are the
  **router's** by the argument already written on Tab: which node an arrow reaches is a
  property of the group's shape, and the radio the focus is on cannot see its siblings.
  Arrows are handled after the focused chain declines the key, so a slider stepping its
  value keeps its own. Both axes rove, because the group's direction is the stylesheet's
  and input cannot know which pair the user is looking at.
- **Where Tab re-enters is derived from `:checked`, not remembered** — the decision the
  record is worth writing for. The obvious implementation is a stored roving position,
  and it is wrong in a way that only shows later: it is a second piece of state beside
  the selection, and the two disagree the first time an application sets the value
  itself, returning the user to the option they last *looked at* rather than the one
  that is *on*. No event would fix it, because a property being set does not know a
  router exists. Derived, **the selection is the roving position**; there is nothing to
  invalidate, nothing to leak when an element unmounts, and one test — focus leaves, the
  model changes underneath, Tab comes back to the new selection — that the stored
  version fails.
- **The invariant:** "exactly one is on" is a fact about the set, so the group applies
  it on every build and `selected` is deliberately not a KDL attribute, since a document
  that could mark one option could mark two. A value no option carries selects nothing
  rather than guessing the first.
- **Selection follows focus through the application**, not inside the widget: an
  arrow raises the change and does not move the tick, so a group whose handler does
  nothing moves the ring and stays put — ADR-0063 applied to a composite. The
  `fromKeyboard` half of the new `onFocusChanged` is load-bearing rather than
  decoration: a mouse focus deliberately does not select, or a press moving focus and
  the click that follows would each fire the change. `Actions` gains a **valued**
  binding, the first action told which one — `Consumer<String>` over the `value` the
  document already wrote, with a plain `Runnable` still resolving against it and a
  valued action *refused* for a `press=` rather than called with an invented argument.
  `radio-indicator` is the **second part**, which is where ADR-0065 asked that its
  argument be made again rather than assumed; it holds, and the circle needed no new
  drawing code — `border-radius: 8px` on a 16px box is one, through the four cubics
  ADR-0064 already ships, so no native symbol was added and `Box.Mark.DOT` finally has a
  caller. Five golden images across both themes, and one of them is what caught that
  options were stretching to the group's full width: a column's flex children stretch on
  the cross axis, so the focus ring and the click target ran out across empty space
  while `.inline` kept hugging its label — the same widget with two hit targets
  depending on a class, which no value assertion would have shown
  ([ADR-0073](adr/0073-a-composite-is-one-tab-stop.md)).
- **`radio` is finished against both specification documents, not just built.**
  Reading §1.3, §1.5, §2.1, §2.2 and §3.1 against what had shipped turned up five
  divergences, four of which `checkbox` shared — §3 gives the two controls **one metrics
  row**, so a rule true of one and not the other is a spec that has stopped being true.
  Both now carry §1.5's small-control `border-radius: 4px`, which §2.2's ring follows
  rather than drawing a square one beside `button`'s 8px; both change a **surface** on
  hover rather than only a border (§2.1's "one surface step"); the group's gap is §1.3's
  8 for related controls rather than the 4 it shipped with, and `.inline` takes 16
  because side by side each glyph-plus-label is a unit and at 8 a label sits as close to
  the next option's glyph as to its own. The fourth was a **bug**: `:active` was set on
  the single deepest element a press landed on, so no control had a working pressed
  state at all — see below. The fifth was §3.1's check/dot **scale**, the last
  unimplemented row in that table, which needed the mark to stop being a mark. Two more
  golden images, one of them a frame 80 ms into a moving selection.

#### `toggle` — the first gesture

- **`toggle` ships, which is the first widget with a *gesture*.** Everything before
  it responded to a click, a key or a focus change — all single events. A drag is a
  sequence, and a widget is a value rebuilt every frame with nowhere to keep one, so the
  `Toggle` that sees the release is a different object from the one that saw the press.
- **The router reports the origin**, as `PointerEvent.dragX()`, by the argument already
  written on Tab and on arrow keys — the router owns what the widget cannot see — and
  because the interval a drag offset is defined over is exactly the implicit capture
  ADR-0058 already spans. It is **`NaN` and not zero** with no button held, because zero
  is a real answer (a press that did not move) and `Math.abs(NaN) >= 8` is `false`, so
  an event with no gesture reads as "not a drag" through the arithmetic rather than
  through a guard. The rule is one comparison against half of §3's travel: past 8px the
  value is the **direction dragged**, under it the value flips — so dragging right on a
  switch already on asks for **on**, which is what a naive "toggle on release" gets
  wrong. It is also the only control that acts on a release rather than a click, because
  a switch has no cancel gesture: dragging off it *is* the interaction. `toggle-track`
  and `toggle-thumb` are the fifth and sixth parts, the thumb by ADR-0073's argument
  that the unit of independent movement is a node — a `transform` applies down its
  subtree, so a thumb drawn onto the track would slide the track with it. Where it
  travels to is the stylesheet's, not Java's.
- **The colour question took two wrong answers before the right one, both of which
  looked like a geometry bug**: the thumb appeared to be breaking out of the pill, and
  measured off the image it never was — it is exactly concentric and 2px inside all the
  way round. What the eye read was the thumb merging with the window *across* those 2px.
  `nord0` was identical to `--gb-bg`; `nord3` was merely near it. Every dark value in
  Nord is near `--gb-bg`, so on a **light** accent pill there is no dark thumb that
  works, and the fix is not a thumb colour at all: the dark theme's on pill is
  **`nord10` rather than `--gb-accent`**, and the thumb is the same near-white in both
  states. That is the one place a control departs from the shared accent ramp, and the
  geometry earns it — a checkbox can use a light accent because nothing sits inside its
  fill, and a toggle cannot because something does.
- **All of it was caught by looking at a golden image and none of it by a test**, which
  is now three occasions; a colour that equals another colour is a passing assertion,
  and a disc that is provably inside its container can still look like it is not
  ([ADR-0075](adr/0075-a-gestures-origin-is-the-routers.md)).

#### `slider` and `fader`

- **`slider` ships, with `fader` as its vertical class** — the sixth control, and
  the first whose value is a **number rather than a state**. Every control before it has
  a value a stylesheet can name; `toggle-track:checked toggle-thumb { transform:
  translate(16px) }` is literally how a switch's thumb moves, and that stops working the
  moment the value is 37.4. So the thumb is placed by **flex ratio** — fill, thumb,
  rest, with the grow factors carrying the value — and `transform` is not merely awkward
  here but *unable*: CSS percentages inside `translate` are a proportion of the moving
  box, so `translate(50%)` moves the thumb by half a thumb rather than to the middle of
  the track. The ratio yields the **filled portion for free**, as a box the cascade can
  reach. The second half is `PointerEvent.local()`, the direct sibling of ADR-0075's
  `dragX()`: where an event landed **inside the widget currently handling it**,
  re-pointed per handler because dispatch bubbles — a press on the thumb targets the
  thumb while the slider wants the position along *itself*. The control **snaps and
  clamps so no application has to**, and each of the three rules is a choice: steps
  count from `min` (so a 1..10 slider stepping by 2 can reach 1), an arrow offers the
  next *reachable* value rather than the current plus a step (nothing snaps a value on
  the way in, because that would be the control overruling the model), and the **ends
  are always reachable** even when the range is not a whole number of steps. It is also
  the first control that relies on ADR-0073 putting scope traversal after the focused
  chain — and it consumes an arrow even when the value did not move, because a slider at
  its maximum still owns `Right` and letting it through would move focus off the control
  being adjusted ([ADR-0079](adr/0079-a-continuous-value-is-placed-by-ratio.md)).
- **`slider` is finished against §3 rather than merely shipped.** Its three optional
  halves — "optional tick marks and value label", and `fader`'s "optional dB scale
  mapping" — look like three small additions and are three different things breaking.
- **A value label makes "the control is the track" false**: `[ track ──── ] 40` is one
  control and two boxes, and the value lives along the shorter one, so a pointer mapped
  along the control reads 88% at the far end of the track — drawn perfectly, reported
  nowhere. `Handles.localPart()` is the answer, naming a **CSS type** because that is
  the vocabulary a part already has (ADR-0065) and resolved by the **router** because a
  widget cannot see its own elements — `dragX()`'s argument for the third time. The
  fallback is on the *rectangle* and not the element, which is the case that actually
  happens: a part exists from the first build and has no region until the first paint,
  so the element-level check finds it and hands back a zero-sized box whose every
  fraction is 0 — for a slider, "the user asked for the minimum".
- **The anatomy was renamed rather than extended**: `slider-track` is now the
  full-height box the value is measured along and the 4px channel is `slider-groove`,
  because two boxes were doing one job under one name until a third thing joined the
  control.
- **Every existing golden is byte-identical**, which is what says that was a refactor.
- **The marks needed two things that rule each other out** — clear the thumb, and do not
  move the groove (a scale that pushed it up would put two sliders in one settings list
  at different heights for no visible reason) — so `slider-ticks` is `height: 0` and
  each mark is moved clear by a `transform`, which costs no layout (ADR-0068). Each mark
  sits in a synthesized **0×0 cell** it overflows out of, because a mark's own 2px has
  no business being in the spacing arithmetic: spread five 2px marks directly and every
  centre is a pixel off the thumb centre it names. The cell is zero on **both** axes so
  a fader can flip the row to a column in the stylesheet alone. Marks are counted along
  the **travel**, not per `step` (twenty-one marks on a 0–100 slider stepping by 5 is a
  wall) and not at even values (on a decibel travel that is four marks huddled at the
  top).
- **`format` is a pattern and not a function**, because §11 compares two records for
  equality and two lambdas are never equal; it is validated at construction, so `%d`
  against a double fails at inflation rather than out of a paint, and formatted in
  `Locale.ROOT`, because the default would draw `0,5` on a `de_DE` machine and the
  golden that failed would be unreproducible anywhere else.
- **`Scale` is a sealed interface of records** — `Linear` and `Decibels(floorDb)` — for
  the same parity reason, and it places a linear gain at a position linear in dB: half
  gain is 6 dB down, which is 90% of the way up a fader and half way up a linear slider,
  and *that* is the feature. The bottom of the travel is silence exactly, a
  0.001-of-full-scale discontinuity at one end, because the thing a fader must be able
  to do is go silent. `SliderGeometryTest` is a new kind of test here and the change is
  what needed it: the claims rest on **geometric relations between two parts** that no
  stylesheet states and no value assertion reaches, and two of its six assertions failed
  on the first run — Yoga adds padding to a box with an explicit `height: 0`, and a
  slider in a *row* collapses to its content width, so the test's own scene was wrong
  ([ADR-0080](adr/0080-a-value-is-measured-along-a-part.md)).

#### `progress` and `spinner`

- **`progress` and `spinner` ship, which are the first two widgets whose motion is
  not a transition.** Everything that has moved so far moved between two styles the
  cascade resolved; §3.1 asks these two for a "sweep loop 1.2s linear" and a "rotation
  900ms linear loop", and §8's subset has no `@keyframes` and is not going to grow one.
  §1.7 names `AnimationController` for exactly this and **it was not built**: a loop
  that never ends is `(now % period) / period`, with nothing to start, stop, dispose or
  leak. That is ADR-0073's argument for the third time — a second copy of a fact the
  tree already holds disagrees with it — and here the stored version has a symptom the
  derived one cannot have: two spinners mounted a frame apart would turn at the same
  speed and never at the same angle, which looks wrong without looking broken. The
  controller's real subjects have a **lifecycle** — toast reflow, and the `opening →
  open → closing → removed` sequence every overlay runs — and none of those widgets
  exist, so it is M3's to build for M3's problem. Two small seams:
  `Paints.Context.nowMillis()`, read **once** per frame so two spinners see one number,
  and `Paints.isAnimating()`, because §1.7's idle loop would otherwise paint a spinner
  once and go to sleep in front of it — a property of the *description*, so a bar given
  a value stops asking. The sweep is a `transform` (animating a width would run Yoga
  every frame of a loop that never ends) and it **turns at the ends rather than running
  off them**, because the usual drawing needs `overflow: hidden` and nothing here clips
  a box: a bar that ran past its track would draw over its neighbours, and the wrap
  clipping exists to hide would be a visible jump once a loop. The spinner's ring is a
  `Box.Mark` and its arc is **three cubics through the already-exported
  `bl_path_cubic_to`** — no symbol added to the export list, ADR-0064's rule holding for
  the fifth time — and it is three quarters of a circle because a spinning circle is a
  circle ([ADR-0081](adr/0081-a-perpetual-loop-has-no-state.md)).

#### `badge`

- **`badge` ships, which is the first entry in §3's table that is not a control** —
  no focus, no value, no keyboard map, no states — and the first widget the design
  system *lets use colour*. §1.2 admits the aurora hues "only with semantic meaning", so
  every widget so far has obeyed the never half; a status chip is the first one whose
  whole job is the only half. Which walks it straight into the other half of §1.2:
  **every text/surface pair meets WCAG 4.5:1, validated in CI against both themes** — a
  sentence that had nothing behind it, because no contrast check existed anywhere in the
  repository. It does now, and it found something on the first run. A filled chip cannot
  take `--gb-text`: white on `--gb-warning` is **1.35:1**, on `--gb-success` 1.77 and on
  `--gb-info` 2.34, so three of the four hues need the *opposite* end of the palette
  from the one the dark theme is built on — `--nord0` text on a theme whose every other
  text token is `--nord6`.
- **The foreground is a property of the fill and not of the theme**, and because §1.2's
  palette is theme-invariant the pairing is identical in both files. `--gb-danger` needs
  something that is not in the palette at all: it is 3.55:1 under `--nord6` and 3.05
  under `--nord0`, legible against neither, so the badge's fill is `--nord11`
  **derived** darker until it clears the floor — the one place a chip's colour is not a
  palette entry, and the reason
  [ADR-0087](adr/0087-a-semantic-fill-brings-its-own-foreground.md) exists. §3's table
  gained a `badge` row before a single number reached `controls.css` (Principle 3), and
  every one of them is derived rather than picked: 20 is on §1.3's ramp and is the
  height `toggle-track` already uses, so `border-radius: 10px` is §1.5's `full` spelled
  the way that part already spells it. `ContrastTest` resolves every pair through the
  **real cascade** rather than parsing the CSS, so a rule that stops matching and a
  token that stops resolving both fail it.

#### `knob` — the first drag that is a rate

- **`knob` ships, which is the tenth control and the first whose drag is a *rate*.**
  It looked like a slider bent into a circle -- same `min`/`max`/`step`, same keyboard
  map, same `bind` and `change` -- and almost none of the machinery transferred.
- **A slider's value is a position**: the pointer is somewhere along a track, the
  fraction it sits at *is* the answer, read fresh on every event with no history at all,
  which is why nothing keeps state and why the router only ever had to report *where* a
  gesture started (ADR-0079).
- **A knob has no track.** §3 gives it "value drag 200px per full range", so the value
  is *where it started plus how far you have dragged* -- and "where it started" is
  exactly what nothing could answer, because a widget is an immutable value rebuilt from
  the model and by the second frame of the drag the value at the press has been
  overwritten by the value the drag itself asked for. So the router remembers a
  **third** thing about a gesture and it is not a point: `Handles.gestureAnchor()` is
  asked once on the press, deepest-first along the chain so a press on a *part* is
  anchored by the control that will handle it, and handed back on every event as
  `PointerEvent.anchor()`. `NaN` outside a gesture, which is `dragX()`'s convention and
  load-bearing -- a widget reading "no gesture" as an anchor of zero would snap a knob
  to its minimum on every hover. That is ADR-0075's argument one step further, and it is
  general: a splitter, a scrollbar thumb and a text-selection drag all want it, so
  `GestureAnchorTest` is written against a bare widget in `:core`.
- **The fine modifier is the gesture's, not the event's**, and the reason is a bug that
  would never have looked like one: reading the live modifier rescales travel already
  covered, so pressing Shift 100px into a drag takes the value from half a range below
  where it started to a twentieth of one *without the pointer moving* -- drawn
  perfectly, reported nowhere, and it reads as the knob slipping.
- **`SDL_GetModState` joins the export list**, the first new symbol since ADR-0086,
  because pointer events carried no modifiers anywhere -- not in `PointerEvent`, not in
  the SPI, not from SDL, whose mouse events have no `mod` field where its keyboard
  events do. Latching them from the last key event needs no symbol and is wrong in a way
  that lasts: a window that loses focus while Shift is held never sees the release and
  sits silently in fine mode.
- **`Box.Mark` gained `start` and `sweep`**, making `ARC` the one mark whose geometry is
  not fixed by its kind -- because it is the one that has to show a number -- and **no
  native symbol was added for the drawing**, because `Arc.addTo` was already general and
  already fed by ADR-0064's cubics; the rule holds for the sixth time.
- **Detents are magnetic and `step` is a grid**, which is why both exist: a knob with a
  centre detent is not a knob with a coarse step.
- **The first drawing was wrong in a way only the golden could say.** The dial was
  `knob`'s own `background` and both rings were stroked on the same box, so the track
  ran *across* the body at about 1.2:1 and the 270° of travel a user is meant to read
  was invisible -- every value assertion passed. `KnobDial` exists because of it, and
  the knob went into `controls-on-surface-*` rather than being exempted from it. A
  second one the goldens did not catch and a test did: a gentle touchpad scroll did
  **nothing** on a stepped knob, because a touchpad reports fractions of a line, a
  stepped knob snaps everything it reports, and every wheel event computes from the
  current value rather than accumulating -- so a third of a step rounded straight back,
  every time. A stepped knob now moves at least one step for any scroll at all
  ([ADR-0089](adr/0089-a-knobs-gesture-is-a-rate.md)).
- **Then it was put in front of someone and two things were wrong that no assertion
  could have said.** It read as a *gauge*: §3 asks for an "arc indicator" and between
  them the two documents say what the value is and never say which way the thing is
  **pointing**, so nothing on the dial turned and nothing about it suggested you could
  turn it. And the ring did nothing — a slider's track is clickable, a knob's ring is
  the same 270° of travel drawn round a circle, and it was inert. So `Box.Mark` gained a
  `POINTER` kind, a radial line at the value's angle, drawn as a **mark on** `knob-dial`
  rather than as a part of its own — the first time that has been the right answer since
  `CheckMark` went the other way, because a part is a node when two things must be
  styled or *moved* apart and the pointer is neither.
- **Clicking the ring positions the value; clicking the dial grabs it.** The boundary
  between them is not a constant: the control cannot know where the dial ends, because
  the inset is the stylesheet's, so `knob` names `knob-dial` as its `localPart()` and
  "outside the dial" is derived from the geometry that was actually painted (ADR-0080
  answering a question it was not written for). The jump fires on **`CLICKED` and not
  `PRESSED`**, which is the whole of what makes it compose with the drag: a press is the
  first event of both gestures and cannot know which one it is, the router synthesizes a
  click only when press and release landed on the same node, and the rest is `Toggle`'s
  8px slop. Jumping on the press would also have fought the anchor, which the router
  reads *before* dispatching — a drag after a jump would continue from the value the
  jump replaced ([ADR-0090](adr/0090-a-ring-is-a-track-and-a-dial-is-a-grab.md)).

#### `segmented` — the one the specification could not describe

- **`segmented` ships, which closes §3's catalog for everything that does not wait
  on a popup — and it is the first control whose *specification* could not be built as
  written.** It was billed as the cheap one: §3 says outright that it shares
  `radio-group`'s model and invariant exactly and "is `radio-group` with a different
  drawing", and the model transferred without a line of thought. The drawing did not.
  §3's row asks for "radius 8 outer, 0 between; 1px divider", which is the
  joined-buttons look — and a **per-corner** radius, where ARCHITECTURE §8 resolves "one
  radius, not per-side" on purpose. There is no clipping either, so the usual escape of
  square fills inside a rounded clipping parent is not there: a square-cornered fill
  inside the bar paints *over* its curve, and the selected end of the control reads as a
  corner that lost its radius. So **the bar carries the 8 and the segment is inset
  inside it at §1.5's 4**, with both numbers derived rather than picked — the 2px inset
  is what fits a 28-high segment in a 32-high bar, the same arithmetic `toggle`'s
  padding comes from — and the divider goes with the joined drawing it belonged to,
  because segments inset on every side are already separated and a rule drawing one
  would draw it through the gap the inset made. **§3.1's row could not be built either,
  and for a better reason.** It asks for a "selection indicator `translate`+width
  between segments": `width` is not on §1.7's whitelist and never will be, and the
  `translate` would have to name the distance from the segment being left to the one
  being arrived at — a fact about two boxes' laid-out geometry. A stylesheet cannot
  write it, because segments are as wide as their labels; and a widget cannot compute
  it, because ADR-0080 already established where geometry *is* available, which is the
  router after a paint. So **the fill is the indicator**, on `fast`, which is what
  `list` selection already does — and the travelling version waits for `tabs`, whose row
  §3.1 says it borrows the effect from, and which will need a widget to be told where
  its own children landed last frame. That is a real feature with real costs and it
  belongs to the control that actually requires it (ADR-0081's argument, for the second
  time). Both `design-system.md` rows were **amended** rather than left describing
  something that does not exist. What is new in Java is one line: `focusScope()` is
  `HORIZONTAL` where `radio-group`'s is `BOTH`, and that single difference is the whole
  of why these are two widgets rather than `radio-group.segmented` — a group has no axis
  because its direction is its stylesheet's, a bar has one because it is a row and no
  class turns it into a column, so `Up`/`Down` are not its keys to take. ADR-0078 wrote
  that rule for menus and this is the first control outside one to use it. A segment is
  `option` — the node §3 writes for this control *and* for `select` — and it is a widget
  rather than a part: a document writes it, it takes the focus, and it means something
  on its own. Two smaller things fell out. `option:checked:hover` is the first **two
  pseudo-classes on one compound** anywhere in the repository; the selector engine
  always supported it and nothing had needed it, and here it is what keeps a selected
  segment selected-coloured under the pointer — `checkbox` and `toggle` spend a
  descendant selector on the identical problem because their fill is on a part. And
  `flex-grow: 1` on a segment is the same question `radio-group` answered with
  `align-items: flex-start`, answered the other way: a group's options are separate
  controls that happen to be listed together, while **a bar is one object and its
  segments divide it**
  ([ADR-0097](adr/0097-a-selection-that-travels-needs-a-geometry.md)).
- **Then the indicator was made to travel, which ADR-0097 had deferred and was wrong
  to.** That record argued a `translate` "would have to name the distance from the
  segment being left to the one being arrived at — a fact about two boxes' laid-out
  geometry", and every clause of it is true except the premise buried in the middle:
  *segments are as wide as their labels*. That was a **choice**, not a fact. Make every
  segment exactly 1/n of the bar and the distance to segment *k* is *k* times one
  segment — a proportion, not a length, and a percentage in a `transform` is resolved by
  the painter after Yoga has run (ADR-0068). Nothing has to measure anything. What *was*
  missing turned out to be somewhere else entirely: a value a widget computes in
  `render` arrives **after** the frame has observed the node's style and started its
  transitions, which is why every Java-computed geometry in the toolkit — a knob's arc,
  a slider's fill ratio — is documented as not animating. So `Styled.restyle` exists:
  **§8's `inline` cascade layer, typed**, applied after the cascade, after the style
  cache, and *before* the animation looks. Those three orderings are the whole
  mechanism, and each is a way it could have been wrong — frozen, snapping, or invisible
  to the subtree that inherits it. The anatomy underneath is `segmented →
  segmented-track → [segmented-indicator, option…]`, and **the track exists because two
  percentage bases disagree**: Yoga resolves an in-flow child's percentage width against
  its parent's *content* box and an absolute child's against its *padding* box, so a
  pill sized against a bar with 2px of padding is 4px too wide and drifts a little
  further with every cell. A track with no padding makes them one box, which is `slider`
  growing a track for the same kind of reason (ADR-0080). Three things fell out of it.

### Structure, ergonomics and the showcase

- **JPMS encapsulates resources, and the first headless run found out.** `exports`
  governs types; a file inside a package of a named module is invisible to other modules
  unless the package is `opens`. So the toolkit could not read the showcase's own
  `showcase.css`, and the error blamed the file. The message now checks whether the
  owning package is open and names the missing `opens` line when it is not. The showcase
  opens its package **to `:core` only** — an unqualified open would hand its private
  types to everything on the module path as well — and this is a line every application
  will have to write, which is a papercut in "implement one interface and go" that
  nothing can remove. — [ADR-0093](adr/0093-an-application-is-a-root-widget.md)
- **The showcase is a widget tree**: bar, sidebar, wrapped prose and a row of
  buttons, with `setState`, theme switching, `Ctrl+T`, focus that survives a rebuild,
  and `:hover` that repaints itself.
- **An application is a root widget, and the showcase's `main` is one line.** It was 190
  lines, and none of them were about the showcase: open a window, open a font book,
  build an element tree, a render tree and a router, hold three one-element arrays to
  remember the renderer and the theme and the density across frames, write the paint
  callback — flush, restyle if the theme moved, update, compute damage, choose partial
  or full, hand the damage back, capture the hit-test snapshot, ask for another frame if
  anything animates — and take it all down in an order that matters. Two of those lines
  are subtly wrong if reordered: a render object holds a Yoga measure callback closing
  over a paragraph closing over a font, so closing the fonts first reads unmapped
  memory, and the trailing `Goldberry.shutdown()` is the difference between a clean
  Wayland disconnect and a compositor unwinding a client that never said goodbye
  (ADR-0085). None of it is a decision an application makes differently, so all of it is
  `Goldberry.launch`'s now: an application implements `Application` — one required
  method, `root()` — and gets back a `Host` with `repaint`, `restyle`, `title`,
  `shortcut`, `fonts` and a *named* escape hatch to the window. **`restyle()` is
  separate from `repaint()`** and is the one piece of state the launcher keeps for the
  application: re-reading `stylesheets()` every frame would rebuild the renderer every
  frame, and never re-reading it would make a theme switch impossible, so the
  application says when. Alongside it, **every widget is chainable** — `Attributed`
  gives `id`, `styled` and `keyed`, `Bindable` gives `bound`, both self-typed so `new
  Badge("3").styled("danger")` is still a `Badge` — and a widget supplies the one line
  only it can, `withAttributes`. Containers take children as varargs, so `List.of` is
  gone from the showcase entirely. And an application's **CSS and markup are resources
  now**: `Stylesheet.resource` and `KdlParser.resource` read files beside a class the
  way the toolkit reads its own, which is also how the badge row became the first thing
  in a *window* to come from KDL rather than from Java (§9 had test coverage and no
  window coverage) — [ADR-0093](adr/0093-an-application-is-a-root-widget.md)
- **The showcase is five classes and two documents, and `new` survived a challenge.** It
  was one 770-line class doing four unrelated jobs — the application lifecycle, the view
  model, the widget tree and three panes' layout — with every screen a private method on
  one state object that also held the model. It is now `Showcase` (the `Application`:
  lifecycle, stylesheets, registries, accelerators), `ShowcaseModel` (properties, the
  methods that change them, and the two registries markup resolves against), and
  `ui.Screen` / `ui.Panes` / `ui.Content`. **`titlebar.kdl` and `sidebar.kdl` carry
  everything declarative**, which is the first time §9's markup path has run in a
  *window* with all three registries live: `bind=`, `change=`, `press=` and `icon=` all
  resolve against what the model and the application register, and all three are strict,
  so a typo fails at inflation with a line and column. `ui.Content` stays in Java and
  the reason is the instructive one — its Undo and Reset buttons are disabled when the
  click count is zero, and §8's markup has no expressions; a document that could
  evaluate `clicks == 0` would be code in a data file with no stack trace.
  **`ShowcaseDocumentsTest` asserts the shape rather than trusting the window**: an
  empty `sidebar.kdl` inflates to an empty column and paints a blank panel, and the
  headless three-frame run would pass — so it checks that every control is there *and*
  that the bindings reach the model's own properties, which a shape assertion misses (a
  `bind=` resolving to nothing still renders a control that never moves). **On
  `Column.of()` against `new Column()`**: `new` stays, and the deciding argument is that
  a public record's canonical constructor **cannot be hidden** — the JLS requires it to
  be at least as accessible as the record — so `of()` could only ever be additive, two
  permanent public doors with no compiler help keeping them in step. The noise turned
  out to be depth rather than the keyword, and decomposition fixed it. Performance was
  **measured** rather than assumed: 20M allocations, `new` 45.2 ms against `of` 45.1 ms,
  identical within noise, because `-XX:+PrintInlining` shows the factory inlined
  (`Box::of (10 bytes) inline (hot)`) — the first attempt at that benchmark said 87
  against 46 and was wrong, with a `String.equals` inside the loop. What *did* get named
  is the ambiguous **overload**: `Slider` had two five-argument constructors differing
  only in whether the fourth parameter was a `double` or an `Observable`, and `Knob`,
  `Toggle` and `Progress` had the same shape — now `Slider.of`, `Knob.of`, `Toggle.of`,
  `Progress.of`, following the `of` = bound convention the catalog already used —
  [ADR-0094](adr/0094-name-the-overload-not-the-allocation.md)
- **Registries are generated, not reflected.** *(Superseded — see "A model is plain
  Java again" below.)* Wiring a model to markup was fifteen
  lines of pure copying — one `.bind(path, property)` per property, one per handler,
  plus the `Double.parseDouble` a valued action needs — and the failure mode was the
  worst kind: a property that exists and is never registered inflates to a control that
  renders perfectly and never moves, with nothing pointing at it. The obvious fix is a
  runtime reflective scan, and §9 forbids exactly that ("no reflective `#handler`
  magic") — rightly, since it would need the application's package `opens`, cost
  start-up, and leave the same silent control. So `@Bind`, `@Action` and `@Registry` are
  read by an **annotation processor** that writes the calls a person would have written:
  ordinary Java you can open, step into and get a stack trace out of, with nothing on
  the runtime path at all. **The refusals are the point** — a `private` member the
  generated code cannot see (with the fix in the message), a `@Bind` on something that
  is not a `Property`, two members claiming one path, an `@Action` taking more than one
  argument or one the toolkit cannot parse, and an annotated member on a class that is
  not `@Registry`, which is the mistake with no other symptom at all. Eight processor
  tests cover those; the showcase proves the generation itself every build. Annotations
  are `SOURCE`-retained so nothing at run time can be tempted to read them —
  [ADR-0096](adr/0096-a-registry-is-generated-not-reflected.md)
- **A shortcut is built from enums, and `Modifiers` is a mask.** An accelerator had one
  way in — `Shortcut.of("Ctrl+S")` — parsed at run time, so `"Crtl+S"` threw whenever
  the line happened to run. `Modifiers` had the same problem from the other side: four
  positional booleans, 23 call sites writing them out, and nothing to catch a wrong
  order. There is a `Mod` enum now with a real bitmask, composed as
  `Mod.CTRL.and(Mod.SHIFT).and(Key.Z)`. **`Mod.CTRL | Key.A` is not reachable**: `|` is
  defined for the integral types and `boolean` and Java does not allow overloading it,
  and the spelling that *would* compile — `Mod.CTRL.bit() | Mod.SHIFT.bit()` into a
  method taking an `int` — is a mask with nothing checking it, where `Key.A.ordinal() |
  Mod.CTRL.bit()` would compile and mean nothing. So the mask is real and private to the
  arithmetic: `bit()` is for the SDL boundary and for tests, and `and` can only ever
  produce `Modifiers` or a `Shortcut`. `Modifiers` is one `int` with `has`/`only`/`set`
  on top, the four boolean accessors kept so no call site changed, and the four-boolean
  constructor demoted to a secondary one — it reads fine where all four are literals and
  is a trap where they are computed —
  [ADR-0095](adr/0095-a-shortcut-is-built-from-enums.md)
- **`:core` ships no widgets, and its own tests stopped needing any.** `text`, `row`,
  `column`, `panel` and `spacer` were nested records inside a `Widgets` class in
  `:core`, for a reason that had expired: the widget tree, the cascade and the painter
  all had to be provable before there was a catalog to prove them with, and five
  primitives were the smallest set that made the parity invariant testable. Once
  `:widgets` reached thirty types with a package per control, they were the only widgets
  in a module that is not a widget toolkit — and `core-widgets.md` had specified their
  packages since v0.1 while the code had them in a different module inside one holder
  class. They are ordinary top-level records now, in the packages the document gives
  them. `Attributes` **stayed**, promoted to a top-level type: it is not a widget but
  part of the widget *contract*, and an application widget wanting an id should not have
  to depend on the catalog to hold three fields. The interesting half was the tests. Two
  of the five that moved could not: `StyleCacheTest` and `BindingTest` reach into
  `Element`'s package-private internals, which is right for a test of the element tree
  and impossible from another module — so they stayed and use **local test widgets**,
  the pattern `DragOriginTest` already established, and nothing in `StyleCacheTest` any
  longer looks like a fact about `panel`. `BindingTest` split along a seam that turned
  out to be real: reading `bind=` off markup is the catalog's, and what an element does
  with a binding once it holds one is `:core`'s. The same 1,641 tests run; 25 of them
  changed module — [ADR-0092](adr/0092-a-primitive-is-a-widget-like-any-other.md)
- **Every widget is provably a value, and now there is a test that says so.** ADR-0004
  rests on it and nothing checked it. `ImmutabilityTest` asserts the parts records do
  *not* give for free: that every widget is a record with no non-final field, that a
  container copies the children list it is handed rather than keeping the caller's, that
  the list it hands back cannot be written to, that `Attributes` copies its class set,
  and that every chainable step returns a new widget rather than mutating the receiver —
  so handing one widget to two panes and styling one cannot restyle the other. Ten
  checks, all passing, which means the guarantee was already true and is now enforced.
  The one component that is mutable by design is called out rather than papered over: a
  binding is an `Observable` and a handler is a lambda, and what matters is that a
  widget cannot *write* through them (ADR-0063).
- **Two things landed that M2's ladder does not name.** *(The first is superseded —
  see "A model is plain Java again" below, where the problem stops existing rather
  than getting a better answer.)* The first is that **an
  annotated member may be private again.** ADR-0096 listed "annotated members cannot be
  `private`" as a cost and argued the fields belonged package-private anyway; the
  argument runs the wrong way round, because the toolkit was deciding a model's
  encapsulation as a side effect of how it reads it, and an `@Action` only the markup
  calls has no business being part of a model's API. A private member now gets a
  `VarHandle` or a `MethodHandle`, looked up **once** in the generated class's static
  initializer through `privateLookupIn` — which needs no `opens` and no `setAccessible`,
  because the generated class is in the target's own package and a module always opens
  its packages to itself. This is **not** the `MethodHandles.Lookup` alternative
  ADR-0096 rejected: that one resolved a name at run time, and this writes the
  descriptor the processor already verified, so a typo is still a compile error naming
  the field and the handle is *access* rather than discovery. An accessible member still
  gets nothing — a handle it does not need is a line of generated code a reader has to
  understand for nothing — so a mixed model gets a mixed file, which is honest. The
  processor's test suite now **runs** its output rather than only compiling it, because
  "it compiles" stopped being the interesting half of the claim, and `ShowcaseModel`'s
  six properties and five markup-only handlers are private
  ([ADR-0098](adr/0098-a-private-member-is-reached-by-a-handle.md)).

- **A model is plain Java again.** The binding schema had drifted into the shape it
  was meant to avoid: `clicks.set(clicks.get() + 1)` is `clicks++` with three extra
  tokens and a heap object, and because the *field* was a `Property`, every read and
  write inside the model went through an accessor nobody chose to write. The showcase's
  model carried eight of them. A model is now plain fields and plain methods —
  `@Bind("app.clicks") private int clicks;` and `clicks++` — and the **build** rewrites
  that one `putfield` into a store that notifies, using the JDK 25 class-file API
  (JEP 484) on the model's own compiled class. It has to be the declaring class:
  `putfield` is not virtual, so no subclass or proxy can see the write. The `@Action`
  half moved with it — one `invokedynamic` per action, bootstrapped by
  `LambdaMetafactory`, written into the model's own class, which is byte for byte the
  call site `javac` emits for `model::click`. That deletes both the `:processor` module
  and the generated `…Registry` source file, and it deletes ADR-0098's `privateLookupIn`
  along with the problem it solved: a call site inside the model reaches the model's own
  private methods with no handle at all. **Measured**, medians per operation: a write
  nobody is watching 9.5 ns → 2.5 ns, a watched write 19.0 ns → 12.9 ns, constructing the
  model 23.8 ns → 2.8 ns; action dispatch unchanged, because it was a
  `LambdaMetafactory` call site before and is one now, and registry construction
  unchanged. The cost is honest and in the other direction: reading *through* a binding
  is 1.9× slower for a primitive, because a woven read boxes where a `Property<Integer>`
  already held a box. That is the right trade — a model writes on every event and the
  tree reads once per rebuild. **The refusals moved with the rules**: a `static` or
  `final` `@Bind` field, an array (only assignment is observed, so `values[0] = x` would
  notify nobody), a malformed path, two members claiming one name, an `@Action` taking
  two arguments or one the toolkit cannot parse, an abstract or empty `@Model`, a `@Model` extending a `@Model` — 41
  weaver tests, each of them a rule that would otherwise have quietly stopped applying.
  The known gap is stated rather than hidden: a field assigned from a *different* class,
  such as a nested class of the model, is not observed. Lambdas are fine, and there is a
  test for that, because javac compiles them into the same class
  ([ADR-0125](adr/0125-a-raw-field-is-woven-into-a-binding.md),
  [ADR-0126](adr/0126-actions-are-bound-by-lambdametafactory.md))
- **The binding schema fits a closed world, and a test says so.** The brief asked for the
  class-file API, `LambdaMetafactory`, and a GraalVM native image — three requirements
  that contradict each other if the first two run at runtime, because a closed world has
  no class loading and no class generation. They do not contradict at build time, which
  is where the weaving happens, and `LambdaMetafactory` is used as an `invokedynamic`
  bootstrap rather than as a method call — the one form the image builder resolves when
  it builds the image. `NativeImageComplianceTest` parses the woven bytecode and asserts
  it: no `Class.forName`, `setAccessible`, `privateLookupIn`, `findVarHandle`,
  `defineHiddenClass` or `Method.invoke`; every bootstrap is `LambdaMetafactory`
  .`metafactory`; one call site per action. **No image has been built** — there is no
  GraalVM in this toolchain or in CI — so what is verified is the structural property and
  not an image that starts. The claim is that the binding layer is no longer the reason
  an image cannot be attempted, and not that the toolkit produces one; `:natives` and its
  FFM downcalls into SDL3, Blend2D and HarfBuzz are a separate and much larger question
  ([ADR-0127](adr/0127-the-binding-schema-fits-a-closed-world.md))

- **An action is an assignment, and a value is named once.** Two things the first
  cut of ADR-0125 left behind, both of which were the model still doing work on the
  toolkit's behalf. The first: every action ended in a `changed()` that asked the
  window to repaint — a line with no meaning of its own, never wrong and only ever
  *missing*, whose symptom when missing is a value that moved and a window that did
  not. A `@Bind` field changing **is** the frame request now, subscribed to with
  `Models.onChange(model, host::repaint)`, and fired once per *change* rather than
  once per write — so a button that sets a counter already at zero asks for no
  frame, where the old code asked every time. The showcase's second callback went
  with it: `onRestyle` became two subscriptions to the two paths a stylesheet
  depends on, which is what made `density` worth binding even though nothing
  displays it. The second: nine `public Observable<String> tab()` accessors, which
  existed because a widget built in Java had no way into the registry a document
  already used — so every bound value was named twice and the two could disagree.
  `Models.observable(model, "app.tab")` is the same lookup `bind="app.tab"` does,
  and the weaver now caches the `Bindings` it builds so a path lookup while
  building a widget costs a map get. `Actions` is deliberately *not* cached,
  because applications extend it — the showcase adds the window's own two — and a
  shared one would fail the second caller for doing what the first did.
  `ShowcaseModel` went from 320 lines to 246 and contains no plumbing at all
  ([ADR-0128](adr/0128-a-change-is-its-own-frame-request.md),
  [ADR-0129](adr/0129-a-value-is-named-one-way.md))
- **A widget inflates itself, and the catalog is a list of names.**
  `Controls.inflater` was 300 lines of `inflater.register("button", (node, children)
  -> new Button(…))`, nineteen times, none of it near the widget it built — so a
  widget's markup contract lived in a different file from the record and the
  javadoc describing its attributes, which is two of §9's three required forms in
  one place and the third somewhere else. It was also mostly repetition:
  `node.argument().map(v -> v.asString()).orElse("")` eight times, the
  `String.valueOf` change adapter three. Each widget now has a
  `static Widget inflate(KdlNode, List<Widget>, Wiring)` beside its record, `Wiring`
  carries the three registries and the readings that were repeated, and
  `Inflatable.Catalog` binds one wiring so the table is `catalog.add("button",
  Button::inflate)`. A class rather than a `Map`, because the registration order is
  the order an unknown node is reported against. `Primitives` uses the same catalog,
  which makes §9's "built-ins and application widgets register identically"
  literally true rather than nearly. **Controls went from 443 lines to 204**, and
  adding a widget is a method and one line instead of a fifteen-line lambda in a
  file about something else
  ([ADR-0130](adr/0130-a-widget-inflates-itself.md))
- **The weaver is a jar with a `main`, and every build can call it.** It has no
  dependencies beyond the JDK, so `java -jar goldberry-weaver.jar target/classes` is
  a complete integration — verified end to end against a class compiled outside
  this build. Gradle gets the `goldberry.weave` plugin, which hangs the weave off
  `classes` and `testClasses` so `jar`, `run` and every `Test` task reach through it
  and unwoven output cannot be consumed. **Maven has no first-class plugin**:
  `exec-maven-plugin` bound to `process-classes` runs it as it stands, which is the
  phase that exists for class post-processing, and [the weaving
  page](weaving.md) carries the XML. A real Mojo would be one `<plugin>` block
  instead of two `<execution>`s and is small work whose awkward part is that this
  repository builds with Gradle and would have to write `META-INF/maven/plugin.xml`
  itself — not built, and said plainly rather than implied

- **A widget package announces itself, and a model wires itself.** The catalog
  ADR-0130 left in `Controls` was still nineteen hand-written lines naming exactly
  the widgets `:widgets` happens to ship — which does not survive a second widget
  module, where an application would merge two registries and keep the merge in
  step with both. A widget now carries `@Markup("button")` beside its record, and
  the build collects every annotated class in the module into a `WidgetCatalog`,
  **patches `provides` into the module's own `module-info.class`**, and writes a
  `META-INF/services` entry for the class-path case. `ServiceLoader` finds them,
  which is the one discovery mechanism GraalVM already resolves at *image build*
  time — a scan would have been the runtime scan ADR-0127 spent the redesign
  avoiding. The wiring went the same way: `Widgets.inflater(icons, model, this)`
  reads the paths and action names off the models rather than being handed
  registries, and takes more than one because "open the menu" is the *window's*
  action and not the view model's — `Showcase` is itself a `@Model` now, and the
  `Showcase.actions(model, openMenu, toggleHud)` static that used to merge them by
  hand is gone. `Controls` is 136 lines and has no `inflater` at all;
  `Primitives.inflater` is gone entirely, because the structural widgets carry
  `@Markup` like everything else — which makes §9's "built-ins and application
  widgets register identically" literally true rather than nearly. **The migration
  found a real footgun**: `Widgets.inflater(actions, icons, bindings)` bound to the
  varargs model-taking overload, compiled, and failed at run time reading `Actions`
  as a model. Eight tests caught it and an exact overload now exists
  ([ADR-0131](adr/0131-a-widget-package-announces-itself.md),
  [ADR-0132](adr/0132-a-model-wires-itself.md))
- **A restyle is declared, and the window repaints itself.** ADR-0128 moved the
  frame request out of every action; what it left behind was two subscriptions
  saying what one word could — and with exactly the property it was written to
  remove, in that they are never wrong and only ever *missing*, and when missing
  the symptom is a theme that changes and a window that keeps painting the old
  one. `@Bind(value = "app.theme", restyle = true)` is the whole declaration now:
  the weaver emits the call in that field's setter, **before** the frame request,
  so a window has dropped its resolved styles by the time it is asked for the frame
  that will use them. And the subscription itself moved into the toolkit —
  `Application.models()` names the objects, and the launcher wires repaint and
  restyle after `start`, so an application says nothing about either.
  `@Model(repaint = false)` turns the frame request off for a model the UI does not
  show. A `Property` field cannot ask for a restyle and the build refuses one: the
  weaver rewires no writes to it, so there is nowhere to put the call — the only
  asymmetry between the two kinds of `@Bind` field, and the error says what to do
  instead ([ADR-0133](adr/0133-a-restyle-is-declared.md))

- **A frame is asked for by the value that moved, and a write is rewritten wherever
  it is.** Two refinements that turned out to be the same shape of mistake. The
  first: `@Model(repaint = false)` was the wrong granularity, and obviously so once
  a real model was written — one model routinely holds both the gain a slider shows
  and the counter nothing shows, so a switch on the *class* has to be wrong about
  one of them. It is `@Bind(value = "…", repaint = false)` now, per value, decided
  in the build — a quiet field costs an instruction that is not there rather than a
  branch that is — and "off" means *do not wake the window*, not *do not observe*,
  which has its own test because the two are easy to conflate and the conflation
  would be silent. The second: ADR-0125 shipped a known gap where a write to a
  `@Bind` field from **outside** its declaring class was not rewritten, and the
  failure was silent. The weaver already made two passes, so pass one now records
  every model's rewired fields and pass two rewrites writes to them in any class.
  The synthesised setter went package-private to allow it, and a write from another
  package is a build error naming both classes rather than an `IllegalAccessError`
  at the first click. **The bug that found the implementation was mine**: composing
  two `transformingMethodBodies` with complementary predicates silently drops every
  rewrite, because the second pass no longer sees the elements the first handed on
  — every notification test failed at once, which was the good outcome
  ([ADR-0134](adr/0134-a-write-is-rewritten-wherever-it-is.md),
  [ADR-0135](adr/0135-a-frame-is-asked-for-by-the-value-that-moved.md))
- **An application is values, actions, views — and there is now a page saying so.**
  Nine records had changed how an application is written, each for a local reason,
  and none of them said what the *result* was; the showcase demonstrated the shape
  and did not explain it, and until now did not follow it either. `ShowcaseModel`
  is 125 lines of fields and four projections; `ShowcaseActions` is a **record**
  wrapping it with one method per thing a control can ask for. Each is the only
  shape that works rather than a preference: a record's components are final and a
  bound field has to be assignable, so the values cannot be a record — and the
  actions hold one thing immutably and have no state, which is the half a record
  fits exactly. The showcase now has three models — values, actions, and the window
  itself — which is a useful proof that multi-model wiring is not a special case.
  The split costs `private` on the values' fields, and that is stated rather than
  glossed: it is available, not required, and a three-field model should not
  bother. [The guide](applications.md) is the deliverable — the four kinds of class,
  what each may know, widget state versus application state, and a "where does it
  go?" table — and it is deliberately **not** enforced by a test, because
  mechanically enforcing a recommendation turns it into a rule nobody agreed to
  ([ADR-0136](adr/0136-an-application-is-values-actions-views.md))

- **A model keeps its fields, an application is not a model, and the showcase runs
  again.** Three corrections to the previous entry, one of them a real bug. The
  bug: `Showcase.start` built its inflater from a hand-written list of models while
  `models()` returned a different one, so `app.toggle-theme` was never registered
  and the window threw on its first frame — while every test passed, because the
  test had its *own* third list that happened to be right. The fix is that there is
  now one list: `start` builds the inflater from `models()`, and both tests take
  the application's own objects rather than constructing parallel ones. **The
  comment above that test already warned about exactly this failure and the test
  did it anyway**, which is worth recording. The first correction: fields did *not*
  have to open up to the package. Nestmates share private access in both
  directions, so an `Actions` record nested inside the values reads a `private`
  field with an ordinary `getfield` — and the weaver now derives each setter's
  visibility from whether anything outside the model's nest writes to it, so a
  nested-actions model is exactly as encapsulated as one with no actions at all.
  `ShowcaseModel`'s fields and its synthesised setters are both `private` again.
  The second: `@Model` sat on top of `implements Application`, which put two
  unrelated roles on one class and was the only place the guide's four kinds did
  not hold. The window's two actions are a `WindowActions` record of `Runnable`s
  now, so it knows what they are called and nothing about who performs them
  ([ADR-0137](adr/0137-a-model-keeps-its-fields.md),
  [ADR-0138](adr/0138-a-window-s-actions-are-a-model-of-their-own.md))

- **Actions are annotated as actions.** `@Model` marked two different things: a
  class of `@Bind` values, and a class of `@Action` methods that operates on
  somebody else's values and holds nothing at all. ADR-0138 had just made that
  mislabelling more visible by extracting a `WindowActions` record whose entire
  content is actions and whose annotation said "model". There is an `@Actions`
  marker now, with three build-time rules — a `@Bind` field on one is refused ("a
  class that holds values is a @Model"), an `@Actions` with no `@Action` is
  refused, and carrying both markers is refused. A `@Model` **may** still carry
  actions, because that is the right shape for a model too small to be worth
  splitting and taking it away would make the second annotation a tax rather than
  a clarification. The name was taken, so `Bindings` and `Actions` — the two
  runtime registries — became `BindingRegistry` and `ActionRegistry`: a rename made
  to free a name, which is a bad reason, and an improvement for a better one, since
  one package held `Bind`, `Bindings`, `Action` and `Actions` where two were
  annotations on members and two were registries. Each family reads distinctly now.
  **Two mistakes worth recording**: the rename's first pass ran over markdown as
  well as Java and produced "GitHub ActionRegistry matrix" in a dozen ADRs — a
  decision log records what the names *were*, and was reverted; and the same pass
  renamed a nested record's own declaration, which the new exclusivity check then
  caught on the next build. The remaining wart is documented rather than hidden: a
  nested type called `Actions` shadows the annotation, so the showcase writes the
  fully-qualified name, and the guide recommends naming the type for its domain
  instead ([ADR-0139](adr/0139-actions-are-annotated-as-actions.md))

- **`select` is built, and it is the last control in §3.** The value model needed
  nothing new — it is `segmented`'s, which is `radio-group`'s, which §3 says
  outright — and everything else it needed had arrived in the last month:
  `scroll` for a list longer than the screen, `host.popup` for a panel measured
  and placed against a rectangle, and a way for a widget to *ask* for one. That
  last was the real blocker and the reason this control waited: opening a menu is
  something an application does, so `Menus.open(host, …)` is right (ADR-0106);
  opening a dropdown is something the control does, and there is no application
  code on a `select bind="app.theme"` line to hold a window with.
  `BuildContext.host()` is the door — Flutter's `Overlay.of(context)`, which
  ADR-0100 named as the wanted shape and declined to build against one consumer.
  There are two now, so it exists, and it is an `Optional` because a golden image
  and a widget test build the same widget with no window at all: **no window, no
  popup, and the control draws its closed form** rather than throwing
  ([ADR-0140](adr/0140-a-widget-may-reach-its-window.md))
- **`option` moved, and the move cost one flag.** §3 gives `segmented` and
  `select` the same child node; TODO had recorded that it would move when there
  were two callers, and refused to guess what the second one would want — "a
  model, possibly a tree node, a popup to render in". Wrong in every part: a row
  in a dropdown is the same record as a cell in a bar. The whole difference is a
  stylesheet's ancestor — `segmented option` against `select-list option` — and
  **which of §3's two keyboards the set has**, which the specification states in
  as many words: a `radio-group` has "arrow keys move selection (roving focus)"
  and a `select` has "arrows, Enter/Esc". `Option.inAList()` is that, and it also
  unlocks `Enter`, which every other control in the catalog refuses because it
  belongs to a dialog's default action — a list is in a popup over everything and
  has no default action behind it. **The flag was found by a failing test**: with
  roving left on, the first `Down` in an open list chose a row, and choosing
  closes the list, so the second and third arrows had nothing to move
  ([ADR-0141](adr/0141-a-select-is-a-closed-control-and-a-list.md))
- **Two things fell out that are not about `select` at all.** A press that
  dismisses a popup no longer also activates what it lands on — the rule the
  launcher already applied to the secondary button (ADR-0108) turning out to be
  the general one, and without it a control that opens its own popup cannot be
  closed by clicking it again. And `Popup.focusOn(id)` exists, because a control
  that has already chosen must open on the row it chose: a list that focused its
  first row would answer `Down` with the second option whatever the value was.
  Focused **not** "from the keyboard", which matters more here than for a menu —
  a row focused from the keyboard in a roving set is chosen on the spot, so
  opening the list would report a change nobody asked for
  ([ADR-0140](adr/0140-a-widget-may-reach-its-window.md),
  [ADR-0141](adr/0141-a-select-is-a-closed-control-and-a-list.md))
- **It anchors to itself by rectangle, not by id.** `SelectField` is `Located`, so
  it is told where the last frame painted it and the state opens the popup there.
  Anchoring by `id` was the alternative and is worse: a `select` a document gave
  no `id` would need a generated one to open itself, and two in one window would
  then depend on that generation being unique. Six golden images and 49 tests,
  eight of them driving the real launcher — a click at a coordinate in the owner
  window, a second window opening, a click at a coordinate in *that*, and the
  value coming back through `change`. The gaps are stated rather than implied:
  typeahead works closed and not open, because a `TextEvent` goes to the focused
  row and there is no text capture phase for the list to take it in; the field is
  as wide as its current value, because no selector can measure a set of options;
  and `multiple`, `autocomplete` and `tree` are unbuilt, two of them waiting on
  `text-input` and `tree` rather than on a decision
  ([ADR-0141](adr/0141-a-select-is-a-closed-control-and-a-list.md))

- **Five things reported from the running window, and one of them was the frame
  itself.** The showcase was painting at 10–15 ms with nothing moving, worse when
  a tour or a menu appeared. The suspects were all innocent — the popup's second
  window, the veil, damage tracking — and the measurement said something much
  duller and much worse: **one `render` of a settled screen cost 10 069 µs for 77
  elements**, and 56 of 72 styled elements missed the style cache **on every
  frame**. ADR-0070's cache is keyed on the style a node's parent handed down, by
  identity, which is what makes inheritance invalidate itself. But the style a
  parent hands down is not the one it caches: `restyle` runs after the cache, by
  design, and every widget that writes an inline value allocates a fresh
  `ComputedStyle` every frame whether or not anything moved —
  `ScrollContent`'s is `resolved.flexShrink(0)`, unconditionally. So every node
  under a `scroll` re-resolved every frame, and in the showcase every screen is
  inside a `scroll`. A node now hands its children the **same instance** for as
  long as the value is equal, which is a flat record comparison against a
  re-resolve costing two orders of magnitude more: Controls 10 069 → 294 µs,
  Values 8 125 → 126, Text 2 607 → 50, Overlays 2 923 → 17, Tabs 5 036 → 22. The
  test asserts the mechanism rather than a duration — a widget whose `restyle`
  allocates, and its child handed the same object twice — because a timing test
  passes on a fast machine with the bug still in it
  ([ADR-0142](adr/0142-a-style-handed-down-keeps-its-identity.md))
- **A menu outlived the window that owned it.** Click on another application with
  the menu open and it stayed, on top, over the window you switched to. Light
  dismissal covered a press inside the owner and `Escape`, and neither of those
  happens when the user clicks somewhere else entirely — because **there was no
  focus event at all**, in the SPI or in the SDL translation. There is now, per
  window, which is what every platform reports; "the application lost focus" is a
  conclusion drawn from the whole set and the launcher is the only thing that
  needs to draw it. **The check is deferred by 60 ms**, and that is the mechanism
  rather than a fudge: opening a popup *is* a focus-lost for the window under it,
  followed by a focus-gained for the popup, so a menu acting on the first would
  close as it opened. Both outcomes have a test, and the second is the one that
  would have caught the naive version
  ([ADR-0144](adr/0144-a-popup-goes-away-when-the-application-does.md))
- **A dropdown is as wide as what it drops from.** A `select` stretched across a
  form opened a list as wide as the word "Dark". No measurement of the *content*
  can fix that — it is a fact about the anchor — so `host.popup` takes a
  **floor** under the width, applied inside the two-pass measurement it already
  did. A floor and not a width: an option longer than the field still widens the
  list past it. Opt-in per call rather than a property of `Placement`, because it
  is false for the other two callers — a menu is as wide as its commands and a
  tooltip as wide as its text
  ([ADR-0145](adr/0145-a-dropdown-is-as-wide-as-what-it-drops-from.md))
- **A tab strip took its height from the tallest thing in it, and a menu icon
  from the corner of its box.** Two drawing defects with one shape. Closing the
  last tab left the `+` — 24 square by design — as the tallest thing in the
  header row, so the strip shrank to it and the button rode high; the same rule
  was quietly wrong *with* tabs in it, measuring 30 where its tabs are 32, which
  is why the gallery's `controls` images moved by two pixels. A header row is one
  control tall by definition and now says so. And an icon was drawn at its box's
  origin, which is a no-op where the box is the icon and four pixels of
  misalignment where a stylesheet sized the box instead — `item-lead` is 16
  square, the showcase builds its palette at 20, and the row with the icon read
  as the odd one out. Centred now, which changes nothing in the common case and
  is what a slot means in the other. Both are pinned by pictures, because both
  are facts about where something is drawn
  ([ADR-0143](adr/0143-a-strip-keeps-its-height-and-an-icon-its-centre.md))

- **The HUD says where the frame went, and the build checks it stays there.**
  ADR-0142's 34× regression lived here for a month with every test passing and a
  HUD on screen reading `paint 12.4 ms` — true, and useless: a total says a frame
  is slow and nothing about which part of it is. Finding the answer took a
  purpose-built probe and a counter compiled into the renderer. So `hud` grew
  four readings — `build`, `style`, `layout`, `raster`, one word for the set of
  them (`readings="stages"`) — timed by five `nanoTime` calls in the painter and
  kept in the same 60-frame ring as the rate. They deliberately **do not add up
  to `paint`**: the hit-test capture and the frame's setup are in the total and
  in none of the stages, and making them add up would mean a fifth reading nobody
  can act on. Two decimals for a stage against one for a total, because `0.0 ms`
  cannot be told from a stage that is not running, and three ranks in the
  stylesheet because six equally bright numbers on one plate read as a wall. The
  showcase turns it on ([ADR-0146](adr/0146-a-hud-shows-where-the-frame-went.md))
- **And a frame now has a budget.** `FrameBudgetTest` measures the showcase's own
  tree at five resolutions from 800×600 to 4K, prints the table, and **fails the
  build** when a stage is over its ceiling — `style` measures 0.03–0.08 ms and is
  allowed 1 ms, which is useless against a 20% drift and exactly right against
  what happens: with ADR-0142's defect put back it reports 10.1 ms and names the
  stage and the resolution. Ceilings rather than stored comparisons, because a
  test comparing against a recorded number fails on a slower machine and passes
  on a faster one that regressed. **Two claims hold on any machine and are the
  sharper half**: style and build do not grow with the pixel count, because the
  cascade runs per element and a 4K window has the same elements as a small one;
  and a settled render is two orders of magnitude cheaper than a cold one —
  450–520× with the cache working and **11×** without it. Zero Blend2D workers,
  because a threaded context queues its work and a loop around `paint` measures
  *submitting* a frame, which is how the first run reported a 4K raster as
  cheaper than an 800×600 one. `FrameBenchmark` stays: it measures the engine's
  parts against each other, which is a different question from "is a real frame
  still fast"
  ([ADR-0147](adr/0147-a-frame-has-a-budget-and-the-build-checks-it.md))

- **A menu row wraps, and a wrapped label sits at the top of its row.** Reported
  as "the item after the iconed one is vertically aligned to top", and four
  rounds of measuring the rows could not reproduce it: every row is 32 tall and
  every label 8 from the top, at both densities and four display scales. The rows
  were never wrong. A menu row is a row of **measured leaves**, nothing stops
  those boxes shrinking, and a row squeezed narrower than its content does not
  clip its label — it wraps it. Two lines measure 32 in a 32-tall row, so
  `align-items: center` puts them at the top edge. **The widest row wraps first**,
  and the widest row is rarely the one with the icon — "Switch density Ctrl+D" is
  longer than "Switch theme Ctrl+T" — which is why it presented as something the
  icon had done. The label and the accelerator no longer shrink; the cost is
  `option`'s, taken for the same reason, that a label with no room overflows
  because nothing in this toolkit clips. The test asserts where the *paragraph*
  was painted rather than where the row was, and squeezes the menu to 160 logical
  pixels — the general sweep passes with the defect in place, which is what made
  it hard to find ([ADR-0148](adr/0148-a-menu-row-does-not-wrap.md))

- **A click on empty space re-resolved the whole tree.** Reported as "clicking
  empty space keeps adding a lot of ms", and the HUD from ADR-0146 is what made
  it findable: measured through the real launcher, **74 of 78 elements
  re-resolved per click** and `style` sat at 12 ms a frame. `:hover` and
  `:active` apply to the whole ancestor chain — `.card:hover .title` has to work
  — so a click marks every node up to the root, and each of those threw away its
  **whole subtree's** styles on the chance that a descendant combinator read the
  state. For a node near the root that subtree is the window. ADR-0070 said
  plainly that this was conservative on purpose; what nobody had was the number.
  `StyleResolver` now indexes, once, which pseudo-classes appear to the *left* of
  a combinator and on what type, so `checkbox:hover check-indicator` makes
  `:hover` on a `checkbox` reach down and nothing makes `:hover` on a `column`
  do so. **A node with no CSS type reaches nothing**, and getting that wrong is
  what made the first attempt change the measurement not at all: the hover chain
  is full of composition nodes, and treating them as "unknown, be conservative"
  is the same as not narrowing. **74 re-resolves per click → 3**, style 12.4 → 2.5
  ms, and what is left is transitions genuinely running rather than the cascade
  ([ADR-0149](adr/0149-a-state-invalidates-what-it-can-reach.md))
- **The HUD says what its numbers are, and colours the ones in trouble.** Three
  things were wrong with the breakdown as it shipped, all of them a correct
  number nobody could read: it never said the readings are **means over sixty
  frames**, so `paint 2.1 ms` reads as "this frame" and a spike looks like a
  plateau; it showed one total where there are two, so a slow frame could not be
  attributed to the toolkit or to the platform; and seven numbers in a row is a
  wall to scan. It is a column now, one reading a line, with `frame` beside
  `paint` and a caption under both. Every reading carries a **budget** — shares of
  a 60 Hz frame — and reports `ok`, `near` or `over` as a class the stylesheet
  colours, because §10 says a colour is a token and a widget that picked its own
  red could not be themed. Two readings are judged the other way round and both
  would otherwise cry wolf: the **rate** is a floor, and the **frame interval** is
  a target to sit *at*, since a vsynced loop measuring exactly 16.7 is success and
  a healthy window reading amber teaches a reader to ignore the colour. That
  needed one new hook, `Styled.classes(FrameStats)`: the cascade reads a node's
  classes before its `render` runs and the statistics only arrive in `render`, so
  a value cannot hold the answer in between
  ([ADR-0150](adr/0150-a-hud-reads-itself-against-a-budget.md))
- **The budget table gained both 2Ks.** DCI's 2048×1080 and the monitor aisle's
  2560×1440 are 25% apart, and a table that picked one would be answering
  somebody else's question
  ([ADR-0147](adr/0147-a-frame-has-a-budget-and-the-build-checks-it.md))

- **A frame can say what it did, and the first thing it said was that the cascade
  is slow per element.** Twice now the answer to "why is this frame expensive"
  has been a count rather than a duration, and twice it meant compiling a counter
  into the renderer and taking it out again. `-Dgoldberry.trace.frames=true` is
  that counter kept: one line per frame that did something, with the four stages
  broken down further into **cascade**, **identity**, **motion** and **boxes**,
  plus how many elements were built, resolved and invalidated, what asked for a
  subtree walk, and how many paragraphs had to be shaped. Free when off — one
  `static final boolean` the JIT folds away — and a system property rather than a
  log level, because an `isTraceEnabled()` per element per frame is a diagnostic
  measuring itself. `-Dgoldberry.trace.input=true` is the other half: what asked
  for the frame ([ADR-0151](adr/0151-a-frame-can-say-what-it-did.md))
- **It found `cascade 5.133 ms for three elements`.** With ADR-0149's narrowing
  in place a click re-resolved one or two nodes and `style` was still 1.5 ms,
  because **one resolve cost 1.7 ms**. Two reasons, both structural. Every rule in
  every sheet was matched against every element — four sheets, some two thousand
  rules, asked about for a `text` node as readily as for a `button`. And custom
  properties are collected by walking to the **root**, running a full cascade at
  every level, so one node at depth ten was eleven cascades — twelve, because
  `resolve` asked for the properties and the declarations separately and both
  cascade the element. ADR-0070 cached the *result* of this term and never made
  the term cheaper, so every miss paid in full. Rules are now bucketed by the type
  their rightmost compound names, custom properties are cached per element on the
  same identity scheme the computed style uses, and `resolve` cascades once:
  **one resolve 1.7 ms → 0.13 ms**, a click frame's cascade 0.48 → 0.26 ms, and a
  cold render of a whole screen — what a tab switch pays — **112 ms → 52 ms**
  ([ADR-0152](adr/0152-the-cascade-looks-at-rules-that-could-match.md))
- **And the HUD says it is in its own numbers.** Three paragraphs a frame are
  re-shaped in the showcase and they are the HUD's own readings: a string that
  changes every frame cannot be held by a cache keyed on the string. It cannot be
  taken out of the measurement without lying about the frame the window actually
  painted, so the caption reads `this hud included` — ADR-0101's rule kept by
  being honest rather than by pretending
  ([ADR-0152](adr/0152-the-cascade-looks-at-rules-that-could-match.md))

- **The frame interval is gone, and the display's refresh rate is in its place.**
  `frame` was counted between frames, and §1.7 makes the loop idle when nothing
  asks for one — so it measured how long the user had not touched the window. It
  collapsed the moment they stopped clicking and stayed low for the next sixty
  frames, because the ring is sixty long, and ADR-0150 had given it a colour: a
  window sitting still read **red, permanently**, which is how the showcase came
  to look broken at rest. **SDL has no frame rate to offer instead** —
  `SDL_GetCurrentDisplayMode` reports what the *display* does, and what a loop
  achieved is not a thing any platform knows. So `refresh` is asked for rather
  than counted: `SdlVideo.refreshRate` was already bound for the pacer and is now
  on the backend SPI, 0 meaning "the platform will not say". **Every budget is a
  share of one display frame** rather than of a hard-coded 16.7 ms — paint a
  half, raster a quarter, style and layout an eighth, build a sixteenth — so a
  120 Hz window judges its paint against 4.2 ms, which closes the gap ADR-0150
  left in TODO. `fps` stays and is **never coloured**: it is worth watching while
  something is moving and it is not a thing the toolkit is answerable for. One
  more lesson recorded: a reading's name is a CSS class, the first draft called
  this one `display`, and `.display` is §1.4's largest type rank — so it rendered
  at 28px in the golden
  ([ADR-0153](adr/0153-a-rate-is-counted-a-refresh-is-asked-for.md))

- **A reading is a range.** Every number on the `hud` was the mean over the
  ring's sixty frames — the right thing for a budget to judge and the wrong thing
  to read a frame by, because it hides the shape of the cost and the shape is
  usually the question. Two windows both averaging 2 ms of paint are different
  animals if one never leaves 1.9–2.1 and the other ranges 0.2–14, and nothing on
  the plate could say which. Each duration is `min / mean / max` now, with the
  mean in the middle where the eye lands and where the budget is still judged —
  colouring by the max would paint every window red for one slow frame in sixty.
  `FrameStats` grew a `Span`, defaulting to a **flat** one built from the mean, so
  a source that keeps no window reports its one number three times rather than
  inventing a spread. The caption says the unit and the shape rather than the
  arithmetic — `ms/frame · min / mean / max · last 60` — and `this hud included`
  is gone: true, true of every such diagnostic, and recorded in ADR-0152 where a
  reader who wants it can find it. **The level and the text now read the same
  span**, which is not tidying: the first draft judged `styleMillis()` while the
  row printed `style()`, and the over-budget golden came out with `style 4.80 /
  9.60 / 38.40 ms` drawn as though it were fine
  ([ADR-0154](adr/0154-a-reading-is-a-range.md))

## M3 — Shell

**Started.** `docs/core-widgets.md` §7 names two places an overlay can be drawn —
"the in-window overlay layer **or** backend popup windows as appropriate" — and
both now exist, with one widget on the first and the showcase opening one of the
second.

### The in-window overlay layer

- **The in-window overlay layer ships, and `hud` is its first occupant.** Every window's
  element tree is rooted at a `window-root` whose children are the application's root —
  in flow, growing to fill the window — and whatever is floating over it, each an
  absolute box pinned to a `Corner` with two of its four insets *undefined* rather than
  zero, which is the difference between a plate in a corner and a scrim across the
  window. Three things follow from that one shape and each is the point: an overlay
  takes no space from the content, it is painted after it because a box tree has no
  z-order beyond document order, and adding one **cannot re-parent the application** —
  the root node is there from the first frame whether or not anything is floating,
  because a layer that appeared with the first toast would throw away every element's
  state to show it. The list is a `Property` the launcher owns and the root *watches*
  through the `binding()` every widget already has (§9's `bind`, pointed at the
  toolkit's own state), since an element tree's root widget cannot be swapped.
  `host.overlay(new Hud(), Corner.BOTTOM_END)` is the whole API and the handle it
  returns is the way out
  ([ADR-0100](adr/0100-a-window-has-a-layer-above-its-application.md)).

### `hud`, the first widget on it

- **`hud` is §7's first widget** and the first in the catalog that is about the toolkit
  rather than the application: `60 fps` and `paint 2.1 ms`, read off a 60-frame ring
  `Window.paint` now writes unconditionally — two `nanoTime` calls a frame, where before
  every timing was behind `LOG.isTraceEnabled()` and watching a rate meant measuring a
  loop that was also writing a line per frame. The numbers travel down `Paints.Context`
  beside the frame clock, which is what lets a bare `hud` node in a document show live
  figures *and* lets a golden image show figures somebody chose.
- **It never asks for a frame**: a rate display that requested one would report the
  frames it had itself caused, and would falsify §1.7's "the frame loop is fully idle
  when no animation is active" for every window with one in the corner — so it reports
  the frames that were already happening, freezes with an idle loop, and draws dashes
  rather than zeroes when there is no loop at all, because a zero is a measurement
  ([ADR-0101](adr/0101-a-diagnostic-must-not-be-the-thing-it-measures.md)). The showcase
  toggles one from a `HUD` button or `Ctrl+F`, off by default, which is also what keeps a machine-dependent
  number out of §14's image corpus.

### Popup windows

- **The backend popup window is built** — the other half of §7's "in the in-window
  overlay layer *or* backend popup windows as appropriate". `Backend.createPopup(owner,
  spec)` opens a real platform window parented to another and positioned in **its**
  coordinates, which is the one thing an in-window overlay cannot do and exactly what a
  dropdown taller than the space below its button needs. A popup **is** a window — it
  acquires a frame, presents, paces and closes by the same code, and its events arrive
  through the same pump under their own id — so `Sdl3Window` became `sealed … permits
  Sdl3Popup` rather than growing a boolean, and popups are in `windows()` because
  shutdown enumerates windows.
- **It returns an `Optional` and empty is a normal answer**: popup support belongs to
  the video driver, not to the request. All four desktop drivers declare it — cocoa
  included, which was worth checking — and SDL's `dummy`, which every headless test here
  runs under, does not; so the refusal is a branch CI runs on every platform and the
  fallback is the in-window layer, clipped to the window. Two things the tests found
  rather than assumed. `SDL_WINDOW_TOOLTIP` alone does **not** stop a popup taking focus
  — `NOT_FOCUSABLE` is a separate flag, and §7's "shows on keyboard focus, never
  focusable itself" is false without it; and `0x80000000` turned out to be the first
  constant in the toolkit with the top bit set, which the layout probe read into a
  signed `int` and refused, so a constant row's value is now read unsigned (a *size*
  that is negative still means the table is being read wrongly). And **a resize is a
  request**: on X11 the window manager grants it when it likes, `size()` honestly
  reports the old one until then, and `HeadlessPopup` defers its resize the same way so
  that the fake is not the one place a caller who measures too early passes
  ([ADR-0102](adr/0102-a-popup-is-a-window-the-platform-may-refuse.md)).

### A widget tree in a popup

- **A `Popup` is an element tree, a render tree and a pointer router of its own,
  in a window of its own** — wrapped in the same `Window` the launcher uses, so
  the existing frame loop paints it, the existing dispatch delivers its events,
  and its pointer goes through its own router. What is **shared** is the
  renderer: the stylesheets, the font book and the frame clock, read live rather
  than captured, so a popup is themed by the cascade of the window that opened
  it, restyles with it and animates on the same tick. What is not shared is the
  tree — a popup's contents are a root, not a descendant, so nothing inherits
  into them and no descendant selector reaches them
  ([ADR-0103](adr/0103-a-popup-is-a-second-tree-in-a-second-window.md)).
- **Light dismissal needed input the router will not deliver.** §7 gives a
  popover "light-dismiss on outside click/Esc", and neither reaches a widget: an
  outside click usually lands on *nothing*, and `Escape` belongs to no control in
  particular. `Window` grew one package-private `InputWatcher`, called before
  routing; the launcher watches the owner window and the popup watches its own
  for `Escape`, because once a menu has focus the key goes to it. A press
  *inside* is deliberately not watched — that is someone choosing an item.
- **A menu is anchored to a rectangle from the last frame.** `Host.anchor(id)`
  answers from the same `HitTest` capture the router is fed, because where a
  button *is* is a fact about the frame that was painted (ADR-0080). By id rather
  than by element because that is how §7's `tour` asks for it and because an
  application holds ids; a `popover` anchoring to itself will want the element
  form.
- **Closing a window closes its popups, and that is not tidiness.** The event
  loop runs until `windows()` is empty and SDL destroys a window's popups with
  it — leaving this side holding dangling handles *and* entries in the window
  map, which is a process that never exits. Both backends close a window's popups
  first, `headless` included, because that is where the bug would otherwise pass.
- **The showcase demonstrates both**, one button each: `Menu` opens a real
  platform popup under its own button and is free of the window's bounds, and
  `HUD` floats a `hud` in the window's own layer, clipped to it and needing
  nothing from the platform.

### `popover`, and the three things it is made of

- **A popup measures its own content, and the second pass is the interesting
  one.** `RenderTree.measure` lays a tree out with no surface — two floats rather
  than a `LogicalSize`, because "undefined" is what has to be expressible and a
  size refuses `NaN`. The trap is that **Yoga lays a root out at exactly the
  available size when that size is definite**: there is no parent for it to be "at
  most" of, so a bound and a target are the same number, and measuring a menu
  against its window returns the window. That happened twice, once per axis —
  `960×640`, then `960×108` — and both times it looked like a placement bug. So
  the measurement is nothing definite, then a second pass with the width pinned
  only if the natural width overflows, where a definite width is now what is
  wanted and a paragraph wraps at it. The same trap caught the widget:
  `Popover.render` grew to fill its window, and a growing root fills a definite
  available size ([ADR-0104](adr/0104-a-popup-is-measured-then-placed.md)).
- **`Placement` is three rules and no state**: preferred side, **flip** only when
  it does not fit and the opposite side does — not when the other side merely has
  more room, which would be a menu nobody can predict — and then **shift** along
  the cross axis, which keeps the popup attached to its anchor's side while
  sliding it along. Too big for the screen either way and it clamps to the *near*
  edge, so the top of a long menu survives. It opens no window and reads no
  display: an anchor rectangle, a size and the rectangle to stay inside go in, a
  point comes out, and every case of it is a test rather than a screenshot.
- **The rectangle it must stay inside is the display's *work area*, not its
  bounds.** `SDL_GetDisplayUsableBounds` excludes whatever the desktop reserved,
  and the difference between the two rectangles is exactly the taskbar a menu
  would otherwise open underneath. `BackendWindow` gained `workArea()` and
  `position()`, both `Optional` because some drivers will not say; the launcher
  translates the first by the second so placement works entirely in the window's
  own coordinates. `HeadlessBackend` has a pretend desktop of 1920×1040 — 40
  pixels reserved, so a test that confuses the work area with the display's size
  fails — and its windows can be moved about on it, because a placement policy is
  only interesting near an edge.
- **The keyboard belongs to the open popup.** Its router focuses the first item
  after the first frame, and keys the *owner* window receives are forwarded to the
  topmost popup before the owner's own router sees them —
  `Window.InputWatcher.keyPressed` returns a boolean now, and `true` takes the
  key. Not belt-and-braces: whether a popup has the platform's keyboard focus is
  per-driver, so without forwarding an arrow would move the selection in the
  window *underneath* the menu on half the platforms.
- **`popover` is the panel and not the opening.** §7's floating surface —
  background, border, radius, padding — as a widget, so it is themeable and
  writable from a document; where it goes and when it goes away is `Host.popup`,
  which serves `tooltip`, `select` and `menu` equally and is not a popover. The
  showcase's `Menu` button opens one with `host.popup(content, "menu-button",
  Placement.BELOW)`, and on X11 it comes out 125×108 — its own content's size —
  directly under the button that opened it.

### `tooltip`, and the timer under it

- **The event loop grew a timer.** `EventLoop.after(delay, action)` runs something
  on the UI thread later and shortens the next pump so the loop wakes for it —
  the loop's, because the loop is the thing that is asleep and a delay implemented
  by sleeping elsewhere fires on time and then waits up to a second for the pump
  to notice. Two consumers are named in the specification (a tooltip's delay, a
  submenu's hover intent) and a toast's timeout is the third.
- **A tooltip is an attribute, not a widget.** §7 attaches one to *any* widget, so
  the text rides on `Attributes` beside `id`, `class` and the key — the three
  things every widget carries and none decides. The router says when the hovered
  or focused node moved and opens nothing; the launcher owns the window and does
  the rest. The target is found by walking **up** from the hovered element,
  because a tooltip on a `button` has to survive the pointer being over the
  button's *label*, which is a different element and the one a hit test reports
  ([ADR-0105](adr/0105-a-tooltip-is-an-attribute-not-a-widget.md)).
- **Adding a component to `Attributes` broke every wither, silently.** `id()`,
  `classes()` and `key()` each rebuilt the record and dropped the new field, so
  `.tooltip("Save").id("save")` lost its tooltip — no error, nothing in a log, and
  a test already written that failed for what looked like a timing reason.
- **It is never light-dismissed.** A press would close it in the same gesture as
  the click on the thing it describes, taking the next tooltip's timer with it. It
  also cannot end up under the pointer, by construction: it is placed outside the
  anchor's rectangle and the pointer is inside it, above or flipped below.

### `menu`, `item` and `separator`

- **A menu is a widget; opening one is a call.** `Menus.open(host, anchor, menu)`
  measures the panel, places it, opens a platform window, wraps every command in
  "and close the stack" and hands each row with children a way to open its own
  submenu. It is not a method on the widget because opening needs a `Host`, and a
  widget holding the window it is drawn in would be describing its own
  surroundings — so the opener rebuilds the tree and supplies what only it knows,
  which is what `radio-group` does to its `radio` children
  ([ADR-0106](adr/0106-a-menu-is-a-widget-and-opening-one-is-not.md), ADR-0073).
- **A nested `item` is the submenu syntax**, so there is no `submenu` node to
  forget and no way to write one that is not a submenu. Submenus open **beside**
  their row — `Placement.AFTER`, flipping near the screen edge — after 150ms of
  hover intent, and close their siblings as they open, so travelling down a menu
  past three rows with submenus leaves one open rather than three.
- **A submenu is anchored inside a popup, which needed a second `anchor`.**
  `Host.anchor` answers from the main window's geometry and knows nothing about
  what is in a popup, so `Popup` gained one that translates by its own offset —
  without which a submenu opens at the right place relative to the wrong origin.
- **A `menu` is a vertical focus scope.** `Up` and `Down` move between rows;
  `Left` and `Right` are deliberately not traversal, because in a menu they mean
  "close this submenu" and "open that one". `Escape` was already the popup's.
- **The tick column is always built**, checked or not, so a menu's labels line up
  the moment one row becomes checkable rather than shifting sideways.
- **The accelerator is displayed and not registered.** §8 asks for both; a
  shortcut has to work while the menu is *shut*, and a menu is built when it opens
  and thrown away when it closes. Registration needs something that owns menus for
  longer than one opening — which is what `menubar` needs too, and why neither is
  here.
- Driven by four tests that post **real clicks** into the popup's own window
  through the real launcher and frame loop: a command runs and closes the stack, a
  hover opens a submenu beside its row, a command inside the submenu closes both,
  and a disabled row does neither.

### Context menus

- **The two halves of one feature sit on opposite sides of the module boundary,
  and the seam is one sentence wide.** Only `:core` can notice the right-click —
  it has the router, which knows what is under the pointer, and the window, which
  is where a popup goes — and only the catalog can turn a name into a menu, because
  opening one means wrapping every item so that choosing it closes the stack. So
  `Host.onContextMenu` hands over *the name and the point*, and
  `Menus.contextMenus(host, map)` is the line an application writes
  ([ADR-0108](adr/0108-a-context-menu-is-a-name-on-a-widget.md)).
- **The name rides on `Attributes`**, beside `id`, `class`, the key and the
  tooltip — which is what "any widget" has to mean, including one in an
  application's own module.
- **The press is taken.** `InputWatcher.pressed` returns a boolean now and learned
  which button and where, so a right-click that opens a menu does not also reach
  what it landed on: right-clicking a button opens its menu rather than pressing
  it.
- **Anchored to the pointer, not to the widget**, so two right-clicks in one list
  open two menus in two places. An unregistered name is logged rather than thrown,
  because a `press=` typo is found when the document loads and this one is found
  on a right-click, where throwing takes the window down.

### `tabs`, the first widget of §5

- **Adding and removing needed no API.** The strip reads its selection through
  `bind` and reports three things — `change`, `close`, `new` — and the application
  answers all of them, which is `radio-group`'s shape extended to a set whose
  *membership* changes. A strip whose `close` handler does nothing keeps its tab,
  which is the visible form of "the model did not change". There is no `addTab`,
  no internal list, and so no second copy of the thing the tabs are *of*
  ([ADR-0107](adr/0107-a-tab-strip-is-a-model-a-header-and-a-panel.md), ADR-0063).
- **A tab takes a colour, which is the first value of its kind in the catalog.**
  `colour="#bf616a"` is application data — a tab coloured after its project — and
  a stylesheet cannot know it, because there is no selector for "the tab whose
  project is red". Written through `restyle`, so the stylesheet still decides what
  the colour *means*: `controls.css` puts it on the label and on the underline,
  and a tab given none is styled entirely by the theme. The syntax is CSS's, via a
  new `CssColor.parse(String)`.
- **Content is lazy by omission**: only the selected tab's widgets are built into
  elements at all, so nine background tabs cost nine headers. The cost is stated
  where somebody will read it — a tab's content is **rebuilt when it is selected
  again**, so a scroll position or a half-typed form belongs in the model.
- **The underline is a box, and the golden image is what said so.** The first
  version wrote `border-bottom` and `currentColor`; §8's subset has one `border`
  covering all four edges and no `currentColor`, so both declarations were
  silently dropped — every number in the layout correct and the underline simply
  not there. It is a 2px box pinned across the header now, and the rule under the
  row is another across the list, which is `segmented-indicator`'s anatomy for the
  same reason. Fifth time a golden image has caught something no assertion did.
- **One Tab stop, and the × is not in it.** A focusable close affordance would
  make nine tabs nineteen stops between the strip and the content; `Delete` on the
  tab is the keyboard's answer. The `+` *is* focusable, because adding a tab is a
  destination the roving selection should reach.
- Two new marks — `CROSS` and `PLUS` — rather than two icons: at eight to ten
  logical pixels inside another control, an icon's metrics and lookup buy nothing.
- The showcase's strip is dynamic, which is the third reason that pane is in Java:
  KDL can write three tabs, not "however many the model has".
- **And then three things were wrong with it, each a different lesson**
  ([ADR-0109](adr/0109-a-tab-arrives-and-departs-on-the-frame-clock.md)). A tab
  added or closed **did not appear until the window was resized** — not a tab bug:
  the showcase held its tabs in a plain `List`, and a plain list is not something a
  widget can subscribe to. Everything else in that window is a *value* reaching a
  bound widget and needs no rebuild; this is the first thing in it that changes the
  **shape** of the tree, so it is a `Property` now and the pane that builds the
  strip watches it, exactly as it already watched the two other structural changes.
  The resize was a red herring twice: it made the tabs appear because it rebuilt
  the tree for another reason, and it made the bug look like a layout problem.
- **The `+` was 28 wide and 20 tall**, and a mark is drawn to fill its box — so the
  cross had a long arm and a short one. One number now. The `margin` that would
  have spaced it from the last tab is not in §8's subset either, which is the third
  property this widget has reached for and not found.
- **Tabs arrive and depart on the frame clock**, which is the toolkit's first
  enter/exit animation and could not be a transition: an arriving tab has no two
  styles to move between, because its element did not exist last frame; and a
  departing one has already been dropped from the application's list, so without
  something holding on there is nothing left to animate. So `Tabs` has state — which
  tabs are arriving, which are leaving, and what the leaving ones last looked like
  — and each tab is handed *are you animating* and *how visible are you now* as
  functions, because `Tab` is public and its phase is not a type anyone outside the
  module can name. Opacity and a 6px translation only: a tab that animated its own
  width would run Yoga every frame and reflow the row beside it.
- **Making `Tabs` stateful put two `tabs` nodes in the cascade**, one inside the
  other, so every rule applied twice. The model node is a composition node now and
  the node it builds carries the appearance, the CSS type, the attributes and the
  focus scope.

### The showcase is a gallery

- **A window is a title bar and five screens**, one per tab:
  Controls (§3's controls whose value is a state), Values (§3's controls whose
  value is a number), Text (§2's paragraph and the buttons that act on the model),
  Overlays (§7's two places something can float), and Tabs (§5's strip gaining and
  losing tabs). `core-widgets.md` asks for exactly this — "a gallery app exercises
  every widget in every state in both themes… **a widget isn't done until it's in
  the gallery**" — and what existed was a *sidebar*: one document holding every
  control there was, which worked at four and was failing at eleven
  ([ADR-0110](adr/0110-the-showcase-is-a-gallery-of-screens.md)).
- **A screen is a file**, so adding one is a file and a line, and the gallery
  knows nothing about what is on any of them.
- **Three screens are documents and two are Java, and which is which is the
  point.** Not appearance — what §8's markup cannot say: Text is Java because
  Undo and Reset are disabled when the click count is zero and markup has no
  expressions, and Tabs is Java because its list *changes* while the window is
  open and KDL is data. Everything else is `bind=` and `change=`.
- **The gallery's own selection is an ordinary bound value**, so `Ctrl+1`…`Ctrl+5`
  and the strip are two ways to set one property rather than two copies of a
  selection. Its strip is deliberately fixed — a gallery whose chrome could be
  closed is a gallery you can break — and the Tabs screen is where a closable,
  addable strip is demonstrated instead.
- **Only the selected screen exists**, which is §5's lazy content doing the work:
  four of the five are not in the element tree at all, so a five-screen window is
  as cheap as the one-pane one it replaced.
- **Six golden images of the example**, which is the first visual coverage the
  showcase has ever had — before this, a screen that rendered blank passed every
  test it had. Two things had to be fixed for them to mean anything: `:example`'s
  test JVM did not know where the native library was, so every one of them
  *skipped* — a green build that checked nothing — and the Values screen has a
  `spinner` on it, whose rotation is a function of the frame clock, so against the
  system clock the image failed by 113 pixels and a channel delta of 144. The
  renderer takes a virtual clock now.

### Five faults in one window, and none of them was the same bug

Reported against the gallery, and worth listing because the interesting one had
been there since text was first painted
([ADR-0111](adr/0111-a-text-box-is-painted-inside-its-padding.md)).

- **A text box was painted outside its padding.** `BoxPainter` drew a paragraph at
  the box's own origin and wrapped it at the box's full width — but Yoga sizes a
  measured leaf as its content **plus** its padding, so every one of those pixels
  ended up on the right and the bottom with the text hanging off the top-left
  corner. Magnified 3×, a tooltip's glyphs were visibly outside their own plate.
  Nothing had hit it because every other widget in the catalog puts text in a
  *child* box — `button`, `option`, `badge` — for `Option`'s reason: Yoga never
  lays out a measured node's children, so a control holding its own text could not
  also hold an icon. Every other golden image is byte-identical after the fix,
  which is the evidence it touched exactly the case that was broken.
- **A popup's rounded corners were black.** What is outside the radius is
  *nothing*, and nothing was being presented as an opaque buffer nobody had
  cleared. Popups are `SDL_WINDOW_TRANSPARENT` now and their frame is cleared to
  transparent — the surface format was checked rather than assumed, and X11 hands
  back `ARGB8888` for these windows, so the alpha survives the blit.
- **The cursor changed the moment a tooltip appeared**, and it was not the router:
  headlessly the same sequence keeps both the hover and the `pointer` cursor,
  which is what pinned it on X11 — mapping a window near the pointer makes the
  server report a *leave* for the window underneath. The launcher swallows an exit
  that arrives within 250ms of opening a tooltip, bounded rather than flagged so
  that a driver which sends no spurious exit does not have the user's real one
  swallowed instead.
- **Warning spam** — `ease-out` is not one of §1.7's easings (they are
  `ease-enter` and `ease-exit`) and `background` is not transitionable
  (`background-color` is). Two rules got both wrong and the engine said so once
  per node per frame.
- **`align-self` is not in §8's subset**, so the `+` in a tab strip sat at the top
  of its row. The row centres its children instead; adding the property would be a
  20-component record change in two records for one `+`, and is recorded rather
  than done.

### What the pointer being somewhere means

Two faults in one menu, and they are opposite halves of one question
([ADR-0112](adr/0112-a-menu-follows-the-pointer-and-lights-for-the-keyboard.md)).

- **A submenu did not close when the pointer left the row that opened it**,
  because ADR-0106 handed `onOpenSubmenu` only to rows that had one — the wrong
  half of the relationship. A submenu is closed by the pointer moving to a
  *sibling*, and most siblings have no submenu of their own. Every row is handed
  `onHovered` now and the menu decides what arriving means: open one, or put away
  what the row above opened. Both go through the one intent timer, because they
  are one gesture. The rename is the point — `onOpenSubmenu` described what the
  caller wanted, `onHovered` describes what the item knows.
- **The first row always looked hovered.** A menu focuses its first row as it
  opens so an arrow key has somewhere to start, and it did so through a call that
  reports the move as the *keyboard's* — so `item:focus` lit it. Focus and the
  highlight are two things: `moveFocus` takes a `fromKeyboard` flag now, and the
  highlight is `item:focus-visible`, which is what §2.2 defined that pseudo-class
  to mean. Open a menu with the mouse and nothing is picked out; press `Down` and
  the row it lands on lights up.
- **A tooltip takes `body` rather than `caption`**, with 8px and 12px of padding.
  §1.4 gives caption to secondary text *under* a control, where the reader has the
  control for context; a tooltip is the only text on screen at the moment it is
  read.

### A menu's geometry, twice made per row and belonging to the menu

- **A submenu opened on top of the border of the menu it came from.** ADR-0106
  anchored it to its *item*, which is right for one axis and wrong for the other:
  an item's right edge is a few pixels inside the menu's — the panel's padding and
  its border — so the submenu's left edge landed inside the parent's frame. The
  anchor is two rectangles now, **x from the popup and y from the row**, with a 2px
  gap: far enough that the two panels do not share an edge, near enough that a
  pointer crossing it does not leave both menus
  ([ADR-0113](adr/0113-a-submenu-is-placed-beside-its-menu.md)).
- **Every row in every menu was indented by a tick column**, whether or not
  anything in that menu could be ticked. The rule — a column that appears with the
  first tick shifts every label sideways — is right *within* a menu and had been
  applied to all of them. A menu reserves a column when anything in it is
  checkable, and then every row has one; which needs `checked` to have three
  states, because "unchecked" and "not a checkbox" had been the same value.
  `Boolean`: on, off, and not a checkbox at all.
- **And that was still not the whole of it.** A row's icon was drawn *after* the
  tick column rather than in it, so a row with an icon sat further in than the rows
  above it — which the showcase's own menu shows, having both an icon row and a
  checkable one. There is one leading part now, `item-lead`, with one width and
  three possible contents: a tick, an icon, or nothing. No menu anywhere shows a
  tick and an icon on the same row, because the tick is the row's *state* and the
  icon is its *identity*.
- **And a row that leads somewhere now says so.** A submenu row was drawn exactly
  like a command; the chevron is a painter mark beside `CROSS` and `PLUS` rather
  than Lucide's `chevron-right`, because an icon owns native memory that must be
  closed exactly once and a menu is built and thrown away every time it opens.
- **The menu has golden images**, five of them, where it had none — a menu is
  drawn in a window of its own and appears in no other picture in the corpus. Both
  faults were visible the moment there was one.

### The wheel, settled

`docs/design-system.md` §2.4 has asked for "pixel-precise wheel/trackpad deltas
with line fallback" since it was written, and `docs/ARCHITECTURE.md` §17.1 has
carried it as an open disagreement for as long: SDL exposes no pixel axis, and
going around it to Wayland and macOS is what ADR-0056 declined. `scroll` is the
first widget that has to care, so the question came due, and reading the header
again is what answered it — `SDL_MouseWheelEvent` carries **two** numbers per
axis and the toolkit was reading one of them.

`x`/`y` are fractional, which is where the smoothness is. `integer_x`/`integer_y`
are SDL keeping the running fraction itself and emitting a whole click when it
crosses one. Neither is derivable from the other, and the difference is not
academic: a trackpad dragged slowly reports a long run of values that each
truncate to zero, so a control that truncates per event **never moves at all**,
however far the user scrolls. The `integer_*` pair is the fix for exactly that,
and it has been declared in the layout probe and unread since the bindings
landed.

So `PointerWheel` and `PointerEvent` now carry `ticksX`/`ticksY` beside the
deltas — passed through from SDL rather than derived, negated on the same axis
and for the same reason. A distance reads the float; a step reads the int. Every
path with no accumulator of its own truncates, so the pair is always populated
and never a lie. What a line is worth in pixels is the scrolling widget's, not
the event's — and not a token, because nothing lets a widget read one.

`Knob` was not changed. §3 calls its wheel a rate and ADR-0089 built it as one,
so it is not a detent consumer; the reader of detents is `select`'s, when it
lands. The two tests worth having are the two that could not be written before:
a detent whose sign disagreed with the fraction beside it would send a stepping
control one way and a scroll view the other, and a detent arriving under a
0.125 fraction is the case no function of one event's floats can produce.

### `scroll`, and the geometry it needed

The one widget `book/src/TODO.md` named three times in three unrelated entries: a
menu taller than the work area loses its bottom, a tab strip wider than its
window overflows it, and `select` over a realistic option list cannot be written
at all. Built as three nodes, each one idea — a `scroll` viewport that clips and
takes the input, a `scroll-content` that is translated, and whatever was written
inside.

The offset is a **transform**, not a layout property. §1.7 already refuses to
transition width and height because animating them would run Yoga per frame, and
an offset expressed as `top` would run Yoga over the whole subtree on every wheel
notch to move a box that did not change size. The transform costs nothing, and
two things then come out right for free: the router inverts the matrix, so a row
scrolled up by 200px is clicked where it looks; and ADR-0114's clip is
intersected in the same walk, so content moved out of the viewport is cut at its
edge. `flex-shrink: 0` on the content is the whole difference between a scroll
view and a squashed one — Yoga's default would have compressed the content to fit
and left nothing to scroll.

**The hard part was the geometry, and it is new machinery.** Scrolling is
arithmetic on two rectangles and a widget can measure neither: `build` and
`render` both run before Yoga, which is ADR-0080's finding and the wall ADR-0097
hit from the other side. Both of those found ways to avoid needing the number.
This one cannot — the clamp *is* the widget. So `PointerEvent` and `KeyEvent`
each gained `bounds()` and `part()`, two `Extent`s the router resolves out of the
snapshot the last paint left behind, reusing `Handles.localPart()`'s existing
vocabulary: a scroll view names `scroll-content`, is handed its viewport and its
content in one event, and the clamp is a subtraction. It is on `KeyEvent` too,
because `PageDown` needs both extents while carrying no position at all, and a
scroll view that only worked with a mouse would fail §1 outright. The
measurements are one frame old, which is honest — the alternative is a widget
that computes layout, and that is the thing three ADRs have now declined to
build.

At its edge it lets go. A wheel or a key is consumed only when something actually
moved, so a scroll at the top of a list bubbles — §2.4's "inner scroller consumes
until its edge, then chains to the ancestor", obtained from the router's ordinary
bubble rather than from anything knowing an ancestor exists. `pointerWheel` now
reports consumption, which `keyPressed` already did.

Seventeen tests, and the shape of them is the point: every one needs a painted
frame before it means anything, because a scroll view that was poked directly
would be testing a calculation nobody performs. The first version of them failed
uniformly — six rows of text came out 94 tall in a 100-tall viewport, so there
was nothing to scroll, which is exactly the bug the widget exists to fix seen
from the test's side.

### The bars, and being told what you measured

§2.4's overlay scrollbars, taken as written: a 6px thumb widening to 10 with a
visible track on hover, `full` radius, accent while dragging, and a fade 800ms
after the last movement. Dragging scrolls and a click on the track pages.

The thumb needed something that did not exist. ADR-0116's extents arrive on the
event that asks to move, which is enough to clamp and useless for drawing — a
thumb whose length says what proportion of the document is visible has to be
right *before anyone touches anything*. So `Measured` is the other direction:
once per frame, only on a change, a widget is told what the last frame laid it
out as.

The obvious worry is the loop — a measurement causes a rebuild causes a frame
causes a measurement — and the answer is that the bars are absolutely
positioned, so nothing the rebuild draws can change what was measured. The
second frame measures what the first did and the router notifies nobody. The
first attempt avoided `setState` entirely on exactly that worry and was wrong
the other way: without a rebuild the extents never reach a build and the thumb
never appears. The tests assert the convergence rather than the argument.

Two things that look like styling and are not. The bar has **no padding**,
because the arithmetic runs against the viewport's length and an inset track
would let the thumb overrun the bottom by exactly the padding. And the fade is a
clock rather than a transition, because no selector can express *when* — so the
wake is flagged and the next frame stamps it, the way a tab's arrival is.

### Where it went

Three entries closed by doing. Every gallery screen is in a viewport — including
the short ones, because a viewport over content that fits costs an element and a
screen that is short at one window size is tall at another. A menu longer than
the screen becomes a menu of the screen's height with its items scrolling, capped
by `Menus` rather than by the popup facility: `:core` has no widgets to wrap
anything in, and more to the point a *tooltip* that scrolled would be a tooltip
that should have been a dialog. And a tab strip scrolls its headers, with the
rule left outside the viewport so a scrolled strip does not take its own
underline with it.

The tab strip is where a bug in the viewport surfaced. It laid its content out
as a column regardless of axis, and a horizontal viewport doing that *stretches*
its content to its own width — so the measured overflow is zero, nothing ever
scrolls, and the tabs spill out of a box that claims to fit them. Only a real
horizontal consumer could have found it.

**Still owed:** `scrollIntoView` (which `affix`, `tour` and selecting an
off-screen tab all want), the "always show scroll bars" reserved gutter, and the
chevrons at the ends of a tab strip. All of it is in [TODO.md](TODO.md).

### `affix`, `scrollIntoView` and `tour`

The three things `scroll` was owed, and one more geometry facility to carry them.

**`affix`** is §1's sticky child. Every geometry facility so far carried a
*size*; this one needs a *position*, and one no widget can compute — whether a
header has gone above its viewport is a comparison between where it was painted
and where a node it cannot see has its edge. `Located` answers it, and the clip
is what made it small: the obvious reading of "the nearest scroll view's
rectangle" is an ancestor walk with a cast, coupling the router to a widget in
another module and answering nothing for a node inside two viewports. ADR-0114's
clip is already that rectangle, already computed, already on every region for hit
testing. Nesting composes for free, and "nothing clips me" resolves to the window,
so an `affix` outside any scroll view pins to the page rather than being a special
case.

A widget told where it is must not move itself, or it is told a new position and
moves again forever. The escape is structural: `affix` is a hole that never moves
and a content node that slides under it — which is the same shape §1 needs for the
hole anyway, so the constraint and the requirement turn out to be one thing.

**`scrollIntoView`** went the wrong way first, and that is the useful part. A
`Reveal` widget wrapping whatever wanted to be seen reads better than a
controller and broke two tab goldens and two motion tests the moment it went
around a tab header: a wrapper is a box, and a box in a flex row changes how that
row is sized. There is no node transparent to flexbox, so any widget that adds
one to observe layout can change the layout it observes. §1 words this as an API
rather than as markup, and that is the load-bearing part of the wording — an API
adds no node. `Tab` was already a `Handles` node and is now a `Located` one, with
no parent gained.

**`tour`** is §5's guided sequence: a veil, a card, Back/Next/Skip, `Esc` to
skip the whole thing, and a target named by id that is skipped with a warning
when it is not on screen. The veil is four rectangles rather than one with a
hole, because §8's subset has no mask — and the workaround turned out better than
the thing it replaced: nothing covers the target, so it stays live and a stop
that says "click here" can be obeyed without the tour arranging an exception to
itself. It needed one small facility, `Host.fill`, which is an overlay inset on
all four sides rather than two.

### The showcase screen, which found the bug

A sixth gallery screen — a list with four sticky headers, jump buttons, and a
tour that points at them. It is the one screen *not* wrapped in the gallery's own
viewport, because §2.4 bans nested same-axis scrollers and the screen that
demonstrates the rule is where it has to be kept.

It found two real defects. `Located` fired only when a rectangle
changed, and a header asked to scroll itself into view is *in exactly the
position it was already in* — so the request was made and never heard. The cache
now holds the widget as well as the rectangles, compared by identity: a rebuilt
node hears again even if it has not moved, and a still window still notifies
nobody, because an element that was not rebuilt holds the same instance.
`Measured` had the same latent bug and has the same fix; nothing had hit it,
because its one consumer is a scrollbar whose state is stable. Which is what a
second consumer is for.

The other was only visible as a picture. Fifteen tour tests passed while the
card was drawn down the whole left edge of the window and stretched to its full
height: `Insets` is in CSS order — top, right, bottom, left — and the placement
passed left and top, which anchors a box by its top *and its bottom*. Every
assertion about what the tree contained stayed true, because the defect was two
numbers in the wrong argument positions. `TourGoldenTest` is the answer, and it
is the right kind of test for this widget rather than an extra one: which region
is dimmed and which is lit is not a question a widget tree can be asked.

### Eight things the running application said

The showcase had been rendered and not *used*. Running it produced a list, and
two of the items were defects the whole suite was blind to.

**A `setState` asked for no frame.** Reported as "the scroll starts working on
the second or third turn of the wheel", and it was neither a scroll bug nor
about the wheel. Two rules met and left a hole: §1.7 says the frame loop is idle
unless something asks, and ADR-0052 says `setState` defers. Nothing connected
them — `handlePointerWheel` did not repaint at all, and every other handler asked
`repaintIfRestyled`, which is a question about `:hover`, not about state. So a
widget that changed its own state waited for an unrelated event to paint it.
Every stateful widget had this; it hid because most of them change a
pseudo-class in the same gesture, and a scroll view changes none. The tree now
tells its window when it goes from clean to dirty, once per transition. **No test
in the suite could have caught it** — a widget test drives frames itself, so it
is a frame loop that never asks whether anyone wanted one.

**A pinned `affix` was painted underneath the rows sliding over it.** `AffixTest`
passed the whole time, because every assertion it makes is about a *position* and
all of them were true. A box tree has no order beyond document order, the affix
sits at index N, and the rows at N+1 are drawn afterwards — so a background
cannot help. This is exactly why CSS puts `position: sticky` in the positioned
layer, and `Box.elevated` is that rule at its narrowest: one bit meaning "draw me
last", no stacking context, no `z-index`, no ordering among elevated siblings.
Layout is untouched, and the hit test reorders with the painter — a box drawn on
top that was not *clicked* first would be a header you can see and point through.

The other six were smaller and mostly mine. The tour anchored to the **layout**
rectangle rather than the painted one, so a target inside anything scrolled was
described in the wrong place, and its card was aligned to the target's left edge
rather than centred on it — which put every card in the same place and made the
sequence look static. The lit target had no edge of its own. `--gb-text-subtle`
was a token this file invented, defined nowhere, logged as a dropped `var()` on
every frame, and — once defined — produced a counter nobody could read: there is
no third text rank in this palette, and the size carries the demotion instead.
`border-bottom` is not in §8's subset and was being dropped with a line in the
log each time. And the tab demo's panel could not fill, because it is inside a
`scroll`, where the remaining height is nothing — correct, silent, and now an
explicit height.

Two goldens came out of it, and they are the right kind of test rather than
extras: `affix-pinned` because "the header is at the top of the viewport" and
"you can read the header" are indistinguishable to any assertion about the tree,
and `tour-edge` because a card clamped against the window's edge is a placement,
not a value.

### Three more from running it

**The jump buttons were never working.** Reported as stopping after a few
clicks; the first press happened to be a scroll *forwards*, which any
measurement gets right. ADR-0120 says the thing that wants to be seen measures
itself, so the section's header did — and that header is inside an `affix`, so
the moment its section starts scrolling away it is **pinned to the viewport's
edge**, by design, permanently. A reveal measured against it concludes the
section has already arrived, however far away it is. Two rules, each correct
alone, composing into a widget that can never ask to be scrolled to.

An `affix` now hands out its **hole** — the same-sized gap §1 already requires,
which travels with the document precisely because it never moves itself. It is a
door and not a policy: `Affix` forwards two rectangles and holds no controller,
and the caller decides whether a section wants showing. The showcase's
`SectionHeader` went back to being a plain node, which is the proof the door is
in the right place. Neither `AffixTest`'s eleven cases nor `ScrollingScreenTest`'s
four could have caught it — the failing sequence is *scroll away, then ask to come
back*, and every test asked to go somewhere new.

**A second invented token.** `--gb-on-accent` this time, on the tour's forward
button: used, defined nowhere, dropped every frame with a warning. A primary
button's foreground is not derivable from the accent — it is `nord0` on dark and
`nord6` on light, because the fill flips which is legible — so it takes
`--gb-button-primary-text` like every other primary button. Twice in two days is
a pattern rather than an accident, so `TokenClosureTest` and
`ShowcaseTokensTest` now check that every `var(--gb-…)` the toolkit or the
showcase writes resolves under both themes. Verified by breaking one on purpose.

**`flex-grow` on the wrong box.** Five gallery screens carried it inside the
gallery's viewport, where a content-sized column means there is no remaining
height to claim, and one screen carried it where it was load-bearing. A dead
declaration sitting next to a live one is how it stops looking dead. The growth
is the `scroll` box's.

- **A jar binds at run time; an image is woven.** ADR-0125's weaving was
  *mandatory*: every module keeping a model applied `goldberry.weave`, and `Models`
  threw at the first sight of an unwoven class. The weaving was cheap and the
  mandatory was not — a consumer had to install a class post-processor before the
  first field notified, Maven had no Mojo to install it with, and an IDE's green Run
  button did not run it at all. So the weaver became what it is actually for: the
  **native image** path. An ordinary jar reads the same annotations reflectively —
  a `VarHandle` per `@Bind` field, a `MethodHandle` per `@Action` — and `Models`
  picks between the two forms, which answer identically. The one thing reflection
  cannot do is see the write, so a **sweep** compares each field against what it
  last held: after every action a document dispatches (across every model, because
  an `@Actions` record writes to the model beside it), at the top of every frame
  over the models `Application.models()` named, and wherever `Models.refresh` is
  called. The showcase needed exactly one of those calls — a background job's
  continuation — and it is the one visible cost. `RuntimeAgreesWithWovenTest` is
  what keeps the arrangement honest: the same model class raw and woven, driven
  through the same actions, asserted to publish the same paths, names, values,
  notifications and frame requests. **The catalog half did not move**: `@Markup`
  widgets have no runtime equivalent — finding them means scanning the path, which
  is what a `provides` exists to avoid — so `WeaverMain` grew `--models` and
  `--catalog`, and the catalog stays hung off `classes` for every build while the
  models wait for `-Pgoldberry.nativeImage=true`. The costs are written down rather
  than discovered: a model in a named module has to `opens` its package to the
  toolkit, the registry listing order differs between the two forms because
  `getDeclaredMethods` promises none, and an image now carries annotation metadata
  it does not read. **And measured rather than asserted**: `BindingSchemeBenchmark`
  runs both forms of one model class in one JVM, and a press one widget is
  watching costs 15 ns woven against 45 ns reflective, a read 1.3 ns against 10 ns,
  and a document reload the same either way (570 ns against 630 ns). The sweep
  scales at 14 ns per attached model and 9 ns per bound field per press — so ten
  models cost a button 140 ns against a 16 ms frame, and the slope is the thing to
  watch rather than the base.

  **Generating the binding at run time was priced and rejected, and the pricing
  paid for itself.** `BindingCodegenBenchmark` builds the option for real — a
  hidden class defined as a nestmate of the model, reading its private fields with
  a plain `getfield` — and it does the sweep's read-and-compare in 0.60 ns against
  the boxed reflective 15.7. But asking the *same* `VarHandle` for an `int` and
  comparing two `long`s gets 5.2 ns with no new mechanism, which said most of the
  measured cost was this implementation's own plumbing rather than reflection. It
  was: replacing the boxed comparison with one small class per primitive kind, the
  `List` walks with arrays, and a `List.copyOf` per action dispatch with a snapshot
  rebuilt on change took a press from 107 ns to 45 and a read from 32 ns to 10 —
  **no new mechanism, no new semantics, and not one test changed**. What codegen
  would still buy is ~4 ns a field, against making `java.lang.classfile` and
  `defineHiddenClass` reachable from the module every image is built from — and
  against the fact that it would make the sweep fast without making it
  unnecessary, which the weaver already does, one flag away
  ([ADR-0155](adr/0155-a-jar-binds-at-run-time-an-image-is-woven.md))

- **The showcase can be asked for a native image, and the metadata is traced.**
  ADR-0127 claimed the binding layer was no longer the reason an image could not
  be attempted; `:example:nativeImage` is the attempt. The obstacle was never the
  binding — it is `:natives`, where a binding class takes a `SymbolLookup`
  obtained at run time and builds its handles from it, so none of the 184
  descriptors is a build-time constant a closed world could fold, and Yoga's
  measure callback is an upcall besides. Writing them out by hand would be 184
  registrations duplicating what the binding classes already say, going stale
  silently. So `:example:nativeImageMetadata` runs the showcase headless under
  GraalVM's tracing agent and writes what it saw into `src/main/resources` —
  source, because it is reviewed in a diff and packaged into the jar — and
  `nativeImage` builds over that, after `weaveModels`, which the build orders
  before `jar` so an image can never be made from classes bound reflectively.
  **The image builds and runs** on linux-x64 against GraalVM CE 25.2.4: 30.6 MiB,
  ~0.55 s to start, exit 0, with the FFM downcalls, both
  upcalls, the fonts, the icons, the stylesheets, the KDL and the `WidgetCatalog`
  service all surviving the closed world. Neither task is in CI and neither is
  wired into `build`; both fail with a download link when asked. Two things the
  first real run taught: the agent found **two** upcalls where this was written
  expecting one — `SdlEventWatch.invoke` beside Yoga's measure callback — and
  collapsed 184 exported symbols into 55 distinct downcall descriptors, which is
  the tracing decision arguing for itself on its first outing; and the linker needs
  `zlib1g-dev` rather than the `zlib1g` a desktop already has, or a minute of
  analysis ends in `cannot find -lz`. **And it logs**, which took one hand-written
  metadata entry and taught the sharpest lesson of the exercise: the agent records
  *how a lookup was made, not where the file will be*. Logback asks a
  `ClassLoader` for `logback.xml`, so the agent files it as a classpath resource —
  but the image runs on the module path, where that file is at the root of a named
  module and, registered without one, is simply absent. Logback with no
  configuration ends with no appenders and prints nothing, not even its own status,
  so a perfectly working image looked like a dead one. There is a second metadata
  directory now, `goldberry-example-manual`, for what a human writes; the traced
  one is never edited because the next trace overwrites it
  ([ADR-0156](adr/0156-the-image-s-metadata-is-traced-not-written.md),
  [the native-image page](native.md))

- **A layer is blitted into its own size, and every disabled control on a Mac
  stopped being twice as big.** Reported from a 2x display. `opacity` is the only
  thing the stylesheets set on a disabled control (ADR-0077) and `opacity` is what
  promotes a subtree to a layer, so "disabled controls are huge" was "promoted
  subtrees are huge". A layer's raster is allocated in **physical** pixels — a
  raster kept at logical size on a HiDPI screen throws the detail away — while the
  frame it composites onto is in **logical** ones, and `bl_context_blit_image_d`
  draws one image pixel per context unit. At 1x the two spaces coincide and the
  arithmetic is right by accident; at 2x a 60-point square is a 120x120 raster
  drawn across 120 logical units. The composite now states its destination
  rectangle, derived from the raster rather than from the laid-out bounds so that a
  fractional scale's rounded-up raster still lands one-for-one on the device.
  **Every golden passed unchanged**, which is what says this is a fix and not a
  rendering change. The lasting part is `LayerTest`'s new `Scaled` nest at 2x and
  1.5x — and the gap it exposes, which is not closed: almost every pixel assertion
  in this repository is at 1x, where this whole class of bug is invisible
  ([ADR-0157](adr/0157-a-layer-is-blitted-into-its-own-size.md))

- **A full repaint is a full upload, and the resize stopped flickering black.**
  Reported from a Mac: dragging a window edge flickered with black areas. Two
  mechanisms, each correct alone. ADR-0072 asks the backend whether the lent
  buffer still holds the last frame; a resize reallocates it, so the answer is no
  and the painter repaints everything. ADR-0046's damage list is a different
  question — which regions *changed* — and the frame loop reported it either way.
  So the frame after a resize was painted in full and uploaded in part, onto a
  surface that was entirely new: everything outside the damage rectangles was
  whatever the compositor had there. At a steady size the two questions have the
  same answer, which is why every damage test, every golden and every headless run
  missed it. The clamp lives in `Window` rather than at the call site, because a
  painter reporting what changed has nothing to do differently and the window is
  the only thing that knows whether the buffer had valid contents. Both halves are
  now pinned — a frame after a resize uploads the whole window, a frame at a
  steady size uploads only what changed, and the second is what stops this being
  "fixed" by uploading everything always. **Not confirmed on the reporting
  machine**: the cause is reproduced headlessly on Linux and is unambiguous, but
  whether it is the whole of what a Mac shows during a live resize — which macOS
  drives from inside a modal run loop — is not something this repository can
  answer yet
  ([ADR-0158](adr/0158-a-full-repaint-is-a-full-upload.md))

- **The native image is one file, and getting there found two bugs that were not
  about images.** It shipped as a binary plus `lib/libgoldberry.so` plus a
  launcher setting `-Dgoldberry.native.library`, so running the binary on its own
  failed — which is what a person does, because an image is supposed to be a
  program. **Statically linking the archives in does not work**, and the failure
  is not where it looks: linking is fine (`-Wl,-u,<symbol>` pulls the code in,
  given the `-lstdc++` and `-lm` native-image does not pass), but Goldberry
  resolves every native function *by name at run time*, so the symbols must reach
  the dynamic symbol table — and `native-image` links with its own
  `--version-script` making everything unlisted `local`. `--export-dynamic-symbol`
  does not beat it and a second version script is refused outright. So the image
  **carries** the library instead: the classifier jar's resource, embedded, and
  unpacked on first use by the branch `NativeLibrary` already had. One 41 MiB
  file, ~5 ms and a writable temp directory the cost. On the way: a **named module
  cannot see a class-path resource**, so the classifier jar — the mechanism a
  released application is meant to use — could never have worked on the module
  path, invisible because every module-path run here points at a local library
  instead; and **`deleteOnExit` drains in reverse**, so the unpacked library's
  directory was attempted before the file in it and every run leaked an empty
  directory, on the JVM as much as in an image. Neither has a test and both live
  where this repository does not look — what caught them was building the artifact
  and running it with nothing beside it
  ([ADR-0159](adr/0159-a-native-image-carries-its-own-library.md))

- **A module's own resources are declared, not traced — after the image crashed
  on the theme toggle.** ADR-0156 wrote the cost of tracing down before it was
  paid: "a screen the run never reaches contributes nothing, and the symptom is an
  image that starts and then dies opening a menu". It was paid on the first real
  use — `the NORD_LIGHT theme is missing from the jar` — and three more had the
  same shape: `density-compact.css`, `JetBrainsMono.ttf` and `OpenMoji-black.ttf`,
  each the far side of a control the 120-frame run never touched. Tracing harder
  would have caught those four and told us nothing about the fifth: a trace can be
  made longer, not complete. So `:core`, `:widgets` and `:example` each ship a
  `reachability-metadata.json` declaring their own files **by glob** — a set that
  is finite and known at build time, where a directory listing cannot be one
  screen short. Each module ships its own because `native-image` reads
  `META-INF/native-image/**` from every jar on the path, so an application
  building an image gets the toolkit's resources without knowing it needs them,
  rather than discovering that Goldberry has two themes by shipping a crash. The
  image went 41 MiB to 43 MiB, which is the two fonts that were missing all along.
  The FFM and reflection metadata are still traced and ADR-0156's warning still
  applies to them
  ([ADR-0160](adr/0160-a-modules-own-resources-are-declared-not-traced.md))

- **A downcall handle is a constant, or it is not a call — and the image went
  from 42 ms a frame to 1.0 ms.** The `hud` on the first properly exercised image
  read `paint 37.5 / 41 / 53 ms`, `raster 34.7 / 36 / 52 ms`, against a 16.7 ms
  budget: two and a half frames of work per frame, almost all of it Blend2D. The
  cause is a **known and open** GraalVM limitation —
  [#8113](https://github.com/oracle/graal/issues/8113) has "improve downcall
  performance (currently always unoptimized)" on its unfinished list — and a
  GraalVM engineer's answer to somebody else's SDL application dropping from 400
  fps to 25 gives the workaround. Measured here before anything was changed: one
  trivial call costs **10 ns on the JVM and 4560 ns in an image**, and an unbound
  handle built at run time is just as slow, so both halves of the workaround are
  load-bearing. A `MethodHandle` is a call only when the compiler can see *which*
  handle it is; the JIT gets there by watching the field, and an image has no
  second chance. A handle bound to an address never can be — the address does not
  exist until `libgoldberry` is `dlopen`ed. So `Downcalls` holds one **unbound**
  handle per signature (134 bindings share 56 of them), each binding keeps the
  `MemorySegment` it looked up, and `:natives` ships the
  `native-image.properties` that initializes that class in the builder — beside
  the `--initialize-at-run-time=…NativeLibrary` that moved there from
  `example/build.gradle`, since both are facts about the module rather than about
  an application. Sixty frames headless: **2.533 s before, 0.061 s after**, with a
  control build — the same code, properties file moved aside — reproducing 2.55 s
  exactly, which is what makes the gain attributable. The image is now faster than
  the JVM over a short run, because the JVM spends its first frames compiling and
  an image has nothing to compile. The JVM loses nothing (9.81 ns bound against
  9.27 ns unbound), and two `invokeWithArguments(Object...)` paths in `SdlVideo`
  and `SdlCursors` that boxed every argument became `invokeExact` on the way past.
  The same trap sits one level down and decided the naming: **a handle has to be
  read by the method that calls it.** A constant passed *into* a three-line helper
  costs 810 ns in an image against 8.9 ns when the helper names it itself, so the
  constants are named for signatures — `INT__PTR_PTR_INT`, in C's words rather
  than JVM descriptor letters — and not for functions, which would mean deleting
  every shape-generic helper and inlining it at ~100 call sites.
  **Nothing fails if the flag goes missing** — the image builds, runs, paints
  correctly and is forty times slower — so `DowncallsTest` pins the naming scheme,
  `DowncallBenchmark` prints both numbers, and the control is written down
  ([ADR-0161](adr/0161-a-downcall-handle-is-a-constant-or-it-is-not-a-call.md),
  [the native-image page](native.md))

- **`-Dgoldberry.trace.frames=all` printed nothing at all.** Found while measuring
  the above. `ENABLED` was `Boolean.getBoolean`, which is false for anything but
  `true`, while `ALL_FRAMES` looked for `all` — so the setting that asks for *more*
  output turned tracing off entirely and every counter guarded by `ENABLED` was
  skipped. `all` now implies `true`, and both readings are pure functions of the
  property value so that `FrameTraceFlagsTest` can check them without setting it —
  which is the only way to test a flag read once into a `static final` field
  (ADR-0101)

- **Every golden is now three goldens, and none of them is committed.** ADR-0157
  fixed a layer composited at twice its size on a 2x display and wrote down what it
  had not fixed: 37 of the 39 `assertMatches` calls in the repository were at 1.0,
  where the multiplication between logical units and device pixels is the identity
  and a conversion done twice, not at all, or in the wrong space draws exactly the
  right picture. A golden that matches is now **drawn again at 2x and 1.5x its own
  scale**, into a frame of the same *logical* size, area-resampled back down and
  compared — so the claim being checked is invariance rather than "this is the 2x
  render", which is what makes it worth a hundred more PNGs of nobody's review
  attention. The comparison lets a pixel find its match anywhere in the 3x3
  neighbourhood around it, because the two things that differ honestly between
  scales — an edge Yoga rounded onto a different device pixel, and a glyph
  antialiased at a different resolution — are both sub-pixel, and a subtree at twice
  its size is not. It runs in **both directions**: "every pixel of the reference is
  still near where it was" says nothing about something that *grew*, since the
  reference's ink is all still there with more around it. `ClipTest`,
  `TransformPaintTest` and `IconPaintTest` get the same check with no golden behind
  it, which is where the arithmetic actually lives. **Nothing new was found** — the
  whole corpus passed at both scales on the first run, 2215 tests green — and saying
  otherwise would misrepresent what this bought: ADR-0157's bug was already fixed,
  and this is what stops the next one being invisible for a year. The cost is about
  17 ms a golden (88 images, 2.16 s to 3.64 s). What keeps it honest is that its
  failure path runs: `ScaleInvarianceTest` rebuilds ADR-0157's bug on purpose — a
  rectangle sized in physical pixels and drawn in logical ones — and asserts it is
  rejected, and does it again for a border thickened by the scale, which is the
  subtle end of the family and only a stroke wide
  ([ADR-0162](adr/0162-a-golden-is-checked-at-every-scale.md))

### `menubar`, and the accelerator that was waiting on it

- **The model that had to outlive one opening turned out to be the one the author
  already wrote.** ADR-0106 left two things unbuilt with one sentence explaining
  both — §8's in-window bar, and the half of an accelerator that is
  *registration* rather than display — because "a menu is built when it opens and
  thrown away when it closes". That is true of the **popup**. A `Menu` is a
  `record`: an ordinary value, and `Menus.open` builds a *second* tree from it to
  put in a window. Nothing was ever holding the description, which is a different
  complaint, and the fix for it is a widget that does. So `menubar` holds its
  menus, `Accelerators` walks them, and `Ctrl+O` runs the command a submenu three
  levels down names **with nothing at all on screen** — which is the central test,
  written that way deliberately, because anything that opened a menu first would
  be testing the part that already worked.
- **No markup was added.** A bar's children are `item`s, and an `item` containing
  `item`s is a heading that opens a menu — the nesting that has been the submenu
  syntax since ADR-0106. The showcase's bar is written entirely in KDL beside the
  buttons that were already there, wired to the same actions, and its
  accelerators are live: `Ctrl+K` clicks the counter with the bar shut.
- **A heading is not a menu row, and the keyboard is why.** `Down` opens where an
  `Item` moves; `Right` moves where an `Item` opens. Neither arrow is the widget's
  — `menubar` is a **horizontal** focus scope where `menu` is a vertical one
  (ADR-0078), so traversal is free and the two arrows left over are the two a bar
  wants. A heading also has none of the three things that make a row a row: no
  tick column, no accelerator on the right, no chevron. Hovering a heading opens
  it **only when a menu is already down**, which is what every desktop bar does;
  hovering with nothing open would drop a menu on somebody crossing the bar on
  the way elsewhere.
- **`F10` and a bare `Alt`, and the difference between them is in the type.** §8
  asks for "`Alt`-style keyboard activation". A bare `Alt` is a *modifier released
  with nothing in between*, and a `Shortcut` here is a key plus modifiers — `Key`
  has no `ALT` to name, because `Shortcut`'s own constructor refuses one that can
  never fire. So the `Alt` half is not an accelerator at all but a **gesture**,
  recognised at the window from the raw keycode
  ([ADR-0223](adr/0223-a-tap-is-a-gesture-and-a-shortcut-is-a-value.md)); `F10`
  is the companion binding on every platform that has the `Alt` one, and the one
  that survives a compositor which eats `Alt` for its own window switcher. Both
  **open** the first heading rather than focusing it, because there is no
  `Host.focus` and a binding that did nothing visible would read as broken rather
  than as missing — and both **close** an open bar, which is what every desktop
  does with the same key.
- **Three costs, written down rather than discovered.** `Host` grew
  `removeShortcut`, and the map is keyed by the shortcut and not by who bound it —
  so a bar going away takes whatever is on `Ctrl+O` with it, including a binding
  made afterwards; a collision inside one bar is logged and the later row wins;
  and an accelerator that does not **parse** is logged and skipped rather than
  thrown, because it is a typo already drawn beside the row where somebody can
  see it.
- **Adding two methods to `Host` found a third hand-written stub.** `SelectTest`
  and `TourTest` each carried a near-identical one, so the interface change would
  have meant editing both and writing a third. `TestHost` is the shared one, both
  extend it, and — like the real thing under SDL's `dummy` driver — it **opens
  nothing**, which is the branch ADR-0102 says a control has to survive.
- **The first widget drawn through ADR-0162.** Six goldens, each checked at 2x and
  1.5x on the day it was written rather than after somebody reports a HiDPI bug.
  And the images earned their keep immediately: `.open` started as
  `--gb-overlay-active` against `:hover`'s `--gb-overlay-hover` — 16% against 8% —
  and the picture said the two are hard to tell apart, which is precisely the
  comparison a bar puts in front of somebody, since the hovered heading is usually
  the one *next to* the open one. It is the accent fill now
  ([ADR-0163](adr/0163-a-menu-bar-owns-its-menus.md))
- **Two things §8 asks for are still not built and neither is a bar problem**: a
  bare `Alt` tap, and `Left`/`Right` moving *between* menus while one is down —
  the open menu is a window of its own with its own focus, so the bar never sees
  the arrow, which is the same missing item-to-popup callback that has kept `Left`
  from closing a submenu since ADR-0112.

### Five of §5's seven containers

- **`card`, `group-box`, `statistic`, `skeleton` and `collapse` ship**, and three
  of them ran straight into a limit worth writing down rather than rediscovering.
  **A card's elevation is an edge**: §10's subset has no `box-shadow` and nothing
  in this toolkit paints outside a box's own rectangle, so a card is raised by
  contrast — `--gb-surface-2` against the page, plus a border — which is the
  answer `popover` reached first and is the honest version of the same idea, since
  contrast is what a rasterizer with no shadow pass can express. **A group box's
  title is above the frame, not through it**: a legend that breaks a border needs
  a notch the subset cannot express, or the page's own background painted behind
  the words, which is wrong the moment the box sits on anything but the page — and
  a heading above a frame wraps at a small width where a legend through one breaks
  it. **A closed `collapse` describes no body at all** — not a hidden one, not one
  of zero height — so nothing is mounted and nothing subscribed, which is §5's
  own reasoning and ADR-0004's; the test for it is therefore an assertion about an
  absence, including that the author's own widgets were never built.
- **The one loop in the canon is a function of the clock, not a transition.** §1.7
  rule 4 lets a `skeleton` shimmer and nothing else, and a transition runs between
  two states where a skeleton has one — so the pulse is computed from
  `nowMillis()`, which is `spinner`'s arrangement (ADR-0081) and is why a column of
  placeholders is in step by construction. A **triangle wave** folding at the
  halfway point, so the two ends meet and it does not snap once a second, between
  0.45 and 1.0 rather than 0 and 1: a placeholder that fades to nothing flickers
  the layout empty, and one at full strength is indistinguishable from content.
  Reduced motion holds it at its **dimmest**, because a placeholder frozen bright
  reads as content that arrived and was blank.
- **Two things the tests found rather than assumed.** A record component **cannot
  be called `children` when `children()` is overridden**: `GroupBox` described its
  parts from that method, which is also the accessor for the author's widgets, so
  asking a group box what was in it returned its own chrome — caught by inflating
  one node and being told it had two, and the component is `content` now. And the
  **skeleton goldens could never have matched**: a widget drawing from the frame
  clock renders differently every run, so they need `Clock.virtual()` the way
  `ProgressGoldenTest` already did. The instant is pinned at the fold, 500 ms; 250
  was the first guess and is a quarter of the way in rather than the peak.
- **`statistic` never formats and never infers.** §5's reason for a string is that
  a locale-aware number formatted inside the toolkit makes a golden that cannot be
  reproduced on another machine. And `direction` names the **sentiment** rather
  than the arithmetic — latency falling is success — so the caller picks it; a
  widget that read the leading `-` would colour a latency improvement red. The
  colour itself is a class on the delta, so it stays the stylesheet's
  ([ADR-0164](adr/0164-elevation-is-an-edge-and-a-closed-section-is-absent.md))
- **`statistic`'s sparkline waits on `canvas`**, and `collapse`'s `accordion=` is
  a rule about siblings and therefore the containing `column`'s.

### `split-pane` and `carousel`, and §5 is complete

- **A divider translates; it does not track.** ADR-0164 said neither of these had
  a design question left in it. Each had exactly one, and neither was the
  predictable one. A slider reads its value straight off the pointer because the
  value *is* a position along a track — a divider cannot, because the pointer is
  somewhere inside a six-point bar and mapping that to a fraction snaps the
  divider so its centre jumps under the finger on every press. So it is
  **`knob`'s** arrangement instead: the divider reports its offset as a
  `gestureAnchor` and the new offset is `anchor + dragX`. This is the second
  widget to want an anchor **for a reason that is not the knob's** — a knob needs
  one because its value has already moved by the second frame — which is the
  useful thing it says: the mechanism generalises past the case it was built for.
- **The position is a fraction and the minimums are pixels**, and they have to be
  different kinds of thing. A divider a third of the way across stays a third of
  the way across when the window widens, which a stored pixel offset gets wrong;
  but "this list needs 160 points or its labels wrap" is a fact about content, and
  a fractional minimum would let a narrow window squeeze it to nothing. The clamp
  between them needs the measured length, which is ADR-0117's channel. And the
  first pane is **sized** while the second grows, because `flex-grow` shares out
  the space *left over* after content — two proportional panes would land wherever
  their content put them and ignore the divider's fraction entirely, and §10's
  subset has no `flex-basis` to say it with instead.
- **A rotation has three brakes and only two of them work.** §5 makes `interval`
  default to off and asks for a pause on hover, on focus anywhere inside, and
  under reduced motion — §1.7 rule 4's canonical violation being a carousel that
  moves while being read. Hover and reduced motion are complete. **Focus is not**:
  the strip and the carousel's own controls pause it, and focus on a widget
  *inside a slide* does not, because the cascade has no `:focus-within` and
  nothing tells a widget that focus landed in its subtree. That is a real gap —
  somebody who has tabbed into a slide is exactly somebody reading it — and it is
  in TODO.md rather than papered over.
- **One one-shot timer, rescheduled**, rather than a repeating one: a pause is
  then a timer *not scheduled*, and needs no second mechanism to suspend. Every
  reason to stop is re-checked **when the timer fires**, because one already in
  flight when the pointer arrives would otherwise advance a slide past the moment
  it should have stopped. And a build found a wasted wakeup: at the last slide of
  a non-looping carousel, `build` scheduled a timer that would fire, move nothing
  and stop — fixed by folding "is there anywhere to go" into the same predicate as
  the three brakes, rather than testing it at the reschedule where the copy in
  `build` was missing.
- **Two small costs, both stated.** `EventLoop.Timer`'s constructor is
  package-private rather than private so `TestTimers` can hand one to a stub
  `Host` — `TestFrames` has the same privilege over `Frame`, for the same reason —
  and the divider's thickness is written in `SplitPaneView` *and* in
  `controls.css`, because the first pane's size is computed against it and a
  stylesheet that disagreed would put the second pane's edge out silently.
  `SplitPaneTest` pins them together.
- **The showcase's Panels screen demonstrates all seven**, still with no Java
  behind it: a `split-pane` and a `carousel` that keep their own state need no
  more wiring than a `card` does
  ([ADR-0165](adr/0165-a-divider-translates-and-a-rotation-has-three-brakes.md))

### Five reports from looking at it, and two of them were decisions being wrong

- **`panel` had no stylesheet rule at all**, and §5 has always said it is a
  "plain surface: `--gb-surface`, border, radius tokens". The widget drew nothing,
  so every document that wanted a surface invented one — and the showcase invented
  one that looked exactly like a `card`, which is how it surfaced: "in black theme
  I do not see any visual differences between panel and card". A building block
  that draws nothing is not a building block.
- **`--gb-surface-2` was never an elevation.** ADR-0164 said "elevation is an
  edge" and then hedged by also stepping the fill, which is wrong twice: eight
  levels on the Nord dark ramp is not an elevation anybody can see, and on the
  **light** theme `--gb-surface-2` is a step *down* from `--gb-surface` — which is
  `#ffffff` there — so a card built on it read as **recessed**. The token means
  "the second surface" and never promised otherwise. There are two tokens now that
  say what is meant: `--gb-surface-raised` and `--gb-border-strong`, the second an
  **alpha over whatever is underneath**, which is the only way to say "lighter
  than its own surface" in a subset with no colour functions — so it lightens on
  dark, darkens on light, and stays right on a card sitting on a page, on a panel
  or on another card.
- **A `group-box` holds its title now**, and the report was the right question to
  ask of the old one: "what is the purpose of group box? I thought I should group
  elements with title and border." ADR-0164 put the title *above* the frame,
  because a `fieldset`'s legend through a border needs a notch the subset cannot
  express. The premise still holds and the conclusion did not: a heading floating
  over a bordered box is a heading and a `panel`, nothing about it says the two
  belong together, and an untitled one was indistinguishable from a card — so the
  widget had no purpose two existing widgets did not already serve. The border
  goes round both now, with the title as a tinted header row inside it.
- **`carousel` and `collapse` animate, and `TabPhase` became `Phase`.** It was
  written for `tabs` and had nothing tab-shaped in it. Both new arrivals are
  **arrival only** and both for the widget's own reason: a carousel builds only
  the current slide, so holding the outgoing one alive for a cross-fade would be
  building a slide that has been moved away from; and a `collapse` unmounts its
  body, so holding it for 160 ms after it was asked to go is the thing the widget
  exists not to do. Closing is instant and opening is not — asymmetric on purpose,
  because the thing worth animating is content appearing where there was none.
  Opacity and a small translation, never height.
- **`accordion=#true` inflates to a widget.** §5 puts the flag on the containing
  `column` and is right to, since "one open at a time" is a rule about *siblings*.
  But `column` is the most-used container in the toolkit and statefulness is a
  property of the type rather than of the instance — so honouring the flag there
  would give every column in every document a `State` it never uses. It inflates
  to an `Accordion` instead, which reports `column` as its own CSS type: the
  document writes what §5 says, a stylesheet still sees a column, and an ordinary
  column pays nothing.
- **And chrome does not shrink.** A title bar half its height on one screen, which
  is Yoga's default: children shrink, so a window whose content asks for more
  height than there is takes it out of whatever will give — and a bar with a
  definite height is the most willing thing in the tree. If the content does not
  fit, the content is what scrolls
  ([ADR-0166](adr/0166-a-raised-thing-is-told-apart-by-its-edge.md))

### §4 opens with `text-input`, and two things underneath it did not exist

- **A field owns its caret and the model is told.** Every control before this one
  has a state the application can hold — one bit, one number, one key. A field's
  is a caret, a selection, an undo stack and a scroll offset as well as its text,
  so the edit lives in the element and each value goes up through `change=`. A
  `bind=` value is the initial text *and* an override, which needs two tests
  rather than one: the value must differ from what the field holds — or an
  application's own `change` handler would reset the caret to the end on every
  letter — **and** it must have changed since the last build, or an unbound
  field's constant `value=` would overwrite whatever had been typed. It also has
  to be in `build` rather than in `didUpdateWidget`, because a binding firing does
  not replace the widget.
- **The editing rules are a value, and forty-five tests need no window.**
  `TextEdit` is `(text, anchor, caret)` and every operation returns a new one — so
  undo is a stack of *states* rather than a log of inverse operations, and nothing
  has to know how to reverse a word delete. A run of keystrokes is one `Ctrl+Z` by
  one comparison, *does this change start where the last one ended*, from which "a
  caret move breaks the run", "a click breaks it" and "a value from the model
  breaks it" all fall out with no rule written for any of them. Editors that
  coalesce on a timer split the undo when you pause mid-word; this cannot.
- **The field names intents; it does not build edits** — and the reason is the bug
  the first version had. A `password` draws bullets, so the caret and selection it
  draws are offsets into *those*, and a field applying `edit.backspace()` to what
  it was drawing deleted a bullet and left the password a row of them. The seam
  passes `move(LEFT, byWord, extend)` instead, and the one rule a masked field has
  lives in one place: a row of bullets has no words, so `Ctrl+Left` goes to the
  start rather than stepping by an amount that says how long they are.
- **A caret blinks on a timer, not on the frame clock.** `isAnimating` asks for a
  frame every frame, which is right for a spinner and wrong by two orders of
  magnitude for something that changes twice a second: a focused field would run
  the loop at the display's rate for as long as a form was open, and §1.7's idle
  loop would be false for every window with one in it. One one-shot timer,
  rescheduled — `carousel`'s arrangement — at two frames a second.
- **`SDL_StartTextInput` was never called.** It was not on the export list, so on
  a real SDL window the `TEXT_INPUT` event had never once arrived — `SdlEventBuffer`
  could read it, `Window` routed it and `KeyboardTest` exercised it, all against
  the headless backend. It is per window and off by default because asking is what
  raises an on-screen keyboard, so it follows focus rather than the window.
- **The clipboard was the SPI's last named hole**, left out by ADR-0019 until
  something wanted it. Text only, for a stated reason — images and files are a
  transfer negotiation rather than a value — and `SDL_free` is bound beside
  `SDL_GetClipboardText` because that string is the caller's to free with *SDL's*
  allocator. A backend without one reports `Clipboard.none()`; the headless one is
  a real in-memory clipboard, so a copy/paste test tests the widget.
- **The golden found what no unit test asked about.** An absolutely positioned
  child here is placed against the border box while the clip is the padding box,
  so the first character of every field was drawn under the padding and clipped
  away — visible in all six fields of the Forms screen at once, and invisible to
  every test that checked what the field *held*
  ([ADR-0167](adr/0167-a-field-owns-its-caret-and-the-model-is-told.md))

### Six reports from using it, and four were defects

- **A drag selected nothing, ever.** The field guarded its whole pointer handler
  on `button() == PRIMARY`, and `PointerRouter.pointerMoved` builds its event
  with a **null** button — a motion is not a button event. So every drag was
  thrown away before the switch could look at it. The button is the press's
  question now, and what a motion carries is `dragX()`, which is `NaN` when
  nothing is held and is how the router already says "not a drag".
- **The caret was as tall as the control** — an 18-point line with a 32-point
  caret through it, which reads as a terminal cursor. It takes the font's line
  height now, as does the selection highlight, and both are centred by the
  field's own `align-items` exactly as the text is.
- **A field was `--gb-surface-2`, which is still not a direction.** On the light
  theme that is one rung off the page, which is what "the fields are too pale"
  was — and it is the same defect ADR-0166 corrected for `card`, in a new place,
  found the same way. `--gb-surface-sunken` is `--gb-surface-raised`'s opposite
  and is an **alpha over whatever is underneath**, because a fixed value has to
  pick one background to be right against and a field has three. The first
  attempt proved it: `--nord2` on dark is exactly `--gb-surface-raised`, so a
  field on a card vanished into it.
- **The placeholder rule applied and could not be seen.** `--gb-text-muted` is
  two rungs from `--gb-text`, which inside a filled field is not a difference —
  so an empty field looked like a filled one. `--gb-text-placeholder` is its own
  token, and its alpha is set by §1.2 rather than by taste: the first value tried
  was 2.4:1 on light, and the shipping ones are the lowest that clear 4.5:1
  against the worst surface a field sits on, landing at about half a value's
  contrast. `ContrastTest` cannot measure either token — both are translucent,
  which is the trap it keeps `button.ghost` out for — so a test of its own
  composites them explicitly.
- **A limit nobody can see reads as a fault.** The screen's first field has
  `max-length=40`; pasting into it a few times stops taking characters, which is
  exactly what that means and is indistinguishable from a broken field. Clipping
  a paste rather than refusing it stands, and the screen says so now.
- **The fields moved into `card`s**, because a form is a set of groups rather
  than a list of lines — and a card is also what shows a field is a well
  ([ADR-0168](adr/0168-a-field-is-a-well-and-a-drag-is-a-selection.md))

### `field`, `form` and the validation model, and a notification two widgets wanted

- **A field is silent until you leave it, and live from then on.** §4 asks for
  validation "on blur and on submit"; the second half of that sentence is in no
  specification and is what every good form does — once a field has complained it
  re-checks on every keystroke, so the message goes the instant the value is
  fixed. A field that validated as you typed would call an email address invalid
  after the first letter; one that waited for a second blur to forgive you is one
  you have to leave and come back to.
- **Blur is `onFocusWithin`, and it had two consumers before it was written.**
  Nothing told a container that focus had entered or left its subtree. The router
  now walks both chains and tells **only the difference**, so a move between two
  controls inside one field is silent — which is what lets a field with three
  controls behave like a field with one. The second consumer is `carousel`, whose
  third brake ADR-0165 recorded as a gap needing "`:focus-within` in the selector
  engine, the matcher and the router's focus bookkeeping". It needed none of
  that: a carousel does not want to *style* itself on focus-within, it wants to
  be told.
- **A field reads its control's `bind=`**, one level down, so nothing is written
  twice and there is no new channel. A field around an unbound control validates
  nothing — `required` included — because the alternative is failing forever and
  gating a form on a control nobody can satisfy.
- **The fields find the form**, through `BuildContext.findAncestorState`'s first
  consumer since it was written. `TabsState` looked at it and said it "looks the
  wrong way", which was right for tabs and is exactly right here.
- **`:invalid` is a real pseudo-class**, which is the one addition §1's list of
  states asks for by name — unlike `select.open`, which is a class because the
  subset had no word for "expanded" and inventing one for a single widget would
  be inventing a language.
- **A validator returns a message rather than a boolean**, because a field that
  goes red without saying why is one somebody has to guess at, and `and` reports
  the first failure because a message slot is one line.
- **`submit` carries nothing**, and that is a departure from §4's "typed event
  with bound values": `bind=` reads *from* the application's model, so an event
  carrying them would hand an application its own data back — and `binding()` is
  an `Observable` rather than a path, so the toolkit cannot name them anyway. A
  `FormController` submits, because a Save button is usually outside the form.
- **Two reports from looking at a real form, and both were geometry.** The
  validation message appeared *beside* the control rather than under it, and the
  Save button lined up with the labels rather than with the controls. The first
  is structural and the fix says why: §4 asks for a label column and a message
  below **in one sentence**, and those are a row and a column — so a flat field
  of label, control and message can only be one of them. A field is two boxes
  now, `field-label` beside a `field-body` that is always a column. The second
  falls out of the same idea: an action row is a `field` with **no label**, so
  the empty label still occupies the column and nothing has to know how wide it
  is. `align-items: baseline` on the row went too — a baseline is a property of a
  line of text, and asking a *column* for one put two fields on top of each
  other. Both passed every test that existed; `FieldGoldenTest` is the pixel
  coverage they should have had.
- **Two things the screen reported by their absence, and both are closed.**
  Markup can name an object now — `controller=` and `validator=` resolve against
  a fourth registry, `Named`, which exists because the other three each refused
  the job and the **binding** registry refused it in words that settled the
  question: "a value that cannot change is not something to subscribe to". And a
  container can hand focus down: `Handles.delegatesFocus()` turns the router's
  walk round, so a press on a label that finds no focusable ancestor takes the
  first focusable descendant instead
  ([ADR-0169](adr/0169-a-field-is-silent-until-you-leave-it.md),
  [ADR-0170](adr/0170-a-document-names-an-object-and-a-label-hands-focus-down.md))
- **A card has had no edge since the day ADR-0166 gave it one.** The CSS
  shorthand splitter broke on any whitespace, so
  `border: 1px solid rgba(255, 255, 255, 0.2)` became seven fragments and the
  whole declaration was dropped — and `--gb-border-strong` is `rgba(…)` by
  design, because an alpha over whatever is underneath is the only way to say
  "lighter than its own surface" in a subset with no colour functions. The
  warning was printed on every run of the showcase and nothing was reading it.
  It was found by running the application and looking at the log, which is how
  ADR-0166's own defects were found
  ([ADR-0170](adr/0170-a-document-names-an-object-and-a-label-hands-focus-down.md))

### `text-area`, which turned out to be `text-input` and two ideas

- **The model needed two helpers, not a rewrite.** `TextEdit` was written without
  a line in it about how many lines there are, and the editing, the undo history,
  the clipboard and the caret's blink are all `text-input`'s unchanged.
- **A column is an x.** `Up` keeps the column, a column is a position rather than
  an offset, and a run of `Up`/`Down` has to survive a short line in the middle —
  which is why the x is captured once per run and held. It cannot live in the
  model: a `TextEdit` has no font, no width and no layout.
- **A selection is one rectangle per visual line**, because a run of wrapped text
  is not a rectangle. That is why `Paragraph`'s measurements take a *line's*
  range: they were written for this one widget early.
- **The three parts are shared rather than copied**, public in a package the
  module does not export — ADR-0065's "styleable and not constructible" has meant
  package-private only because one widget owned its parts, and JPMS can say the
  real thing when two do.
- **Two things about the render order, both found by looking.** `render` runs
  before Yoga, so a box does not know its width — the first version wrapped at one
  point before anything was measured and put every word on a line of its own,
  which the Forms golden showed at once. And a measurement has to **ask for a
  frame**: `text-input` records its width and requests nothing, because its width
  only decides how far it has scrolled, and a `text-area` doing the same would
  show its first guess until something unrelated repainted
  ([ADR-0171](adr/0171-a-column-is-an-x-and-a-width-arrives-late.md))

### `message`, and the sentence §1.2 had nothing behind it

- **The kind is said twice, which is the whole of the widget.** §7 asks for four
  kinds and says the icon is not decorative — §1.2 forbids colour as the only
  carrier of meaning — so a `kind` sets a glyph *and* a hue. The glyphs are four
  new `Box.Mark` kinds rather than four Lucide icons, for `tab-close`'s reason
  with one more on top: an `Icon` is native memory that must be closed exactly
  once, and a banner is described afresh on every build.
- **The theme's own claim about its hues was untrue, and measuring it is what
  found out.** Both files documented `--gb-danger` as "what a label, an icon or a
  border is drawn in"; this is the first widget that draws one, and five of the
  eight hue/surface pairs are below §1.2's 3:1 floor — the dark theme's danger at
  2.46:1 and the light theme's warning at **1.28:1**. A hue has three ranks now:
  itself, `-fill` for words on top of it, and `-line` for a stroke on the page.
  `ContrastTest` gained a second sweep, so the rule has a check under it rather
  than a sentence.
- **It is stateful and holds one timestamp**, which is what §3's entrance costs:
  a newly mounted element starts no transition, so an arrival is a function of
  the frame clock and needs a beginning. One correction to `collapse` and
  `carousel` came with it — they decide at *build* time whether they are
  animating and nothing rebuilds a banner, so this asks the `Phase` and actually
  goes quiet.
- **The exit works, and the trick is the order.** "A banner goes away because
  the application stopped describing it, so there is nothing left to fade" looked
  airtight and was a false choice: the × starts the fade **in the widget** while
  the description is still in the tree, and tells the application when it is over.
  No owner needed, which is what a lone banner has not got. `Phase` carries a
  duration now, because §3 asks for 160ms in and 100ms out.
- **§4's error summary is drawn at last** — `Message.summary(errors)`, empty when
  nothing is wrong, which is the register [ADR-0169] built and nothing consumed.
- **A gallery image is the second frame now.** The Overlays screen's first
  picture showed four banners at zero opacity, holding their space and drawing
  nothing, because the gallery painted the frame before every arrival starts.
  That is half of the entry ADR-0171 filed under `text-area`.
- **The showcase has a ninth screen, and it is Java on purpose.** Everything
  worth seeing about a banner is a *change* — it arrives, and it goes — and both
  are the application's doing, which is the same fact that keeps `bind=` off the
  widget. So **Notifications** has four buttons that spawn one and a × that
  actually removes it, where a `dismiss=` in a document can only report; the four
  resident banners at the top of it are the four kinds, and §4's summary is under
  them. `NotificationsScreenTest` presses the buttons, because a golden cannot.
- **Four banners in a column touched**, because a `column` has no gap of its own
  and nothing had stacked two blocks with borders before. 12px in the showcase's
  own stylesheet, where every other gap on that screen is — and a note for
  `toast`, which stacks them with no container an author could write
  ([ADR-0175](adr/0175-a-banner-says-its-kind-twice.md))

### `dialog`, and the two mechanisms it was waiting on

- **A modal is two different things and only one of them is code.** The pointer's
  modality is *geometry* — the scrim is a filling overlay, and a filling overlay
  takes every press wherever it draws, which `tour`'s veil discovered and which
  needed nothing new. The keyboard has no position, so its half had to be said
  out loud: `Handles.isModal()`, read by the router as one sentence — **while
  something modal is mounted, the focused node is inside it**.
- **The trap is enforced where focus is *set***, not at the routes that move it.
  The routes are Tab, a press, a roving arrow, a control focusing itself and
  whatever asks next; a trap that covered four of five would be no trap. Nothing
  is registered when a dialog opens, so a dialog removed by any route at all
  gives the keyboard back.
- **`Host.focus(id)` exists**, which three separate TODO entries were waiting on,
  and the rule that makes it useful is the fallback: a node that cannot take
  focus resolves to the first focusable thing *inside* it. A dialog's panel is
  not focusable, so without that the one caller that most needed this could not
  have used it.
- **The roles are values and the order is the theme's.** `Esc`, `Enter` and §7's
  platform button order all need the dialog to know which button is which, so a
  `DialogAction` carries one — and two affirmatives is refused when the dialog is
  built, because `Enter` cannot be a coin toss. The order itself is one CSS
  declaration: the bar writes neutral, dismissive, affirmative, and a Windows
  theme reverses it. A widget that read the operating system to lay itself out
  would be a widget whose goldens differ per machine.
- **Both keys bubble.** `Handles.onKeyCapture`'s own doc says "where a dialog
  swallows Escape", and this dialog deliberately does not: a `text-area` keeps
  `Enter` and an open `select` keeps `Esc` by consuming them, and the control is
  the reason the dialog is open.
- **Closing runs before the application is told**, which is §1.7's overlay
  lifecycle and `message`'s order from the same day — so a handler that removes
  the overlay immediately still gets the fade, and no application writes a line
  about the animation
  ([ADR-0176](adr/0176-a-dialog-is-a-widget-and-showing-one-is-not.md))

### `toast`, and §7 is complete

- **The value is not a widget**, which is the one place in the catalog that is
  true. §7 draws the line itself — a message is part of the layout and a toast is
  "something that just happened" — so a `Toast` is a record raised through a
  controller and there is no node an author can write. A document describes a
  screen; a toast is an event, and a screen that described one would raise it
  again on every reload.
- **The stack is the widget, and holding the list is the point.** Both things
  ADR-0175 filed as impossible for a lone banner fall out of owning a queue: a
  toast can outlive its own dismissal long enough to fade, and something finally
  knows what a notification's siblings are.
- **"Queued" is a cap of three**, which is a judgement §7 does not make: four in
  a corner is a wall nobody reads and one at a time makes a burst take half a
  minute. A departing toast gives up its place immediately, so the stack briefly
  holds four rather than making a burst stutter.
- **The hover-pause is a pause.** `Host.after` gives a timer and no way to ask
  how much of it has run, so resuming needs a clock — and the only clock a widget
  has is the one `render` is handed, which the stack reports back on every frame.
  A toast you glanced at for two seconds gets its remaining three, not another
  five.
- **The corner decides three things** — where the stack sits, which edge a toast
  slides in from, and which end of the column is newest — because they are one
  decision. The last is a CSS rule (`column-reverse` for the top corners) rather
  than a list the widget reverses and then has to reverse again for the keyboard
  ([ADR-0177](adr/0177-a-toast-is-a-queue-and-the-stack-is-the-widget.md))

### A dialog that would not fade, found by being asked for it

- **`isAnimating` answered `!closing && phase.isRunning()`**, so the instant a
  dialog started closing it stopped asking for frames — and a widget nobody asks
  to repaint does not fade: it stood still for 160ms and vanished. Two flags were
  doing one job. `closing` means *input is off* from the moment an answer is
  given (§1.7's "no ghost clicks"); only a second flag, *there is nothing left to
  draw*, may switch the animation off.
- **Every golden passed**, which is the part worth keeping. A golden drives
  `render` by hand and never asks whether the frame loop would have, so the four
  dialog images were pictures of an animation that never ran in a real window.
  The two tests that would have caught it assert on `isAnimating` directly, and
  they exist now.

### The sibling reflow, and §7 is finished

- **Only the older toasts move**, which is not obvious and is not the widget's
  doing: a `toaster` is a corner overlay, so the column is anchored along the
  edge it is against, and `controls.css` puts the newest toast at that end. The
  column is therefore anchored by its *newest* member — so a hole in the middle
  leaves everything between it and the corner exactly where it was, and the older
  half comes in to close it. The corner decides which way, as it already decides
  which edge a toast arrives from.
- **The ordinary case moves nothing**, and that is correct. A stack whose toasts
  share a timeout loses its oldest first, and the oldest has nothing older to
  move. The reflow is what a dismissal *from the middle* looks like — an action
  button, a `clear()`, a burst with uneven timeouts.
- **The translate runs backwards.** Yoga has already put the survivor where it
  belongs; the transform puts it back where it was for one frame and then lets
  go. Same shape as the arrival, and they compose on two axes rather than taking
  turns — a toast can still be sliding in when the one beside it is dismissed.
- **The two numbers are read, not invented.** The height of the hole comes from
  `Measured` and is banked every frame, because by the time it is wanted the
  toast is gone; the gap comes from `toaster { gap }` through the channel
  ADR-0177 opened for the frame clock. A toast dismissed before it was ever
  painted has no height, and that reads as *no hole* rather than a hole of
  nothing — the check is on the height, because the gap alone is a real number.
- **The `AnimationController` §3 names did not appear.** `Phase` already does the
  start and the end; the interruption — a second toast going while the first
  reflow runs — turned out to be three lines of arithmetic rather than a
  mechanism. ADR-0081's finding one level up. The overlay enter/exit sequence is
  the specification's one remaining subject.
- **The golden had to be taken in a real window**, and that is the first decision
  above making itself felt: every other picture in `ToastGoldenTest` is of a
  column on its own, which is top-anchored, so it would photograph the *newer*
  toast moving. Overlay placement is not assertable as a number, which
  `HudGoldenTest` found first
  ([ADR-0178](adr/0178-a-stack-closes-its-own-hole.md))

### What a popup measured, said out loud

- **The measure step was never observable, and `Host` had always claimed it was.**
  Measure, place, open — "and each is separately observable" — but a caller that
  needed to know how big its content came out had no way to ask, and `Placement`
  clamps anything taller than the work area to the near edge with everything below
  it silently dropped.
- **So both callers who needed the number worked around it, differently.** `Menus`
  guessed — rows times an assumed 34px, rounded up so it erred towards wrapping a
  menu that would have fitted — and kept a second copy of
  `--gb-menu-item-height` to do it. `select` did not try at all, so a list with
  more options than the display is tall lost its bottom: the same defect `menu`
  had before ADR-0118, still shipping in the control §3 most expects to be long.
- **`Host.Fit` is the report**, taken between the measure and the place: handed
  what the content measured and the room it has, answering with what to open.
  Returning the content unchanged is the ordinary answer and costs nothing;
  returning anything else costs a second element tree, which is the right way
  round — nearly every popup fits, and only the one that did not pays.
- **The facility asks rather than decides**, for two unchanged reasons: whether
  long content should scroll or be clamped is a fact about the content — a tooltip
  that scrolled would be absurd — and `:core` has no widgets to wrap anything in
  anyway. Reporting in `:core`, policy in `:widgets`, which is the fence the
  modules already draw.
- **One policy now, held once.** `Fitted` is what both callers answer with, beside
  the `Scroll` it builds. The 8px margin came out of `Menus` and was never anything
  to do with menus: a panel flush against both edges of the screen looks cut off
  even when it is not.
- **A twenty-row menu measures 667px where the estimate said 696** — close enough
  that the guess was never wrong on a full-height display, and 29px of menu
  needlessly wrapped on a short one. The end-to-end test opens that menu into
  240px of work area through the real launcher, and fails at 667 without the fix
  ([ADR-0179](adr/0179-a-popup-says-what-it-measured.md))

### The keyboard, given back — and the stale pointer under it

- **`Element.unmount` tells the element tree and nothing else.** The router is not
  a listener, so a dialog closing left `PointerRouter.focused` pointing at an
  element that had left the tree: unmounted, still receiving key events, still
  keeping its whole dead subtree reachable. "Focus is not restored when a modal
  closes" was the half of that anybody could see, and it was on the list; this
  was not, because nothing had looked.
- **So it is two rules, and the first is not about dialogs.** The router never
  holds an element that is not in the tree — a switched tab, a shortened list and
  a closed dialog strand the same pointer — and *then*, if there is somewhere to
  put the keyboard back, it goes there.
- **`refocus()` runs once a frame** from `updateRegions`, beside `notifyMeasured`
  and `notifyLocated`, and is public where they are private: the question is about
  the element tree rather than the painted frame, and a test that closes a dialog
  without drawing anything still needs the answer.
- **One slot, and it is the first state the focus trap has ever held.** TODO was
  right to flag the cost. Everything else about the trap is a question about the
  tree asked fresh — which is why a nested dialog gives the first one back for
  nothing — and this cannot be: what had focus before a modal opened is a fact
  about the past. So it is written at exactly one moment, never overwritten by
  focus moving inside a modal, kept rather than spent when a nested modal closes,
  and allowed to go stale on purpose.
- **The ring goes back with the keyboard**, because §7.2 keeps `:focus` and
  `:focus-visible` apart and restoring one without the other would either lose a
  ring the user was looking at or conjure one under a pointer nobody moved.
- **The two popup entries turned out to be wrong about the cause.** A probe through
  the real launcher — a widget logging every focus change, a menu opened over it
  and closed — recorded no focus loss at all. A popup has its own tree and its own
  router and touches neither of the owner's, so a `select`'s field keeps its focus
  and its ring for as long as the list is up. What may still be missing is the
  *platform's* window focus, which the headless backend cannot show. Those entries
  say that now instead ([ADR-0180](adr/0180-the-keyboard-goes-back-where-it-was.md))

### How small and how large, which three widgets had been writing around

- **§8's subset gained `min-width`, `max-width`, `min-height` and `max-height`**,
  and `dialog` has the two numbers §2 has asked it for since it was specified. It
  was never only about dialogs: `toast`'s 360 is a *width* and `controls.css` says
  outright that it is one "because the subset has no `max-width`", a `tooltip` has
  no maximum and so runs a long one onto a single line, and `popover` takes
  `minimumWidth` as a Java argument doing a declaration's job. Three widgets
  writing a width where they meant a maximum is a missing property rather than
  three choices.
- **One value, not four components.** `Limits`, beside `Insets`. The `Insets`
  argument applies — the four are only meaningful together, and `Box` and
  `ComputedStyle` would each have grown four where they now grow one, across 45
  positional reconstructions. The extra argument is that these are the *same
  question asked four ways*: a caller handling three of them has a bug nobody
  would find, and one value makes that impossible.
- **Undefined, not zero**, because a minimum of zero constrains nothing but a
  maximum of zero is a box that may not exist. "No limit" and "a limit of none"
  cannot share a spelling.
- **The scrim lost its padding across, and that is the whole trick.** §2 wants
  "80% of the window" and a percentage resolves against the containing block — a
  dialog's is the scrim, which fills the window, so 80% means what §2 says only
  once the scrim stops insetting it. Measured rather than assumed: with the 24px
  still there the dialog came out at 330 in a window where §2 permits 339. Down
  the page the padding stays, because nothing else keeps a tall dialog off the
  top and bottom edges.
- **All four dialog goldens changed, and the change is the spec being applied**:
  the dialog wanted 85% of the window and is capped at 80%, so its message wraps.
  That is what a maximum does, and it is the first evidence the property is real.
- **Of the four consumers waiting, one wanted converting.** `tooltip` has a
  maximum now — 320, a judgement rather than a specified number, because without
  one a sentence of help text is a ribbon across the window. The other three were
  not consumers: `toast`'s width is a *design* argument its own note already made
  (the same 360 on every toast is what makes a stack read as a stack, and a
  maximum gives the ragged pile back), `popover`'s `minimumWidth` is a runtime
  measurement no declaration can express, and `text-area`'s max rows is built and
  is a row count.
- **The 24-component record has a test rather than a refactor.** One failure mode
  follows from a positional constructor that long — an argument in the wrong slot,
  compiling and running and wrong in a way no golden shows. `RecordWitherTest`
  asks every wither on `Box` and `ComputedStyle` to set its component to the value
  it already holds and requires an equal record back, which catches a wrong slot,
  a wrong read and a doubled component alike, needs nothing per component, and
  covers whatever is added next the moment its wither exists. Verified by planting
  a `width`/`height` swap the compiler cannot see. The structural answer — group
  the components until no argument list is long enough to get wrong, which is what
  `Insets` and `Limits` already do — would turn `box.width()` into
  `box.layout().width()` across the toolkit for a benefit this already has
  ([ADR-0181](adr/0181-a-box-may-say-how-small-and-how-large.md))

### A set of things, and a way to give one back

- **A toast's plate is its own dismiss affordance.** §7 gives a `message` a × and
  a toast an action button and nothing else, and that was followed exactly — which
  left a `Duration.ZERO` toast with no action removable only by `clear()`. What
  the missing × meant is that a toast does not need a *second* affordance
  competing with its action on a 360×40 plate. The click was already being
  swallowed and doing nothing.
- **`select multiple=` is built**, and `change` is a **toggle** in that mode: the
  set is the application's, so asking for a value it already holds can only mean
  taking it out. One channel keeps a chip's × and a click on a chosen row from
  being two ways of saying one thing. The order is the options' rather than the
  model's, so removing a chip and putting the value back does not move it to the
  end of the row.
- **A popup's content may now change while it is open**, which the toolkit could
  not do: a popup is an element tree with its own build schedule, so a `setState`
  in the widget that opened it reached nothing in the popup's window, and showing
  it something new meant closing and reopening. `ElementTree.update` reconciles
  from the root, so elements, state and focus survive.
- **§4's free-text autocomplete is built.** The field raises the query through
  `change`, the application answers by handing back a list, and choosing reports
  through the same channel — so "the field's text is never rewritten without the
  user choosing" falls out of the shape rather than being enforced.
  `Option.inAList()` is what makes the panel right: arrows move the focus and
  `Enter` commits, where follow-the-focus is a `select`'s behaviour and would
  rewrite the field under a user who is only looking.
- **A field that learns where it is asks for one frame**, and that was a real bug
  rather than a test artifact: a field focused with suggestions in hand offered
  nothing until some unrelated frame rebuilt it, because the rectangle arrives
  after the paint and §1.7's idle loop was never going to ask for another one. The
  rebuild is asked for only on a *change* and only when something is waiting, so
  it settles in one frame
  ([ADR-0182](adr/0182-a-select-may-hold-more-than-one.md))

### A combobox, which is a select you can type in

- **§3's `autocomplete` is built, and the editor is a real `text-input`.** The
  sentence says "makes the closed control an editable `text-input`" and it is
  meant literally: everything an editable field needs — the edit model, the undo
  history, the clipboard, the caret, IME — already lives there and has rules in
  it, and a second editor grown inside `select` would be a second copy of those
  rules with the first drift going unnoticed.
- **One Tab stop.** The field stops being focusable when it holds an editor and
  delegates focus instead, which is `field`'s mechanism for its own reason: the
  thing that takes the press is a *sibling* of the thing that should end up
  focused. Two keys change meaning with it — `Space` types a space, because §3
  lists it as a way to open a *closed* control and a combobox is not one, and a
  click opens rather than toggling, because a click in a combobox is a user
  putting the caret somewhere.
- **One nullable string does all the work.** What the user has typed is handed to
  the `TextInput` as its `value`, and `follow` overwrites the field only when the
  offered value *changes* — so typing is never fought, `Esc` restores by setting
  it back to null, and choosing clears it for the same reason. No new rule was
  needed anywhere.
- **Refusing is a blur-time decision**, because that is when a half-typed value
  stops being an attempt and starts being an answer. Heard through
  `onFocusWithin` rather than `onFocusChanged`, since the thing that has the
  keyboard is the editor *inside* the field.
- **The editor is drawn as the select's interior**, checked rather than assumed:
  left alone it brought a `text-input`'s border, fill, radius and focus ring
  inside the `select`'s own ([ADR-0183](adr/0183-a-combobox-is-a-select-you-can-type-in.md))

### `tree`, and the last of §3's select line

- **`tree` is built in a first cut**, and it had to be: §3's `select tree=` takes
  "a `tree`'s model", and §3 also says a tree shares `list`'s item-factory — but
  `list` is not built either, so the model was defined here and `list` will have
  to agree with it.
- **The id is the whole model.** §3 asks for expansion retained "by node id, not
  by index", so `TreeNode` requires one, the state holds a set of ids, and the row
  uses it as its reconciler key. One decision paying three times: a model
  re-sorted under an open branch leaves it open, and leaves its focus alone.
- **A chevron is drawn before anyone knows what is under it**, which is what makes
  lazy children possible at all — a node that had to know its children to decide
  whether to draw a chevron would make a directory tree stat the whole disk to
  draw its first row. The fetch happens in the toggle rather than in `build`, and
  a branch closed and reopened does not go back to the supplier.
- **The indent is a sized box, not padding**, so the selection highlight still
  reaches the left edge; and a leaf keeps the chevron's box and draws nothing in
  it, so a folder's label and a file's label at one level line up.
- **`Right` on an open row does nothing, deliberately.** Rows are flattened
  depth-first, so the next row *is* the first child — the key falls through
  unconsumed to the vertical scope. `Left` on a closed row is the one that needs
  help and asks the host to focus the parent by name.
- **Not built, and filed**: the checkbox per node with `cascade` and
  `indeterminate`, `*`, type-to-select, multi-selection, `Home`/`End`. And §2's
  chevron `rotate`, which is two marks instead because §8's subset has no
  `transform` on a mark
  ([ADR-0184](adr/0184-a-tree-is-a-list-that-remembers-what-is-open.md))

### Three defects found by running it, and one still open

- **A `multiple`'s chip appeared and the row stayed grey.** The list was
  re-described at the moment of the click, where `widget()` is still the
  description from *before* the application was told — so it drew the selection
  the list already had. The rule worth keeping: after reporting upward, a
  controlled widget knows nothing new until it is rebuilt, and reading `widget()`
  there is reading the past. The refresh moved into `build`.
- **An `autocomplete` took one character and went dead.** The list focused its
  first row on opening and took the keyboard off the editor being typed into. So
  a popup that hangs off a field does not take focus — the arrows still reach it,
  because the owner forwards keys to whatever popup is open, which is a mechanism
  that existed for another reason and turns out to make this safe rather than a
  compromise. It opens on **focus** rather than on the click, because the editor
  consumes the press to place its caret.
- **A `tree` would not open with a mouse.** Nothing handled a click: a row
  selected when selectable, and in a leaf-only tree a parent is not — so a click
  on "Europe" did nothing, and the chevron had no handler either. Every keyboard
  test passed.
- **All three were green in CI**, which is the part worth keeping. Each lives in
  the seam between the widget and a running window, and every test drives the
  widget by hand. `MenusTest` drives the real launcher against the headless
  backend and nothing in §3's select family does — closing that is worth more
  than the three bugs were.
- **The wither check now covers the catalog.** `WidgetWitherTest` walks the
  compiled classes and asks every wither on every widget record to set its
  component to what it already holds; verified by swapping two same-typed
  arguments in `Select.placeholder`. Five widgets refuse the values it invents and
  are named in the failure message rather than skipped quietly.
- ~~**Still open: a popup hangs when the application loses focus to another
  window.**~~ **Closed.** ADR-0144's mechanism was wired and the fault was inside
  it: `anyWindowFocused()` counts popup windows, so a popup holding the platform
  keyboard kept the check true and the dismissal never fired. No popup of any
  kind is focusable now, which costs nothing — the owner has forwarded keys to
  whatever popup is open since ADR-0104, because SDL focuses `POPUP_MENU` windows
  on some drivers and not others
  ([ADR-0185](adr/0185-a-list-that-hangs-off-a-field-does-not-take-the-keyboard.md),
  [ADR-0186](adr/0186-a-panel-that-hangs-off-a-field-is-not-a-menu.md),
  [ADR-0189](adr/0189-no-popup-holds-the-keyboard.md))
- **`SelectLoopTest` drives §3's select family through the real loop**, which the
  three defects above argued for, and it found a seventh on its first run: a click
  opened the list and closed it again in one gesture, because the press focused the
  editor — which opens it — and the click then toggled from a stale `open` flag. An
  editable control opens on **one** signal now, and the signal is focus
  ([ADR-0188](adr/0188-a-control-opens-on-one-signal.md)). What the harness still
  cannot reach is the platform's window flags: reverting `NOT_FOCUSABLE` fails
  nothing, because the headless backend has none.
- ~~**Still open: `flex-wrap` is not in §8's subset.**~~ **It is now**, and the
  chips wrap ([ADR-0192](adr/0192-a-row-of-chips-wraps-and-the-chevron-does-not.md)).
  The gap was in one place — Yoga had the setter bound and the enum written, and
  nothing above the native boundary could say it — so the work was one component
  on `Box`, one on `ComputedStyle`, one parser case and 48 positional
  reconstructions, which is the churn ADR-0181 grouped four properties to avoid.
  **Where** the wrapping goes had to be seen rather than reasoned about: on the
  field it drops the *chevron* onto a second line under the chips, which is a
  worse picture than the shrinking it fixes, and only the golden image said so.
  The chips have a box of their own now — `select-chips`, a part in ADR-0065's
  sense — and a second golden holds five chips in a 220px field, because the
  existing one has three that fit and its javadoc claimed they wrapped.

### `tray-icon`, and the first widening of the export list

- **§9's `tray-icon` is built**, and it is the first thing M3 owed that begins in
  `goldberry.symbols` rather than in a widget. Eleven symbols — nine tray calls,
  plus `SDL_CreateSurfaceFrom` and `SDL_DestroySurface`, which are how a painted
  BGRA buffer becomes an icon — took the list from **192 to 203**, and the five
  `SDL_TRAYENTRY_*` values went into the constant probe with everything else. The
  one that pays for the probe is `DISABLED`: `0x80000000` is a negative `int`,
  and a mask assembled in one is wrong in a way nothing else would have noticed.
  `SDL_UpdateTrays` is deliberately unbound — SDL calls it from its own event
  loop, and this toolkit pumps events.
  ([ADR-0191](adr/0191-a-tray-is-a-menu-somebody-else-draws.md))
- **It is the first entry in the catalog Goldberry does not draw.** A tray menu is
  a GTK menu, an `NSMenu` or a Win32 popup: the shell owns the font, the row
  height, the highlight and the click. So the parity invariant's third clause has
  nothing to attach to, and a `TrayIcon` is a **value** like a `Toast` rather than
  a widget — `Trays.show(host, tray)` is what puts one on the desktop.
- **The menu it holds is an ordinary `Menu`**, which is ADR-0163's finding used a
  second time: what is short-lived about a menu is the popup and not the
  description, and a tray menu is the longest-lived opening there is. An author
  writes one description and shows it in a window, in a context menu, or here.
  What the platform has no vocabulary for is **dropped with a warning** — an
  icon, an accelerator, any widget that is not an `item` or a `separator` —
  because a tray that quietly ignored half a description would be a menu somebody
  kept editing without effect.
- **Absence is reported and no error string is read to decide it.** A popup's
  caller reads SDL's `not supported` to tell a driver's limit from a caller's
  mistake; the tray has no such line, because the Linux path fails with
  `Could not load AppIndicator libraries` — an absence wearing the words of a
  failure. Every null is empty, logged at debug with SDL's own words, and the
  showcase says `tray unavailable on this desktop` and carries on.
- **`HeadlessTray` is the only place a tray menu can be observed at all.** There
  is no golden image of a GTK popup and nothing to hit-test, so `choose("Recent/
  report.pdf")` is the click the shell would have delivered, applied in the
  platform's order: a checkbox toggles **before** its handler runs, because SDL
  applies the click itself and the handler reads the result. A test that toggled
  afterwards would be asserting an order no platform uses.
- **It ran for real**, which for this widget is the only proof available: the
  natives test created a live tray on this machine's session under
  libayatana-appindicator, and the showcase puts one up on start — six rows, a
  submenu among them — and takes it down in `stop`, because a tray left behind is
  a picture in somebody's notification area that the shell will not clean away.
- **Every row but `Quit` did nothing, and that was found by running it.** A tray
  row is the only input in the toolkit that arrives with **no event behind it**:
  it is delivered from inside `SDL_PumpEvents` by way of `SDL_UpdateTrays`, so no
  pointer moved, no key arrived, and nothing asked for a frame. A jar-bound model
  is swept at the top of a frame, so a handler that set the theme set it where
  nobody was looking. `Quit` worked because closing a window is a platform effect
  rather than a model change — which is exactly the shape that makes this look
  like "the tray is broken" rather than "the loop is asleep". `Host.tray` gives
  every row the window's repaint now, and both the value and the widget layer
  assert it. The lesson generalizes: a source of input the frame loop cannot see
  has to say so itself, and this is the first one whose failure was silent.
- **The `libayatana-appindicator is deprecated` warning on Linux is the
  distribution's, not the toolkit's.** SDL's loader tries
  `libayatana-appindicator3.so.1` and `libappindicator3.so.1`; the `-glib`
  successor the message names is not on its list, so silencing it is a change to
  SDL on a pinned commit.
- **Not verified on Windows or macOS.** Those paths are SDL's, are compiled, and
  nobody has looked at them. Said here rather than implied by silence.

### Charts, which start two layers down

- **`canvas` is not built, and charts sit on it.** `content-widgets.md` §3 builds
  the five chart widgets on the `canvas` primitive so they inherit the theme, the
  text stack, hit testing and the golden corpus — and §1's `canvas` was never
  written. It has been blocking something shipped since M2: `statistic`'s
  sparkline is specified and absent for want of it (ADR-0164). So the order is
  `canvas`, the chart substrate, then the widgets.
- **The paint surface gained a state stack**
  ([ADR-0193](adr/0193-a-canvas-is-a-second-clip-depth.md)), which is the first
  thing `canvas` needed and the second widening of the export list in this
  milestone. Every painter inside the toolkit knows what it set and unsets it; an
  application's `onPaint` is not one of those — it runs inside whatever clip the
  tree established, and `resetClip` goes back to the **whole frame** rather than
  to the region before it, so a canvas inside a `scroll` would paint over the
  viewport's edge. `bl_context_save` / `bl_context_restore` are exported now (205
  symbols), `Frame.save()` / `restore()` sit over them, and the nesting is
  asserted rather than assumed. The export list's own comment used to explain why
  the pair was unnecessary; it now explains why both calls exist.
- **The series palette is derived and measured**
  ([ADR-0194](adr/0194-a-series-colour-is-derived-from-nord-not-taken-from-it.md)).
  §3 says "categorical series colors from aurora + frost hues", and the word doing
  the work is *derived*: Nord used literally fails five of the six categorical
  checks — six of eight hues below the chroma floor, so they read as gray and stop
  doing identity work, and `nord9`/`nord8` at ΔE 8.5 because the frost family
  spans 23° of hue and two of its members are 5° apart. What ships is eight slots
  re-stepped from Nord's hue angles, with dark as its own steps rather than a
  flip, passing all six checks in both modes. The **order** was searched over all
  40 320 permutations rather than chosen, because adjacent slots are what touch in
  a stack: Nord's own numbering puts orange beside green at ΔE 0.8 under
  deuteranopia, which is two series nobody can tell apart.
- **The Grafana question is answered in `docs/charts.md` §3** — which of its
  features belong in a desktop toolkit, which are `goldberry-plot`'s, and which
  are dashboard machinery a *toolkit* must not grow (query editors, field
  overrides, auto-refresh, dual y-axes).
- **`canvas` is built** — §1's last unbuilt primitive, and the substrate the five
  chart widgets sit on. A `Painter` is a content slot on `Box` beside `text`,
  `icon` and `mark`, and `paintOne` hands it the frame **translated to the box's
  content corner and clipped to it**, inside the `save`/`restore` pair above. So
  a painter draws in its own coordinates from `(0, 0)`, cannot escape its
  rectangle however wrong its arithmetic is, and may leave the context in any
  state at all — which is what makes it safe to hand an application the toolkit's
  own rasterizer.
- **Three guarantees, asserted in pixels rather than in calls.** A painter that
  fills `(-50, -50, 200, 200)` paints its own 40×40 and nothing else; a painter
  that clips to a 2px sliver, translates and returns leaves the box drawn after
  it whole; a painter that throws propagates its exception *and* restores, so an
  application's bug is a stack trace rather than a window that draws wrong from
  then on. A canvas laid out to nothing is skipped rather than throwing, because
  a collapsed split pane produces one.
- **It draws inside the padding**, which is ADR-0111's rule for text applied to
  the one content that is not text: `canvas { padding: 8px }` is eight pixels of
  surface, not eight pixels of drawing.
- **Markup writes one and names no painter.** A `canvas` node inflates to a
  styled, sized surface that draws nothing, so the parity invariant holds — a
  document says how big it is and what it sits on, and the drawing is Java.
  Naming a painter from markup needs the registry indirection `icon` and `action`
  use, and the shape of that registry depends on whether a painter is a value or
  a method. Filed rather than guessed at.
- **`sparkline` is built, and `statistic` has its child.** §11's first chart and
  its smallest: no axes, no legend, no tooltip — a shape beside a number, read
  for its direction. It is **one series, so it takes `color`**, exactly like
  text: a palette is for telling series apart and there is nothing here to tell
  apart, so an application recolours one with the property it would already reach
  for and a trend inside a `statistic` can inherit the delta's hue from a rule.
  The palette arrives with `line-chart`, which is the first widget that has two
  of anything.
- **It scales to the data's own range, not to zero.** A series between 1000 and
  1004 baselined at zero is a flat line that says nothing, and the shape of the
  change is the whole job. A flat series is centred rather than divided by a zero
  range, and the stroke is inset by its own half-width so a maximum is not
  clipped in half at the top edge.
- **`Lttb` is the first of §3.1's borrowed algorithms.** Largest-Triangle-Three-
  Buckets, and the reason is truth rather than speed: a hundred thousand points
  in a two-hundred-pixel sparkline is five hundred per pixel, and whichever one
  is drawn last wins. The test that matters puts a single 20× sample at index
  4237 of 10 000 and asserts both that LTTB keeps it **and** that every hundredth
  sample — "just take fewer points" — misses it entirely.
- **The marker is a disc and there is a test that says so.** SVG's `A`, which is
  what Blend2D's path takes, cannot draw a full circle in one segment, so it is
  two half-arcs; getting that wrong gives a square, a wedge or nothing, and all
  three look plausible at 200px. The corners of its bounding box are what tell
  them apart.
- **`statistic`'s note turned out to be right.** It said, while it was waiting,
  that a sparkline would be "one more child at the end of the column" — and that
  is exactly what it was, with no other change. That sentence is an assertion
  now. The gap has been open since M2 (ADR-0164).
- **Two goldens**: `canvas-dark`, a themed surface with a border and a radius
  from CSS and two bars from a painter — the picture that says the two halves
  compose — and `sparkline-dark`, filled and marked, in `--gb-accent`.
- **The axis substrate is built: `Ticks` and `Scale`.**
  `Ticks.extended` is §3.1's Wilkinson algorithm in Talbot, Lin and Hanrahan's
  2010 extension, which scores candidate labellings on simplicity, coverage,
  density and legibility and takes the best. It exists because nice numbers are
  not a rounding problem: `0…97` at five labels is either `0, 20, 40, 60, 80`,
  leaving a quarter of the axis unlabelled, or `0, 12.5, 25 …`, which asks the
  reader to do arithmetic to place a point — two failures pulling opposite ways,
  which is why one number cannot decide it. **Legibility is a constant 1**, and
  that is stated rather than dropped: the paper weighs font size and label
  overlap, which need a decided axis width this does not have yet.
- **`Scale` has no "inverted" flag**, and that is the design. A frame's y grows
  downward and a chart's values grow upward; every bug in this area is
  remembering that in one place and forgetting it in another. So a y scale is
  `linear(min, max, height, 0)` — a *swapped range* — and the arithmetic never
  knows which axis it is. A flat domain maps to the middle rather than to an
  edge or a `NaN`, so the next four charts inherit the rule `sparkline` had to
  state for itself.
- **`sparkline` was moved onto it and the golden did not move a pixel**, which is
  the check that says the substrate is the same arithmetic rather than a second
  opinion about it.
- **Twenty tests on eleven lines of algorithm, and the ratio is the point.** The
  tick tests are ranges chosen to be awkward: `0…97`, `1000…1004` (which must not
  fall back to labelling from zero, or the whole series sits in the last
  thousandth of the axis), negatives, and the same range at five magnitudes from
  nanometres to trillions, because a step that stops being round at some
  magnitude is a rounding bug. Plus a timing bound — it runs per axis per frame,
  so a millisecond would be a third of a frame's budget.
- **The series palette is the theme's, not the toolkit's**
  ([ADR-0195](adr/0195-a-painter-reads-the-theme-through-a-custom-property.md)).
  The eight values live in `nord-light.css` and `nord-dark.css` as
  `--gb-chart-1…8` and are read through a new `Paints.Context#color`, because a
  chart is the one widget that **cannot express its colours as CSS properties**:
  a node has one `color`, a stylesheet cannot say "the fourth series", and
  ADR-0065's parts do not help because a `canvas` has no child nodes at all — its
  content is a painter rather than a tree. A Java table would have worked and
  would have taken colour away from the theme: two themes would share one
  palette, an application could not recolour one chart's first series, and a
  third theme would be a code change. Now
  `#revenue { --gb-chart-1: #b48ead }` is an ordinary rule, and there is a test
  that says so.
- **It is the first thing on `Paints.Context` whose answer is per node**, and the
  context is deliberately one object per renderer — which is why `nowMillis` is a
  field rather than a clock call. So the renderer sets `currentElement` before
  `render` and clears it in a `finally`, and the clearing is not tidiness: a
  `canvas` painter **closes over the context** and runs later, during the paint,
  so a context still holding an element would let a painter read a stale node's
  tokens in a frame where the tree had changed under it.
- **`line-chart` is built** — the first chart with axes, and the first widget in
  the toolkit with more than one of anything. It is **two halves**: a `chart-plot`
  that is a canvas, because a chart of a thousand points must not be a tree of a
  thousand nodes; and a `chart-legend` that is ordinary widgets, because a legend
  is text and a swatch — the two things the toolkit is already good at — and
  making them nodes means a stylesheet reaches them, the shaping cache serves
  them, and the entries **wrap** when the chart is narrow, which is what
  ADR-0192's `flex-wrap` was added for. Drawing the legend inside the canvas
  would have re-implemented all three.
- **The legend is present for two series and absent for one**, which is a rule
  and not an option: with one line the title names it and a box repeating that is
  noise; with two, colour is the only thing telling them apart, so identity must
  never be colour alone.
- **What happens in `render` and what happens in the painter is the design.** The
  cascade and the text stack are only available in `render`, so the tick
  labelling, the series colours and the **shaping** of every label happen there —
  a chart that shaped its axis inside the painter would re-shape five unchanged
  numbers sixty times a second, at 56 µs each (ADR-0037). The painter gets the
  size, so it decides where the gridlines go and **how wide the gutter turned
  out to be**: measured from the shaped paragraphs, so an axis reading
  `1,000,000` reserves more room than one reading `5` and nobody wrote a number
  down.
- **Axis labels are formatted in the root locale**, which is `hud`'s rule with a
  stronger reason: a golden image of a chart formatted in the machine's locale is
  a test that passes in one country. Decimals come from the *step* rather than
  the value, so an axis stepping by 0.5 labels `1.0` and not `1` — a column where
  one label has a decimal point and the rest do not reads as ragged.
- **The golden found a defect immediately.** The last x label read `Su`: it is
  centred on its point, the last point is at the right edge, and half of it hung
  outside the clip. Edge labels are pulled back inside the plot now — nudging
  beats dropping them, because the two ends of an axis are the labels a reader
  most wants.
- **§3.2's inline KDL data works, via `option`'s precedent.** The inflater builds
  depth-first and hands a factory children that are already widgets, so `series`
  and `point` are registered nodes that draw nothing — exactly what `select`'s
  options are, and for exactly that reason. The alternative was teaching the
  inflater that some children are data, which is a change to the one mechanism
  every widget goes through, for a case two widgets have.
- **All five of §11's charts are built.** `area-chart` and `bar-chart` are modes
  of the same `chart-plot`, because the axes, the gridlines, the gutter
  measurement and the label-collision rule are the same for all three and three
  copies would be three chances for a chart whose gridlines are a pixel off its
  labels. `ChartParts` holds the two rules every chart shares — what its children
  are, and how §3.2's inline data is read — for the same reason.
- **A bar and a band start at zero and cannot be talked out of it.** A bar
  encodes its value as a *length*, so a baseline at 90 makes a 3% difference look
  like a doubling; `line-chart` is the only one of the five that may zoom its
  baseline, because a line encodes by position rather than by area. A negative
  bar hangs below the zero line rather than being drawn upside down, which is the
  one thing every naive bar renderer gets wrong.
- **An area chart is stacked, always.** Overlapping translucent bands are the
  classic unreadable chart: three series make seven possible colours on screen
  and none of them is in the legend. Stacked, the bands add to the total — which
  is what a reader assumes an area chart means anyway. So the choice between the
  two is real: `line-chart` for separate quantities, whose total is meaningless;
  `area-chart` for parts of one.
- **Bars sit *in* a band and lines sit *on* a point**, which decides where a
  label goes. Getting it wrong puts every bar chart's labels half a band to the
  left, and it looks like a rounding error rather than a category error.
- **`donut-chart` refuses two slices and refuses nine**, at construction, which
  is where `dialog` refuses two affirmative buttons and for the same reason. Two
  is a ratio and reads better as `progress`; nine has arcs too narrow to compare
  and more parts than there are distinguishable hues, and `bar-chart` answers the
  same question at forty categories. It also **always** has a legend, unlike the
  axis charts: an axis chart with one series is named by its title, and an arc
  has nowhere to write a name.
- **The ring starts at twelve o'clock and goes clockwise**, because that is where
  a reader's eye starts; the maths starts at three o'clock if nobody intervenes.
  The gap between slices is taken *out of* each slice rather than drawn over it,
  so a slice's area stays its share.
- **Four goldens**, one per chart, and each caught something a test could not
  have asserted: the clipped `Su`, the stacking order, the band-versus-point
  label offset, and the arc direction — `largeArc` set wrongly draws the
  *complement* of a slice, which is exactly wrong rather than obviously wrong.
- **The showcase has a Charts screen**, which is the first place all five are on
  one wall — and it is what asked for `masonry`
  ([ADR-0196](adr/0196-a-masonry-is-a-layout-that-reads-last-frame.md)). A donut
  is square, a `statistic` is three lines and a `line-chart` is whatever height
  it was given; in equal rows every card is as tall as the tallest beside it and
  the short ones sit in acres of surface.
- **`masonry` reads the frame before.** Nothing can tell a widget how tall a
  child will be before it is laid out, so each card reports what it came out as
  through `Measured`, the state banks it, and the next frame puts each card under
  the shortest column. First frame round-robin, second frame right. It is allowed
  here — where `Measured`'s third rule forbids it in general — for one reason:
  **the columns are equal width, so a card's height does not depend on which
  column it is in**, and the number being reported is stable under the thing it
  causes. Which is also why `columns` is a count and not a list of widths, and
  why there is a test that four frames produce one layout rather than a comment
  claiming they do.
- **It is a widget the design documents do not have.** §5's containers were
  complete without it; recorded in `ARCHITECTURE.md` §17.1 rather than resolved
  by editing a document that is the authority.
- **The screen found three defects that no assertion would have.** A legend's
  entries touched, because §8's `gap` takes one length and the two-value
  row/column form parses as nothing; the lowest y label was cut in half when a
  chart had no x labels, because it is centred on the baseline; and
  `--gb-chart-1: var(--gb-warning)` did nothing, because a custom property may
  hold **another** `var()` and CSS resolves those at *use* time — reading the raw
  tokens saw "not a colour" and fell back silently. `StyleResolver` exposes a
  substituted read now, which is what ADR-0195's mechanism should have done from
  the start.
- **And it closed an open TODO.** The gallery goldens never fed hit-test regions
  back between their two frames, so every self-measuring widget saw a first-frame
  answer for ever — `text-area` wrapped as though it were narrow in the Forms
  image and the entry said so. A masonry cannot be photographed at all without
  it, which made the gap concrete enough to close; the Forms picture is now the
  one the running application shows.
- **The interaction layer** `charts.md` §3.1 lists was next, and the hover half
  of it is built — see [What a chart does when a pointer
  arrives](#what-a-chart-does-when-a-pointer-arrives) and [A donut under the
  pointer](#a-donut-under-the-pointer-and-every-chart-under-the-keyboard).
  What a chart says when it has no numbers is built too — see [What a chart says
  when it has not got the
  numbers](#what-a-chart-says-when-it-has-not-got-the-numbers), and so are
  thresholds, the `java.time` axis, interpolation, log scales, soft bounds,
  point markers and the shared crosshair. **Only the gradient fill is
  outstanding**, and it is waiting on a native symbol rather than on a decision.

### Two things found by scrolling the wall of charts

- **A chart did not move with the panel it was on.** Scrolling the Charts screen
  slid the cards, the headings and the axis labels, and left the five plots
  where they were laid out — clipped by a viewport travelling over them. One
  line: `paintCanvas` moved the painter's origin to the box's content corner
  with `Frame.transform`, and that call **assigns** rather than composes
  (ADR-0068 chose that: the walk accumulates the matrix in Java, and each box
  assigns the answer, so a run of untransformed boxes costs no native call). A
  `scroll` moves its content with a `translate`, so the canvas replaced the
  scroll's matrix with its own. `paintOne` now takes the ambient matrix and the
  canvas composes onto it
  ([ADR-0197](adr/0197-a-painters-transform-composes-onto-its-ancestors.md)).
- **The clip in the same method was already right**, which is why the symptom
  was a chart standing still rather than one that vanished: a clip lands in the
  context's *current* user space and Blend2D intersects, so the two halves of
  one method disagreed about which space they were in. Both are now written down
  beside each other.
- **Nothing could have caught it.** Every canvas test paints at the root, and a
  golden is captured at scroll offset zero — where `ScrollContent` puts no
  transform on the context at all, because an unscrolled viewport should not.
  `CanvasPaintTest` now paints a canvas under a `translate` and reads the moved
  rectangle; it fails on the old code with the square exactly where it was laid
  out.
- **The gallery had two orders in it, and one of them was a crash.** The strip
  lists eleven screens; `Showcase` held its own copy of the list for the
  `Ctrl+1`… accelerators, and `charts` went into that copy *instead of*
  `choosers` rather than after it. So `Ctrl+8` selected the ninth tab, and the
  loop asked a nine-element digit list for its tenth entry — an
  `IndexOutOfBoundsException` in `start`, which is a window that never opens.
  `Screen.GALLERY` is the one order now: the strip is built through
  `Screen.inGalleryOrder`, which **throws** when the tabs and the list do not
  name the same screens, and the accelerators are bound from it.
- **Ten digits, eleven screens**, said out loud rather than papered over.
  `Ctrl+0` is the tenth and the eleventh has none; which screen goes without is
  the order's decision, and it is the tour screen, which is opened by name
  anyway. `GalleryOrderTest` runs each bound accelerator to find out *which*
  screen it picks, because a key bound to the wrong screen is invisible in a
  picture — both tabs render correctly.

### What a chart does when a pointer arrives

- **A crosshair, a marker per series, and a readout beside it** on `line-chart`
  and `area-chart`; a **band highlight** on `bar-chart`, because a hairline down
  the middle of a group of bars points at the gap between two of them. This is
  the first half of `charts.md` §3.1's interaction list
  ([ADR-0198](adr/0198-a-charts-readout-is-painted-and-its-legend-is-a-control.md)).
- **The plot's geometry is one arithmetic used in two directions.** `PlotGeometry`
  turns a point index into an x and an x back into a point index, and the test
  that matters is the round trip over every point count from 1 to 40 in both
  modes — because a crosshair two pixels left of the point the pointer chose is a
  chart that looks broken at one window size and fine at every other. It also
  settles where bars differ from lines in one place: a bar owns a **band** and a
  line passes through a **point**, which decides the label offset, the crosshair
  and the pointer mapping at once.
- **The pointer resolves against the frame that was painted.** The gutter is
  measured from the shaped axis labels, so the geometry is only known inside the
  painter — and a pointer event carries a rectangle and no text stack. So the
  painter leaves its answer in a `PaintedGeometry` and the handler reads it,
  which is ADR-0054's rule one level down: the toolkit already routes a pointer
  against the frame the user was looking at when they pointed.
- **The readout is painted and the legend is not, and the difference is not
  taste.** A legend wraps, is selected by a stylesheet and is in the same place
  every frame, so it is nodes (ADR-0192's `flex-wrap` was added for it). A
  readout is positioned in plot coordinates, flips side at the middle of the
  plot, and must not participate in layout — as widgets it would need absolute
  positioning against a gutter `children()` cannot see, so it would read a
  geometry one frame late to produce a node that must not be laid out. Its text
  is still shaped by the text stack, in `render`, where the hovered index is
  known: one point's worth of strings, and the cache serves the repeats.
- **It takes `hud`'s tokens**, through ADR-0195's `Paints.Context#color`. A
  floating overlay over content the reader is looking through it at *is* a HUD,
  and a chart inventing a fourth surface token would be one the theme cannot
  restyle with the rest.
- **Clicking a legend entry isolates its series, and clicking it again puts them
  all back** — §3.1's "the one interaction Grafana users reach for first". The
  entries that are not isolated are **dimmed rather than dropped**, because a
  legend that changed width as you clicked it would take the way back with it.
- **Isolation is an index, not a set of hidden series.** Unhiding a set requires
  remembering what you hid, and a chart showing three of eight series has a
  legend that no longer says what the picture is. Isolating **rescales the axis**,
  which is the point of asking for one series: a flat line at the bottom of a
  chart scaled to a bigger one has nothing to read.
- **The isolated series keeps its own colour**, which is why the series list is
  never filtered: the index *is* the palette slot (ADR-0194), so filtering would
  redraw an isolated fourth series in the first slot's hue and its own swatch
  would then disagree with it.
- **Three charts became stateful and the box tree did not change.** A hovered
  point and an isolated series are state, and a widget is a value — so
  `ChartPlot` is stateful above the canvas, and `line-chart`, `area-chart` and
  `bar-chart` are stateful above both halves, because the click lands on the
  legend and changes what the plot draws. What they build is a `ChartView`
  carrying the chart's own CSS type, `id` and classes, so every rule in
  `controls.css` still lands where it did and **all four chart goldens are
  unchanged, to the pixel**. A stateful widget occupies an element and no box.
- **Driven through the real router in tests**, which render, lay out, *paint* —
  the step a hover cannot work without — and then dispatch. They compare pictures
  to pictures rather than coordinates, because the hovered index is deliberately
  not readable from outside: hovering draws something, two positions over one
  point draw the same thing, the gutter draws nothing, and leaving clears it.
  Three new goldens say what it looks like.
- **`RoundRect` is public**, with a comment saying why: a canvas painter needs a
  rounded rectangle and the alternative was a second derivation of ADR-0064's
  four cubics in `:widgets`. No new symbol crosses the native boundary.
- **Still owed from §3.1**: thresholds, log axes, `java.time` axes, null handling,
  interpolation, soft bounds, gradient fills, empty and error states, a shared
  `CrosshairGroup` across charts, hover on `donut-chart`, and §3.5's keyboard
  operation — arrow keys walking the crosshair, which now has somewhere to keep
  its index and no keys bound to it.

### A donut under the pointer, and every chart under the keyboard

- **A donut reads its slices now**, which closes the hole ADR-0198 left in its
  own parity row. The hovered slice keeps its colour, the others fade, and the
  hovered one's name and **share** go in the hole
  ([ADR-0199](adr/0199-a-chart-answers-the-keyboard-and-a-step-is-relative.md)).
  The share rather than the value, because a part-to-whole chart is about the
  proportion and a reader who wanted the raw number wanted a bar chart; `<1%`
  rather than `0%` for a sliver, because a readout must not contradict a visible
  arc.
- **The hole is where a donut's readout belongs**, and it is the one placement
  decision in the five charts that needed no arithmetic: an axis chart's readout
  has to be placed, flipped and clamped, and a ring has already reserved an empty
  circle that cannot cover the data and cannot be clipped by the box. Only what
  fits is drawn — a hole is a circle and text is a rectangle, so a long name is
  left out and the share is not.
- **A ring needs no banked geometry.** `DonutGeometry` follows from the box
  alone, so unlike `PlotGeometry` — whose gutter is measured from shaped labels
  and has to be left behind for the pointer — it is computed fresh on both sides.
  Which is why its test needs no renderer at all.
- **The gaps between slices belong to a slice.** The painter trims a sliver off
  each arc so they do not touch; a hit test that respected those slivers would
  put a ring of two-pixel dead wedges through the chart, and a pointer crossing
  one would drop the readout and pick it up again. There is a test that walks 720
  angles and finds a slice at every one.
- **Every chart can be read without a pointer**, which is §3.5's first item and
  the one it is most insistent about. A plot with data is a Tab stop and takes the
  same focus ring as every other control; `Left`/`Right` walk, `Home`/`End` are
  the ends, `Escape` lets go and is consumed **only if it cleared something**, so
  it still closes the dialog the chart is sitting in. An axis **clamps** at its
  ends and a ring **wraps**, because a line has two ends and a ring has none.
- **`Up` and `Down` are deliberately left alone.** A chart is very often inside a
  `scroll`, and a focused widget that consumed the vertical arrows would swallow
  the keys that move the page — the same class of theft §2.4 bans nested scrollers
  for. Two arrows reach every point.
- **`Tab` does not move between series**, which is a refusal of one sentence of
  `charts.md` §3.5: `Tab` is the focus traversal and a composite is one Tab stop
  with roving arrows inside it (ADR-0073), and the readout already names *every*
  series at the point rather than one at a time. Recorded in
  `ARCHITECTURE.md` §17.1 rather than quietly not done.
- **Writing the keyboard found a defect that has nothing to do with charts.** A
  key handler computing "one to the right" was reading the crosshair index off
  the **widget**, which is the description the last frame was built from — so two
  arrow presses between two frames both stepped from the position before either
  of them and the crosshair moved once. That is a key repeat on any machine
  dropping frames. A step is **relative** now and only the state applies it;
  absolute positions stay absolute, and the callbacks answer whether anything
  changed so nothing has to consult a stale copy to decide whether to consume a
  key. The rule generalizes to any widget whose keys move a position it reports
  upward.
- **The keyboard and the pointer are held to one answer, in pixels.** The test
  that matters asserts `End` and a pointer at the right-hand edge produce *the
  same frame* — not two descriptions of one intent. It was also the test that
  caught the defect above, by pressing an arrow twice without a frame in between.

### What a chart says when it has not got the numbers

- **A chart with no data says so**, where it used to draw five gridlines and five
  labels over nothing — every one of those numbers invented. An empty grid is not
  a neutral picture: gridlines are an assertion about a scale, and asserting one
  over no data is the same class of untruth as a bar chart baselined at 90
  ([ADR-0200](adr/0200-a-chart-with-no-data-says-so.md)).
- **Three states and one of them is derived.** `LOADING` and `FAILED` are the
  application's to say — only it knows whether a query is in flight or came back
  angry — and **empty is not**: a chart whose series are empty is `READY`, and the
  widget notices. A fourth state an application had to declare would be one that
  can disagree with the list beside it.
- **It keeps the box, and that is why the chart owns this at all.** An application
  can write `loading ? spinner : chart` in a line; what that costs is the height.
  `chart-message` takes the plot's `flex-grow`, so a chart in a 156px card is
  156px while it loads, and a `masonry` of cards whose charts came and went as
  their queries resolved would reflow the wall twice per panel. Asserted by
  measuring the laid-out height in all three states rather than argued.
- **The message is widgets and the hover readout is paint**, which looks
  inconsistent until you ask the question that decides it: does it participate in
  layout? A readout is placed in plot coordinates and must not affect the box; a
  message is centred, wraps, and *is* the content.
- **Two more strings the toolkit writes rather than the application** — `No data`
  and `Loading…`, after `Field.REQUIRED_MESSAGE` and for its exact reason: an
  application that passed an empty list has supplied no words. Both are
  overridable. **No spinner**: §1.7 keeps the frame loop idle when nothing
  animates, and a dashboard's charts are all waiting at once.
- **A hole is not a zero**, which is `charts.md` §3.1's sentence and now three
  ways of drawing one ([ADR-0201](adr/0201-a-hole-is-not-a-zero.md)). `GAP` is the
  default because it is the only one of the three that invents nothing; `CONNECT`
  interpolates the interior holes, which is the straight segment for a line and
  the same shape filled for a band; `ZERO` says the value was zero, which is right
  for a counter and a lie everywhere else.
- **And it was a live defect rather than a gap in a feature list.** `Math.min`
  propagates `NaN`, so one missing reading made `Series.min()` answer `NaN`, the
  axis found its domain was not finite, fell back to `0…0` and **collapsed the
  whole chart onto one line**. A single absent sample destroyed the picture,
  silently, because no test had a hole in it.
- **A `null` is read as a hole rather than refused.** `List.copyOf` rejects nulls,
  so before this a series read out of a nullable column had to be converted by its
  caller — and the obvious conversion is `orElse(0)`, which is exactly the answer
  the policy exists to prevent.
- **One place applies the policy.** `Gaps.resolve` produces the substituted values
  *and* the runs of consecutive drawable indices, so a polyline, a band and a bar
  read one answer and no mode can quietly disagree about where a hole is. LTTB
  runs per run, because downsampling across a hole would invent a segment through
  it.
- **A hole in one series is a hole in the whole stack.** A band's y is a running
  total, so an index where one component is missing is an index where the total is
  unknown; drawing the bands above it as though the missing one were zero would
  put them at a height nobody reported.
- **Nothing is dropped silently.** A run of one has no segment, so a line draws a
  dot and a stacked band draws its cross-section a pixel wide — the objection LTTB
  exists to answer, applied to one point rather than to a spike in a hundred
  thousand. The area golden showed the dropped Monday before the fix.
- **The policy is the chart's, not the series'.** One picture, one convention: two
  series treating their holes differently is a chart nobody can read without being
  told which line is which kind, which is the argument that gives a chart one x
  axis and refuses it a second y.
- **Four new goldens, and the assertion that matters is that three of them
  differ.** Null handling wired up but never applied would pass every unit test
  about the arithmetic and draw one picture for all three policies.

### Three things the wall of charts said about holes

- **The showcase has the argument on it.** The Charts screen's last card draws
  the same twelve readings twice — three of them missing — under `GAP` and under
  `ZERO`, with a line of prose under each. One card and not two, because a
  `masonry` places by column height and could not be promised to keep a pair
  together; and because the wrong picture is only obviously wrong *beside* the
  right one. On its own, a line diving to the baseline looks like data.
- **The data was chosen to show both shapes a hole makes.** A two-sample dropout
  leaves a hole between two segments; a single reading with a hole on either side
  leaves a **dot**. The first draft had no lone reading in it and the caption
  promised one, which is the sort of thing a screenshot catches and an assertion
  does not.
- **A lone reading was nearly invisible, twice over.** It was drawn as a disc of
  the stroke *radius* — a couple of pixels, indistinguishable from the gridline
  behind it — and when it fell on the last index it sat exactly on the plot's
  right edge with half of itself outside the box. So the one rendering that exists
  to stop a reading being dropped was dropping it. It is a disc of the stroke
  *width* now, pulled back inside the plot at the ends, which is the rule the x
  labels already follow and for the same reason.
- **And the axis did not have to cover the data.** `Ticks.extended` scores a
  candidate labelling on four things and coverage is only one of them, so the
  nicest labels for `12…36` are `10, 15 … 35` — which stops short. The scale was
  the *labelling's*, so the last point was drawn above the top gridline, inside
  the headroom by luck rather than by rule, and a 4px disc there hung over the
  edge. The scale is the union of the labelling and the data now; the gridlines
  stay on the round numbers, which is what every chart a reader has seen already
  does. Six goldens moved, all of them by a few pixels of scale.

### A limit drawn across a chart, and the grey it turned out to be

- **Thresholds are built** — `charts.md` §3.1's lines and shaded regions, in one
  of four semantic levels with **no way to pass a colour**
  ([ADR-0202](adr/0202-a-limit-is-not-a-series.md)). That refusal is the decision:
  the series palette exists to keep the things being *compared* apart, and a limit
  is a statement about them, so a threshold from the palette would read as one
  more series and steal a real one's hue. It reads `--gb-<level>-line`, which is
  the rank §1.2 added for a stroke drawn on the page rather than for a label or a
  fill.
- **A band is a wash and its edges, and that was a measurement rather than a
  taste.** The first version was a wash alone, and this theme's warning hue at 16%
  over the dark surface computes to `(76, 76, 76)` — *exactly* neutral grey. A
  band whose semantic colour a reader cannot perceive says "something" rather than
  "warning". Each finite edge is drawn at full strength now, and the test asserts
  both halves: that the edges are in the warning hue, and that the wash really is
  the grey that made them necessary.
- **A threshold is part of the domain.** The axis stretches to reach it, so "we
  are a long way from the limit" is a reading a chart can give. One that only
  appeared once it had been breached would be a warning light that comes on after
  the fire.
- **A band goes under the data**, because a warning that hid what it was warning
  about would cost you the reading you came for. `NaN` is refused at construction
  with a message naming `NullPolicy`: it is the one place in this area where a
  `NaN` means something specific, and a limit that is missing is not a limit.
- **The showcase's `p99 latency` card has an SLO band on it**, which is also the
  only colour on that screen that is not from the series palette — the decision,
  visible on the wall.
- **The three axis charts now have six components**, and the next §3.1 item makes
  it seven. ADR-0202 records that the next one should bundle everything that is
  not the data into a `ChartOptions`, with the withers kept as the public surface;
  doing it in the same change as the feature would have hidden the feature.

### The same mistake twice: where a pointer is inside a scrolled box

- **Scroll a panel and a chart stops highlighting**, which is what running it
  said. Every pointer event still arrived — hover, press, click, the cursor, all
  of it — and the crosshair simply never appeared once the panel had moved.
- **`localTo` subtracted the layout origin from the window point.** A hit-test
  region holds the rectangle a box was *laid out* in plus the **inverse** of the
  matrix it was painted with, because a `scroll` moves its content with a
  transform and Yoga never saw it (ADR-0054, ADR-0068). `Region.contains` maps
  the pointer through that inverse; the router's *where inside* did not. So a
  control 300px down a scrolled panel was told the pointer was at `y = -290`:
  every widget asking "am I inside" got no, for the whole length of the scroll,
  while every one of them still received the event.
- **Two arithmetics for one question**, which is the shape this codebase keeps
  finding: `PlotGeometry` exists because a crosshair and a painter must not each
  work out where a point goes, and `Scale` exists because a value becomes a
  position in exactly one place. This was the same defect one level up, in the
  method that had no second reader until a chart arrived.
- **It is the second time in a week that something ignored the ambient
  transform.** `paintCanvas` assigned its matrix over its ancestors'
  ([ADR-0197](adr/0197-a-painters-transform-composes-onto-its-ancestors.md)) and
  this dropped the inverse; both were invisible until a widget that *reads*
  geometry was put inside a `scroll`. The pattern worth remembering: anything
  that mixes a window coordinate with a layout coordinate is wrong unless it says
  which space it is in.
- **The chart was not the only casualty.** `text-area` places its caret from
  `local().y()`, so a text area below the fold put the caret on the first line;
  anything measuring vertically inside a scrolled panel had the same answer. A
  vertical scroll leaves `x` alone, which is why a `slider` — which reads
  `fractionX` — looked fine and hid the bug.
- **Guarded at both levels.** `LocalUnderTransformTest` asserts the arithmetic in
  `:core` with a hand-built region, which is where the defect is; `ChartInputTest`
  scrolls a real viewport with the wheel and asserts a scrolled chart highlights
  *exactly what the same chart highlights on its own*, compared over the plot's
  own pixels because the two windows differ everywhere else. The first version of
  that test passed without the fix — the scroll bar reacts to the same pointer, so
  the frame changed for a reason that had nothing to do with the chart.

### A time axis, which is where the points go and not how they are labelled

- **`content-widgets.md` §3.1's `java.time` axis is built**, and the decision it
  turns on is not the labelling
  ([ADR-0203](adr/0203-a-time-axis-is-time-not-a-relabelled-index.md)). Every
  chart's x has been the point **index**: a metric scraped every 15 seconds that
  missed four minutes had exactly one step of gap, the same step as every reading
  that was on time. That is a picture of a schedule nobody kept, and relabelling
  the index would have left it there.
- **The ticks step in `java.time`, and that is the whole of why the class
  exists.** `Ticks` is Wilkinson's algorithm for numbers and a nice number is a
  round multiple; time has no round multiples. A step of `2 592 000 000 ms` is a
  month only in a year with no February in it and has drifted five days by
  December; a step of `86 400 000 ms` is a day except on the two days a year a
  zone changes offset. `TimeTicks` picks a rung — the steps a clock is read in, 1
  through 30 seconds, up to decades — snaps to a boundary of that rung's own unit
  and advances with `ZonedDateTime.plus`.
- **The DST case is a test because nobody writes one.** A day step across
  Berlin's spring-forward is 23 hours and still lands on local midnight; a month
  step over a year lands on the first of twelve different-length months. Both are
  asserted, and both are what stepping in milliseconds gets wrong in a way that
  looks like an off-by-one.
- **The zone is the application's and the format is the root locale**, which land
  on opposite sides of a question that looks like one question. A locale changes
  *how* a number is written and a zone changes *which* number it is: an axis in
  the machine's language is an unfamiliar picture and an axis in the machine's
  zone is the correct one. `times(list)` reads the machine's zone; a test passes
  `UTC`.
- **A sampling gap is not a hole, and the two compose.** The axis makes an
  unscraped stretch wide; the line still crosses it, because both ends are
  readings that happened. An application that means "nothing was measured in
  between" writes a `NaN` and `NullPolicy` breaks the line — two mechanisms, two
  meanings, and a test that says so.
- **A bar chart ignores it.** A bar has a width and sits *in* a band, and bands of
  unequal width are a different chart; half-applying the axis would put labels
  where the bars are not.
- **The first version of the axis labels read `09:00, 09:30, 09:45`** — it dropped
  colliding labels one at a time, which keeps two neighbours and loses the one
  between them, so a reader cannot tell what the spacing is. It strides now, like
  the categorical labels, with room for the end labels being clamped inward.
- **`ChartOptions` earned itself first.** Everything about a chart that is not its
  numbers is one record now — the state, the null policy, the limits and what the
  x means — which is what ADR-0202 said the next feature should find rather than
  a seventh component on three charts.
- **The showcase's `p99 latency` card is a real time series**: its ninth scrape is
  twenty minutes after its eighth, and the axis is twenty minutes wide there.

### A curve is a claim about what happened in between

- **Interpolation is built** — `linear`, `smooth` and `step`, with `LINEAR` the
  default because it makes the weakest claim and a chart should not make a
  stronger one unasked
  ([ADR-0204](adr/0204-a-smooth-line-cannot-overshoot.md)).
- **`SMOOTH` is monotone cubic, and the point is what it refuses to draw.** A
  Catmull-Rom or a natural spline through `0, 0, 100, 100` dips **below zero**
  before it climbs and overshoots above a hundred after — which is what those
  splines are for, and wrong for data: on a percentage the overshoot is not
  inaccurate but impossible, and it lands exactly where a reader is looking
  because it lands where the interesting thing happened.
- **Two limits, and the second is the one that was missing.** Fritsch–Carlson's
  `α² + β² > 9` circle scales a pair of tangents back; a **local extremum needs a
  flat tangent**, which the circle does not give. Averaging the secants at the top
  of `1, 9, 2` gives `+0.5` and the curve reaches 9.0013 on a series whose maximum
  is 9 — a chart drawing a number nobody recorded, at the one point a reader is
  looking at.
- **The test found it**, which is the reason it is written the way it is: every
  assertion **samples the curve** densely and checks the bounds rather than
  inspecting the coefficients. A property one missing `if` away from being false
  is not one to argue from the algorithm.
- **`STEP` holds forward.** A value read at 09:00 is what was true from 09:00
  until somebody looked again, so the horizontal comes first and the jump lands on
  the next reading. Holding backwards would say the new value was already true
  before it was observed, which is the one direction the data cannot support.
- **One emitter, used by a line and by both edges of a band.** A smooth band whose
  underside was straight would be thicker than its own numbers wherever the top
  bulged; the underside is the same curve reversed, which for a cubic is its
  control points in reverse order. The showcase's `Bytes served` stack is smooth,
  which is the case that would show a mismatched pair.
- **`Curves` is public and pure** — no renderer, no natives, no path. The painter
  asks for tangents and control points and does the drawing, so `goldberry-plot`
  gets the arithmetic without the widget.
- **And it composes with everything already there**, because the tangents are
  computed on the pixels the painter is about to draw: an unevenly sampled series
  on a time axis curves correctly for free, a `GAP` run curves per run, and an
  isolated series curves alone.

### A log axis, and the readings it cannot take

- **§3.1's log axis is built**, and it is the first thing in the toolkit that is
  not affine ([ADR-0205](adr/0205-a-log-axis-has-no-room-for-zero.md)). Every
  scale — the sparkline's included — has mapped a domain onto a range linearly;
  `Scale.log` maps `log10(value)` instead, as a **flag** rather than a subtype,
  because every caller wants *a scale* and none of them wants to know which kind.
- **Wilkinson is the wrong algorithm again**, for the reason it was wrong for
  time: it scores how round a number is against how evenly the labels cover the
  range, and on a log axis those pull apart completely — `1, 10, 100, 1000` is
  the only labelling anybody wants and it is, in value space, wildly uneven.
  `LogTicks` labels decades, strides them when there are too many, and subdivides
  by the **1-2-5** mantissas when there are too few. Not every integer, which
  crowds the bottom of each decade where a log axis has least room.
- **The subdivision is the one nearest the target, not the first to reach it.**
  The first rule here turned `1…1000` at five labels — four whole decades — into
  `1, 5, 10, 50, 100, 500, 1000`: a decade axis made into a half-decade one to
  gain a label it did not need. Caught by the first test written against it.
- **A zero has no logarithm, and the chart says so.** `Gaps.positiveOnly` turns a
  non-positive reading into a hole and `NullPolicy` draws it as one, so the line
  **breaks** there rather than sliding off the bottom of the picture. That is the
  honest rendering of "there is nowhere to put this", and it is why a log axis is
  opt-in rather than something a chart could choose when its numbers span enough
  decades: choosing it costs data, and only the application knows whether the
  zeroes matter.
- **Only `line-chart` draws one.** A bar and a band encode their value as a
  length *from zero*, and zero is infinitely far down; a chart drawing one anyway
  would have to pick a bottom, and every choice is a number nobody gave it.
- **A series with nothing positive in it falls back and keeps its data.** The
  first version filtered and *then* discovered it had nothing left, and drew an
  empty grid — worse than either honest answer. `Scale.log` refuses a non-positive
  domain and a paint pass must not turn that into an exception, because a query
  can return zeroes.
- **Measured rather than asserted by eye**: the six quiet readings of a series
  that spikes four decades get **two rows** of a 156px plot on a linear axis and
  **thirteen** on a log one. The test compares the multiple rather than the
  difference, because what the axis promises is proportional.

### The last three of §3.1, and the one that needs a symbol

- **Soft bounds stop a flat series rendering as noise**
  ([ADR-0206](adr/0206-a-crosshair-may-be-shared-and-a-bound-may-be-soft.md)). An
  uptime reading `99.94, 99.97, 99.91, 99.99` auto-scaled fills the plot with the
  difference between 99.91 and 99.99 — a mountain range made of eight hundredths
  of a percent, shouting loudest exactly when the news is good. `softAxis(99,
  100)` draws the flat line near the top that it is, and an outage still pushes
  the axis down to meet it, because "at least this far" is what soft means. A
  **hard** bound does not move, and data outside it is clipped — the correct
  rendering of a promise that was wrong. Asserted rather than argued: the same
  readings use **more than three times** the vertical room auto-scaled that they
  use bounded.
- **Point markers are `AUTO` by default, and this changed every sparse chart in
  the toolkit.** A dot appears when its neighbours are more than four
  marker-widths away — **in pixels**, because what makes a dotted mess is how
  close the dots are on screen rather than how many there are, so the same chart
  shows dots at seven readings, none at seven hundred, and shows them again when
  the window is widened. Deliberate: a dot per reading is the difference between
  a measurement and a trace, and the goldens moved with it.
- **A crosshair can be shared.** `CrosshairGroup` is a mutable holder an
  application owns — a `ToastController`'s shape (ADR-0177) — and what travels is
  the point **index**, so the charts in one are assumed to be sampled together.
  Every chart in the group draws the line; **only the one under the pointer draws
  the readout**, because a dashboard with six floating boxes on it, five about a
  chart nobody is pointing at, is worse than no linking at all.
- **Which needed the crosshair and the readout to stop being one condition.**
  `paintHover` returned early when the readout was null, so a linked chart drew
  nothing at all; and the hover marker's ring colour was read off the `Readout`,
  so a chart drawing markers without one crashed. Both were the same assumption —
  that a chart draws a crosshair exactly when it has something to say — and it
  held right up until two charts shared one.
- **The showcase links its two seven-day charts.** `Requests per day` and `Bytes
  served` are the same week, which is what a group needs; pointing at Thursday on
  either puts the crosshair on Thursday on both.
- **A leak is tested for rather than reasoned about.** A chart that stayed
  subscribed would hold the group's listener list — and through it the last
  window's charts — alive; `CrosshairGroup.listenerCount()` exists for that test
  and nothing else. Writing it also found that the test harness was never
  unmounting its element tree, so `dispose` had never run in any of these files.

### The fill that needed a wider library

- **`charts.md` §3.1 is complete**, and its last row was the only one that could
  not be built out of what the export list already had
  ([ADR-0207](adr/0207-a-fill-may-be-a-ramp.md)). Every drawing call on that list
  takes its colour as an `rgba32` argument, because that is what the toolkit's own
  painter has ever needed; a gradient is an object with stops that has to exist
  while the fill happens, and it reaches a context as *state*. So the first commit
  of a chart feature was six symbols and two layout rows.
- **The sixth symbol is the interesting one.** `bl_context_fill_path_d` — the
  plain fill, with no `_rgba32` suffix — is the only styleless drawing call bound
  and the only way a ramp reaches a path. The other five build a gradient and put
  it on the context and take it off again, and **taking it off is not optional**:
  a gradient left set would be drawn by whatever reached for the styleless fill
  next, somewhere else in the frame entirely. That is `globalAlpha`'s rule and
  the opposite of its mechanism — an alpha has a neutral value to go back to and
  a fill style does not, so restoring one means choosing one.
- **The OKLCH in the deferred entry turned out to be vacuous.** A fade between two
  alphas of one hue is the same curve in every perceptual space. What actually
  makes a fade correct is premultiplied interpolation, which Blend2D does, and
  **repeating the colour at the far stop**, which the caller must: `0x00000000` is
  transparent *black*, and a green fading to it goes through grey on the way out.
  `BlendGradient.fade` is a constructor rather than two lines at each call site
  for exactly that reason.
- **`Fill.NONE` is the default, so nothing changed.** A line chart draws no fill,
  which is what a line chart already was; an area chart reads `NONE` as `SOLID`,
  because a band with no fill is not a band. Every existing golden is untouched.
  Three values rather than an opacity number: an opacity is a number a caller
  could want any value of, and a fill is a choice between two conventions.
- **A ramp is anchored to the data rather than to the plot.** Under a line it runs
  from the furthest point of that run from the baseline back to the baseline; in a
  band it runs across the band's own extent. Anchored to the plot instead, two
  series of different magnitudes would be drawn at different strengths and a
  stack's lower bands would be half gone before they started — which is what the
  test asserts, by measuring that both bands still reach their own colour
  somewhere.
- **The fill follows the curve the line was drawn with**, because it is built from
  the same run of points — after smoothing and after downsampling. One built from
  the raw values would show its own straight edges through a smoothed line.
- **A gradient is sampled at the pixel's centre**, so the pixel sitting on the
  start point is already half a pixel along the ramp. The native tests assert
  *near* a stop's colour rather than equal to it; the exact form would be an
  assertion about Blend2D's sampling grid rather than about the fade.
- **`goldberry-html` and `goldberry-vector` start one commit further along.** Both
  entries in [TODO.md](TODO.md) named these symbols as their own first step, which
  is what made the width worth taking for one row of a chart table.
- **The showcase's `p99 latency` card fades**, and it is the one thing on that
  screen that could not be drawn before. It thins out before it reaches the SLO
  band, so the limit is still read *against* the data rather than through it.

### The keyboard's right-click

- **A context menu opens from the keyboard now**
  ([ADR-0208](adr/0208-a-context-menu-answers-the-keyboard.md)), which is the half
  of ADR-0108 that did not ship and left the catalog with one entry a pointer was
  the only way into — in a toolkit whose §2.2 says everything must be reachable.
- **`Key.MENU` and `Shift+F10`**, both rather than either: the first is SDL's
  `SDLK_APPLICATION`, and the second is the companion binding everywhere and the
  only one on a keyboard that has no menu key. **Bare `F10` is left alone**,
  because it is the `menubar`'s (ADR-0163) and an application with both would
  open a context menu where it meant to activate its bar.
- **It anchors to the focused element's painted rectangle**, because there is no
  point to anchor to — so the menu hangs off the bottom of whatever has the focus
  ring. The element-wise anchor the TODO entry said "does not exist" turned out to
  have existed since ADR-0111, where the tooltip path built it.
- **One walk, two callers.** "A right-click on a button's label is a right-click
  on the button" and "the menu key on a focused button is that button's menu" are
  the same rule, so they are the same method — a second copy would be a second
  chance for the two to disagree about which ancestor wins.

### The rest of a tree's keyboard

- **§3's `Home`/`End`, `*` and type-to-select are built**
  ([ADR-0209](adr/0209-a-tree-finishes-its-keyboard.md)), which is three of the
  five things ADR-0184 shipped `tree` without. They waited for one reason and it
  is the same reason for all three: each needs to know about rows the focused one
  cannot see, so each is a callback the tree hands down — the shape `Left`'s
  move-to-parent already had.
- **`Home`/`End` mean the flattened list**, not the viewport: `End` in a scrolled
  tree goes to the last row of the model, and the focus ring asks the scroller to
  follow (ADR-0120).
- **`*` opens the siblings and not the descendants**, which is the reading that
  makes it useful and the one that does not hang a lazy tree by fetching its whole
  model on one keystroke. A lazy sibling's supplier runs exactly as it does for a
  branch opened by hand.
- **`*` and the typeahead both arrive as text rather than as keys.** `*` is a
  *character* whose key differs by layout — `Shift+8`, a numpad key, or neither —
  and asking for the key would be asking for the physical position, which §7.1
  says this toolkit does not answer. A typeahead wants what was typed for
  `select`'s reason (ADR-0141).
- **Typing moves the focus and chooses nothing.** `Enter` is what chooses, which
  is `select`'s split and ADR-0063's rule; a keystroke committing a value in a
  controlled widget is the thing that rule exists to prevent.
- **Type-to-select matches visible rows only**, which is §3's own wording: a
  search that opened branches to find its match would be a search, and a lazy tree
  cannot have one without fetching everything.
- **A test that could not fail was found writing these.** `TestHost.focusRequests()`
  hands back a copy, so a test making several moves and clearing it between them
  was clearing a list nothing was writing to. `forgetFocusRequests()` is the fix,
  and it keeps the record on the host where it belongs.

### A tree checks and selects two different things

- **`tree`'s last two leftovers are built**
  ([ADR-0210](adr/0210-a-tree-checks-and-selects-two-different-things.md)), and
  §3 owes it nothing further: `checkable=` puts a box on the rows and
  `selection=` is none/single/multi with the Ctrl/Shift semantics.
- **Multi-selection was recorded as blocked on `list` and that reading was too
  strict.** `tree` defined the *node* model itself for exactly the same reason
  (ADR-0184) and wrote down that `list` will have to agree with it; the selection
  models are the shape every desktop list has, which is what makes that a small
  promise to make on `list`'s behalf. The same debt, taken knowingly and in the
  same place.
- **The checkbox needed a question answered first, and it was in the design
  document rather than in the code.** §3 spends the word `checkable` twice — on
  `select tree=` it is which rows are an **answer**, and on a standalone `tree` it
  "adds a **checkbox** per node". Both ship, under two names, and the word doing
  two jobs is now recorded in `ARCHITECTURE.md` §17.1.
- **Selecting and checking are two values through two callbacks.** The selection
  is where the reader *is*; the checks are what they have *marked*. A file manager
  where those were one thing could not copy six files, because opening the seventh
  folder would clear the list.
- **A cascade parent is derived and never stored.** A stored parent bit goes stale
  the moment one child is unticked, and the row then claims "all of these" while
  showing one that is not. A **lazy branch nobody has opened reads its own
  membership** — fetching a model to draw a checkbox is the one thing a lazy tree
  must not do, which is what `mayHaveChildren` exists for.
- **Clicking a mixed branch asks for all of it**, which is
  `Checkbox.Value.toggled()`'s rule getting a second *caller* rather than a second
  copy — and the box itself borrows `check-indicator`, so there is one tri-state
  mark in the toolkit and not two kept alike by hand.
- **The whole set is reported even when it holds one.** A `Shift` range runs over
  the flattened visible rows, which only the tree can see, so an id on its own
  would be an answer the application could not turn back into a selection. The
  three-argument constructor unwraps it again, so `select tree=` and every
  existing caller see the `String` they always saw.
- **The anchor does not move under `Shift`**, so a run of shifted presses sweeps
  from one end rather than growing from wherever it last stopped — which is what
  makes an over-shot range recoverable without starting again.
- **Two tests failed first by assuming the widget remembered its own selection**,
  which is exactly what ADR-0063 says it must not. Rewritten to apply the reported
  set back between presses, which is what an application does and what turns them
  into tests of the loop rather than of one call.
- **Every existing golden is byte-identical**, because all three defaults are
  unchanged. The new one is a cascade tree with a partly-ticked branch — the one
  state a picture is the only proof of, that the mixed mark is a bar and not a
  greyed tick.

### The release that arrived in another window's space

- **Every popup on macOS could be opened and hovered and not chosen**
  ([ADR-0211](adr/0211-a-popup-asks-the-desktop-where-the-pointer-is.md)). The
  press landed on the row and the release did not, so no click was ever
  synthesized — a dropdown, a menu and a suggestion panel all unusable, and the
  toolkit's own halves all correct.
- **SDL promises coordinates in the target window's space and does not deliver
  them here.** `Cocoa_SendMouseButtonClicks` rewrites them only when the event's
  `NSWindow` is not the key window; a mouse-up goes to the key window, and a
  `NOT_FOCUSABLE` popup can never be one — which it is by ADR-0189, and for a
  reason that stands. So the press arrives in the popup's space and the release
  in the **owner's**, both carrying the popup's id.
- **And the owner-space value is stale**, which is worse than a fixed offset:
  nothing updates it while the pointer is over the popup, so a release reports
  where the pointer was before the popup opened.
- **The bounds check is the detector and the desktop is the answer.** A
  coordinate inside the window it was delivered to is taken as given — every
  event on every other platform, and most of them here. One that falls outside
  has its *space* in doubt, and only then is `SDL_GetGlobalMouseState` asked.
- **A popup's desktop origin is its owner's position plus the offset it was asked
  for**, because `SDL_GetWindowPosition` on a popup reports the display's
  coordinates on some drivers and the parent's on others. Two readings that are
  not in doubt rather than one that is.
- **Every window is reconciled, not only popups.** A top-level window's
  coordinates are already inside its own bounds, so the branch never fires for
  one — and a rule that named popups would stop being checked the day something
  else needed it.
- **A test can push a pointer now.** `SdlEventBuffer` gained `writeMouseMotion`
  and `writeMouseButton` for `writeWheel`'s reason (ADR-0061): all three branches
  run under the dummy driver against the real translate. What no test here
  reaches is SDL's *attribution* — the dummy driver refuses popups outright, and
  the behaviour is a property of a real `NSWindow`.
- **A drag off a control still cancels its click.** The desktop reading agrees
  the pointer is outside; only the magnitude changes.

### A list, and the models it was owed

- **§10's `list` is built** ([ADR-0212](adr/0212-a-list-owns-the-models-a-tree-borrowed.md)),
  which leaves `table` as the only entry in that section — still deferred, still
  on virtualization.
- **It was built to settle a debt as much as to fill a gap.** `tree` took
  ADR-0184's rule twice: the widget that needs a model first defines it and
  writes down that the other will have to agree. So `Selection` lived in
  `panel.tree` while §3 called it "`list`'s selection models". It has moved, and
  **nothing about its shape changed on the way** — which is the evidence that the
  promise was a small one to make. `Checkable` stayed, because §10 gives a list no
  checkbox and a model with one consumer belongs to that consumer.
- **An item is the application's own type**, and three functions describe it:
  `identity` says what it *is*, `factory` says what it *looks like*, and `text`
  says what it *reads as*. Three lambdas rather than an interface to implement,
  because an interface would make the trivial list — strings drawn as text — the
  one that cost the most to write. `ListView.of(List<String>)` is that case in one
  call.
- **`text` is optional and its absence turns type-to-select off**, which §10 asks
  for in as many words. The half that is not obvious is that the row must then
  **not consume** the keystroke: one that swallowed text it could not use would
  stop a field elsewhere from ever seeing one.
- **§10's item context menus are named on the row**, and that is the one place
  they can be. A menu is a name on a widget the launcher finds by walking up from
  an element — a right-click walks up from what is under the *pointer* and the
  menu key from what has the *focus* (ADR-0208), and the row is the only node on
  both paths. So `ListRow` carries an `Attributes`, which no other part in the
  catalog does.
- **A row's focus name is scoped by its list's `id`.** `host.focus` takes a name
  global to the window, so two lists over items with equal identities would each
  answer to the other's `Home`. This is `tree`'s behaviour improved rather than
  copied; what is left of the collision needs two lists, both unnamed, holding an
  item with the same identity.
- **The focus ring stays, where a dropdown's row has none**, and the difference is
  which thing the arrows move. In a `select` they move the *value*, so the
  highlight is always where the keyboard is and a ring would be a second marker
  for one place. Here they move the **focus** and `Enter` chooses, so those are
  genuinely two rows and need two marks — ADR-0063's split, drawn.
- **A `none` list is still walkable and still does not eat its clicks.** §10's
  `none` says what may be *chosen*, not what may be *read*: rows nobody can select
  are still content a keyboard user has to reach, and a row that consumed the
  click would stop a button the item-factory put on it from ever being pressed.
- **The class is `ListView` and the CSS type is `list`**, because a widget record
  named `List` would shadow `java.util.List` in every file that built one —
  including its own, whose model is a `java.util.List`.
- **The showcase's Choosers screen gained it**, above the `select tree=` section
  rather than below, which is also the order that reads: "a tree instead of a
  list" means more after a list. Every `tree` golden is byte-identical, because
  only the enum's package moved.

### Ten thousand rows, and the two spacers that hold them up

- **§10's virtualization is built** ([ADR-0213](adr/0213-a-virtual-list-is-two-spacers-and-a-window.md)),
  which is the promise ADR-0212 shipped an API shape for and could not test. A
  list of ten thousand builds about twenty rows.
- **The window comes from [Located]**, the facility `affix` opened: `clip.top() -
  self.top()` is how far into the model the viewport has reached, because `self`
  is where the list has been *scrolled to* rather than where it was laid out.
- **The spacers are the whole safety argument, not a detail.** Every facility that
  hands geometry to a widget carries the same warning — what it triggers must not
  change what it reports — and a list that built fewer rows would be shorter, be
  told a new position, and oscillate at the frame rate. A spacer's height is
  `rowCount × rowHeight`, so the column adds up to the same total however the
  window moves and the measured node never moves. An arithmetic identity rather
  than a rule anyone has to remember.
- **It takes a row height rather than a flag**, because the height is the one
  thing the widget cannot find out: a stylesheet resolves `--gb-list-row-height`
  and no widget can read a resolved custom property. It is also where the
  precondition becomes obvious — `index × height` is only a position if every row
  is that height, so a list with rows of varying height must not virtualize.
- **`Home`, `End` and the typeahead are what virtualization breaks**, and putting
  them back is most of the work. All three move the focus by *name*, and a name
  resolves against the element tree — so a virtual list asked for its last row was
  asking for a row that does not exist, and `End` did nothing at all. The move is
  two steps now: widen the window, then focus.
- **And the second step is a retry rather than a delay.** The frame loop fires its
  timers *after* the platform pump, so whether the repaint a `setState` asked for
  has been drawn yet depends on the pacer. `Host.focus` returns whether it found
  anything, which is what turns a race into a recoverable one; two attempts, so an
  id naming no row stops rather than re-arming for ever.
- **The tests drive real painted frames**, like `affix`'s, because the window does
  not exist until Yoga has run and the router has captured the result. Setting the
  row height back to zero fails seven of them, which is how the assertions were
  checked for being load-bearing rather than decorative.
- **What it costs: the focused row can be scrolled out of existence.** Wheel far
  from the ring and the focused row leaves the window, is unmounted, and the
  router drops it. Arrow keys are unaffected, because the ring asks the viewport
  to follow; only pointer-scrolling away and then pressing one loses the place.

### A table, which was waiting for a list all along

- **§10's `table` is built** ([ADR-0214](adr/0214-a-table-is-a-list-with-columns.md)),
  and §10 is complete. It leaves ARCHITECTURE §17's deferred list, which had it
  behind virtualization — correctly, as it turned out, though not for the reason
  the entry gave.
- **Most of the work was writing the specification.** The entry was one sentence
  — "deferred; it awaits the virtualization work" — and `design-system.md` §3 had
  no metrics row for it at all. §5 requires a spec **and** a metrics row **and**
  gallery coverage before code, in that order, so all three came first.
- **What it was waiting for was `list`.** A table's rows *are* a list's rows with
  more than one thing in them, so `Table` builds a `ListView` whose item-factory
  returns a row of cells, and the selection models, the typeahead, `Home`/`End`,
  the item context menus and the ten-thousand-row window are inherited rather
  than written twice. `TableTest` asserts the **seam** and leaves the rest to
  `ListTest`, which is the whole argument for composing.
- **A column's width is a number or a share, and flexbox already had both.** A
  fixed column will not shrink; a weighted one is `flex-grow` over a **zero**
  basis, because over `auto` a column of long strings would quietly outgrow its
  weight. One function sizes a header and the cells under it, which is the
  cheapest guarantee that they come out the same width.
- **Sorting is the application's**, and the click reports *what the sort would
  become* rather than which column was hit: which way a second click goes is a
  rule about tables and not something every application should restate. A table
  over a database sorts in the query, which is why the widget does not.
- **A caret slot is kept on every sortable header, drawn or not** — and the
  **golden image is what found that**. Without it, sorting a column takes 16px
  away from that column's own label at the moment the reader clicks it, so every
  header the sort visits shuffles its text. Every assertion in `TableTest` passed
  while that was true; the picture did not.
- **`Box.Mark.Kind.CHEVRON_UP` is new**, a third chevron for the second one's
  reason. Here the two are not decoration but the *value*: a caret pointing the
  wrong way says the column is sorted the other way, which is a lie a rotation
  would have made easy to ship.
- **No rule between the rows, and one under the header.** A grid of lines is
  furniture competing with the data in it, and the row height and the hover wash
  already say where a row begins. The line that stays is a boundary between two
  *kinds* of thing rather than between two of a kind.
- **The showcase has a Collections screen**, which is where the ten-thousand-row
  list and the sortable table live. Its own screen rather than a section on
  Choosers, because the two things worth seeing are *scale* and *sort*: the first
  needs a viewport of its own to be scrolled through, and the second needs
  somewhere to keep the state the sorting is done in.

### The rule that was written and never drawn

- **`table-head` shipped with `border-bottom` and drew nothing**
  ([ADR-0215](adr/0215-a-property-the-engine-drops-is-a-rule-that-does-nothing.md)).
  §8's subset has one `border` and no per-edge longhands, so the engine did what
  it promises — logged at debug and carried on — and the golden was accepted with
  the line missing. §3's metrics row asks for that line.
- **It is the fourth time.** `TODO.md` has recorded it since ADR-0109:
  `border-bottom`, `currentColor` and `margin` were each reached for and not
  found, "all silently ignored… and nothing warns when a declaration is dropped".
  `menubar` documents the same wall in a comment in the stylesheet itself.
- **The rule is a node now** — `table-rule`, a box one pixel tall with a
  background, which is `separator`'s answer to the same problem and the only one
  the subset allows.
- **And the toolkit's own stylesheets are linted.** `SupportedPropertyTest`
  resolves every rule the catalog and the showcase ship through the **real**
  cascade and fails on anything reported as unsupported. The asymmetry is the
  point: an application naming `box-shadow` before it exists must not stop a
  window opening, but the toolkit was being held to that same lenient standard
  against itself.
- **It asserts the behaviour rather than a copy of it.** No list of supported
  properties to drift — it attaches an appender and reads what the cascade
  actually said, so a property added to the engine tomorrow needs no edit here.
- **Custom properties had to be excluded, and finding that out was the check
  working.** `--gb-accent: …` reaches the same branch and is logged the same way,
  because custom properties are the resolver's rather than `ComputedStyle`'s
  (ADR-0049) — so the unfiltered version reported 158 failures on a healthy tree.
- **And the check checks itself**: a third test feeds it `border-bottom` and
  asserts it is caught, because a change to the log's wording would otherwise
  make the other two pass by seeing nothing at all.
- **Two live instances in the whole tree**, and that was all: this one, and a
  `padding-bottom` in the showcase's own sheet.

### The corner that was written and never drawn

- **`group-box-title` asked for `border-radius: 7px 7px 0 0` and got four square
  corners** ([ADR-0216](adr/0216-a-corner-is-four-numbers-and-a-lint-reads-values-too.md)).
  §5's frame is 8px round with a 1px edge and the header fills the top of it, so
  the header's top corners are the frame's less the border and its bottom ones
  are square. The engine resolved one radius per box, dropped the declaration
  whole, and two square shoulders spilled out of the frame's curve. `select
  text-input { background: none }` was the second line in the same log, doing
  nothing for the same kind of reason.
- **A radius is four numbers now.** `Corners` over CSS's 1-4 shorthand, in CSS's
  order. This is the change ADR-0215 declined to make for `border-bottom`, and
  the difference is that a rule under a table header is expressible as a node and
  a corner is not — nothing in this toolkit clips.
- **One drawing serves both**, and the uniform case emits exactly the point
  sequence the single-radius painter always did. Two goldens changed by 31 pixels
  each — the two with a `group-box` in them — and every other golden is
  byte-identical, which is the assertion that a hundred controls did not move.
- **`background: none` is transparent and `background-color: none` is not**,
  which is CSS's own division: `none` turns off the layers in the shorthand, and
  the longhand takes a colour. It is what `border: none` has always done one
  property up, and it is why the field inside a `select` had a fill it was told
  not to have.
- **The lint reads values as well as names now** — and had to start running the
  real cascade to do it. Every colour in the toolkit is `var(--gb-something)`, so
  raw declarations handed to `ComputedStyle` reported 164 failures on a healthy
  tree; the check builds a probe element per selector, chained by parent so
  `select text-input` is a `text-input` inside a `select`, and resolves it
  through `StyleResolver`. The leftmost probe has no parent, which is what makes
  it `:root` and how the theme's custom properties reach the chain.
- **The mistake was not that the log was too quiet.** A dropped value has always
  warned, one level above the dropped property that started ADR-0215, and it was
  read exactly as often. What catches it is a test.
- **ADR-0097's parked question is unblocked**: `SegmentedTest` said the bar's
  inset grid should be revisited "the day a per-corner radius exists". It is not
  revisited — the control draws correctly — but it is now a choice rather than a
  limit.

### A key given back by whoever took it

- **A `menubar` going away could unbind an application's own `Ctrl+O`**
  ([ADR-0220](adr/0220-an-accelerator-is-given-back-by-whoever-took-it.md)). The
  window's map was keyed by the shortcut alone, so giving back what the bar took
  removed whatever was on those keys — including a binding made after the bar
  was mounted.
- **A binding is `(action, owner)` now**, compared by identity, and there are two
  ways to give a key back: by key, which removes whatever is there and is what an
  application means; and by key *and owner*, which is a no-op when somebody else
  holds it and is what a widget means.
- **The bind side did not change.** Two commands on one key is still an authoring
  mistake where the later one wins; the loser simply cannot take the winner away
  with it any more.
- **A displaced binding is not restored**, deliberately: that needs a stack per
  key, and a stack needs an answer for what happens when the middle of it leaves.
- **`menubar` is the only owner in the toolkit**, which is exactly what the entry
  predicted when it was filed.

### Four entries that were one missing callback

- **An `item` could tell its menu one thing** — "the pointer arrived on me" — and
  four `TODO.md` entries were all the sentences it could not say
  ([ADR-0219](adr/0219-an-item-tells-its-menu-what-the-keyboard-did.md)): a
  keyboard `Right` waited out the pointer's 150 ms hover-intent, `Left` closed
  nothing, `Left`/`Right` did not move between a bar's menus, and nothing marked
  the row whose submenu was showing.
- **`MenuSignals` is the sentence**: `hovered`, `open`, `back`, `forward`. Each
  says what *happened to the row*, not what to do about it — which is how `Left`
  means "close this submenu" in one menu and "the menu on the bar's left" in
  another without the row knowing either.
- **A delay is for a pointer.** Hover-intent stops a submenu dropping out of one
  travelling past three rows; a keypress has travelled past nothing, so `open()`
  cancels the timer and opens in the same frame.
- **A bar hands its root menu a `Siblings`** and a submenu gets none, which is the
  whole of why `Left` goes back one level inside a branch and along the bar at the
  top of one. It wraps, and skips a separator or a disabled heading.
- **`Menus` grew an object.** Three of the four fixes need state that lived
  nowhere — which row's branch is open, and which menu is above this one — so an
  open menu is an `OpenMenu` rather than five parameters passed down a chain of
  statics.
- **The mark is read in pixels.** A popup's tree is in another window, so the test
  moves the pointer out of the parent menu and compares the row's own pixels
  before and after: the only thing left on it is the mark. Four of the five new
  tests fail against the old code.

### The paste that took the window down

- **`Paragraph.of` refused right-to-left text and a `text-input` does not choose
  its text** ([ADR-0218](adr/0218-a-paragraph-approximates-bidi-rather-than-refusing-it.md)).
  A user pasting Arabic lost the window: the paste succeeded, the field held the
  text, and the frame that tried to describe it threw. The last crash on
  `TODO.md`.
- **A paragraph never refuses text now.** Bidi text is shaped with the direction
  forced to `LTR`, so the glyphs come back in the order every measurement here
  assumes. The glyphs are right — joining comes from the script, which is still
  guessed — and the *order* is mirrored.
- **Wrong in exactly one way.** Widths, wrapping, carets, hit testing and
  selection all come off the same prefix sums, so a click lands where the caret
  is drawn. What is wrong is the reading order, which is the thing that needs run
  splitting.
- **It says so twice**: `isBidiApproximate()` for a caller, and one warning per
  distinct string for a reader. The alternative to a crash should not be a
  silence.
- **`Font.shape(text, direction)` is the seam the real fix will use**, because
  bidi run splitting *is* "shape each run in its own direction".
- **The test fails against the old paragraph**, which is what says it tests the
  crash rather than the fix.

### The bar that was drawn the other way

- **`segmented` draws §3's row now** — "radius 8 outer, 0 between; 1px divider in
  `--gb-border`" ([ADR-0217](adr/0217-a-segmented-control-is-joined-again.md)).
  ADR-0097 declined it on two grounds: per-corner radii did not exist, and
  nothing clips. ADR-0216 removed the first, and the second turned out not to
  need answering — clipping was only ever needed to cut a square fill to the
  bar's shape, and a fill that rounds its own outer corners already is that
  shape.
- **The bar's padding is its border's width**, which is the whole of the new
  arithmetic: 1px puts the track on the bar's inner box, and the 7 the segments
  and the pill carry is the bar's 8 less that border. Concentric, which is what
  makes a fill lie flat against a rounded edge instead of poking through it.
- **The radius is the stylesheet's and the corners are Java's.** Which cell is at
  an end of the row depends on a count, and no selector can count segments — the
  same argument ADR-0099 used for the cell width. `Corners.inRow` is in `:core`
  because `button.square`'s joined buttons and `tabs` are the next two callers.
- **The divider came back as a node and out of flow.** In flow it would take a
  pixel of the row, and the row is a grid — `(100% - 3px) / 4` is not a
  percentage anything can name, and the travel depends on every cell being
  exactly `1/n`. The two beside the selection fade rather than blink, because the
  pill takes `base` to reach them, and all of them are painted **under** the pill
  so a moving fill never has a line drawn across it.
- **A new golden, `segmented-unset`, is the only image that shows a hairline at
  all**: with three segments and the middle one selected, both dividers are
  beside the selection. That is correct and it is exactly why the image exists.
- **The focus ring left the bar's edge.** ADR-0097 recorded as a coincidence that
  a 2px ring at a 2px offset landed on the border when the segment was inset by
  2; with the segment against the inner edge the ring sits just outside the bar
  and takes the segment's own corners.
- **Two tests were counting past the track's parts** — `children().get(index + 1)`,
  one past the indicator — and there are `n` parts now. Both find an `option` by
  type instead, which is what they meant.

### The gallery that was twelve lists and is seven questions

- **The showcase is a `menubar`, a bar and seven screens**
  ([ADR-0222](adr/0222-a-showcase-is-a-window-a-bar-and-seven-screens.md)).
  ADR-0110's rule for what went where was *one screen per widget family*, and it
  did not survive the catalog reaching fifty-one widgets: `Controls`, `Values` and
  `Text` were three tabs you had to visit in turn to see one screen's worth of
  chrome; `Overlays` and `Notifications` were the two halves of one comparison
  with a tab between them; and twelve screens against ten digits left two of them
  with no accelerator at all, which ADR-0110's own note had admitted.
- **Every screen is a `Wall`** — a heading, a line of prose, and a `masonry` of
  cards. A *type* and not a convention, because it was a convention first and
  four screens had already drifted off it: one had its heading inside the wall,
  one had no prose, two disagreed about whether the caption was `.caption` or
  `.prose`.
- **A document supplies the cards it can and Java appends the rest to the same
  masonry.** `Panes.wallOf` refuses a document whose root is not a `masonry`, and
  that check is the load-bearing one: a `column` wrapped round it during an edit
  is a perfectly good document, nothing throws, and the screen quietly grows a
  *second* wall laid out against different columns. The Java cards are exactly
  the five things markup cannot write — an expression, a list the application
  edits, a set that is toggled, a filter that hands options back, and series
  data.
- **The window opens maximized**
  ([ADR-0221](adr/0221-a-window-may-open-maximized.md)), which is a `default
  false` predicate on `Application` and a `SDL_WINDOW_MAXIMIZED` flag beside the
  size rather than an enormous size instead of one. `WindowSpec` refuses
  maximized-and-not-resizable, because SDL silently drops the flag there and both
  readings of a warning would be wrong. The layout verification caught the
  missing `GB_CONSTANT` in `goldberry_shim.c` on the first run, which is the
  check doing exactly what it is for.
- **The theme is one fact in two spellings, written in one place.** `app.theme`
  is a name because three controls pick from a list; `app.light` is a boolean
  because `Toggle.resolved` reads `source.get() instanceof Boolean` and falls
  back to its own flag otherwise — a switch bound to `"light"` never moves. Both
  are assigned in `pickTheme` and nowhere else, and a test walks every route to
  the theme asserting the two agree after each.
- **`Set.of` is now banned from anything a golden image prints.** Its iteration
  order is randomized once per JVM, so the Collections tree's caption —
  `String.join(", ", checked)` — came out "buckland, weathertop" on one run and
  the other way on the next. A thousand pixels of caption failed the image at
  random; a `LinkedHashSet` fixed it. Three consecutive `--rerun-tasks` runs are
  what confirmed it.
- **`Scrolling` keeps §2.4's nested-scroll ban and stops being a special case.**
  It used to be the one screen the gallery did not wrap in a viewport; it is now
  a card in a two-column wall, and the screen fits without scrolling at all. The
  wall is what made the ban affordable rather than awkward.
- **Section names are one word each**, because a section's name becomes its
  `#section-<name>` and its button's `#jump-<name>`, and `#jump-bag end` is not a
  selector. The slug helper that had appeared to cope with the others went with
  them.
- **`SectionHeader` is public and is the screen title.** `text.screen-title` did
  the job for eleven screens and stopped being honest: a class is something any
  node can wear, a heading is a *kind* of node, and being an element type is what
  lets one rule say "a heading inside an affixed section takes a surface".
  `ShowcaseTypographyTest` asserts its rank through the cascade, which is the one
  place that can see it — every golden image is drawn with the single-font
  renderer and is blind to every typographic rank there is.
- **The content is Middle-earth.** Not decoration: a table of nine companions
  with a `Kindred` column that repeats and a `Leagues` column that does not shows
  a sortable header doing something `Row 1`…`Row 6` cannot, and names of wildly
  different lengths are what a layout has to survive. No text is quoted — the
  prose is written for the purpose.
- **Eleven golden images at 1200×900**, replacing twelve at 900×560, plus
  `gallery-basic-narrow` at 720: a `masonry`'s columns are a count and not a media
  query, so two columns at 1200 are two columns at 720, half as wide and twice as
  tall, and a card with a minimum width would overflow rather than wrap.
- **`WindowActions` survives, and the reason is the interesting one.** It was
  deleted when the menu bar started holding its handlers directly — and put back,
  because `overlays.kdl` presses `app.open-menu` by *name* and only a registry
  can turn a string into a call. The two halves of §9 are now visible side by
  side in one window.
- **Two new test classes**: `WindowSpecTest` in `:core` for the flag and its
  refusal, and `ShowcaseShellTest` in `:example` for the three bands, the seven
  screens, and every row of the menu bar including the one that is honestly
  disabled. Neither is a thing a golden image can show — every picture is drawn
  at a size the test chose, so a window that opened 200px wide would look
  identical in all of them.

### The key that could not be a shortcut

- **A bare `Alt` tap opens the menu bar**
  ([ADR-0223](adr/0223-a-tap-is-a-gesture-and-a-shortcut-is-a-value.md)), which
  is §8's "`Alt`-style keyboard activation" itself rather than the `F10` that had
  been standing in for it since ADR-0163. The entry that tracked it had already
  written the design — "key-release tracking with a nothing-happened-in-between
  rule, at the window level" — and got one thing wrong by omission: *where the
  keycode can still be read*.
- **`Key` names no modifier, deliberately**, so `Alt` reaches the router as
  `Key.UNKNOWN` and is indistinguishable there from every letter that arrives as
  text. `Window` is the last component holding a platform keycode, which is why
  the recogniser lives there and is fed before the `InputWatcher` and the router
  both — a key a popup swallows still has to spoil a tap.
- **A new package, `input.tap`**, beside `input.key` rather than inside it:
  `ModifierKey` is the four modifiers seen as keys that can be tapped, and
  `ModifierTaps` is the detector and its owner-keyed registry. The first thing in
  `input` that is a *recogniser* rather than a value or a dispatcher.
- **The rule is stated as what spoils it**, because that is the half that has to
  be exhaustive: another key, an auto-repeat, a second modifier, a pointer press,
  a wheel, a focus change — and, deliberately not, pointer motion. `Alt+F` must
  not read as a tap of `Alt` followed by an `F`, and the window switcher's `Alt`
  must not open a menu on the way back.
- **`Host` grew `modifierTap`/`removeModifierTap`** with ADR-0220's ownership and
  no unowned overload, because the only reason to bind a tap is a widget that will
  have to give it back. `menubar` now holds two kinds of registration and returns
  both; `F10` and `Alt` both toggle, which is a behaviour change to `F10` and the
  right one.
- **Three test classes.** `ModifierTapsTest` states the rule against the detector,
  `ModifierTapWindowTest` drives the real launcher and asserts each interruption
  separately — a detector that is correct and unwired looks exactly like one that
  is absent — and `MenusTest` taps `Alt` through the real window and the real
  popup, twice, and then proves `Alt+F` leaves the bar alone.

### The click that acts on what it landed on

- **A right-click selects the row it is over before the menu opens**
  ([ADR-0224](adr/0224-a-right-click-selects-what-it-is-over.md)) — every file
  manager's gesture, and one the toolkit had left to applications because it "has
  no notion of what select means for an arbitrary widget". It still has none. The
  widget under the pointer does, and what was missing was a **moment**.
- **The launcher's existing walk is the moment.** It already goes from what the
  gesture landed on up to the nearest widget that named a menu; it now remembers
  the deepest `Selects` it passed and asks it once, immediately before opening. So
  a right-click on a cell targets its row, by the same rule that makes a
  right-click on a button's label a right-click on the button.
- **Nothing is asked when no menu opens**, because a selection that changed with
  nothing to show for it is a gesture with no visible cause. The keyboard's menu
  key shares the walk and therefore shares the rule (ADR-0208).
- **The rule that makes it worth having** is the one an application writing this
  by hand gets wrong: a row already *in* the selection leaves it alone, so
  right-clicking one of five chosen files opens a menu about the five rather than
  collapsing them to one.
- **`Selects` is one method in `input.handler`**, and the first thing in that
  package that is a *request* rather than a report. `ListRow` and `TreeRow`
  implement it in four lines each; `table` inherits it, because a table is a
  `ListView` whose item-factory returns a row of cells.
- **Twelve tests.** Six in `:core` against the real launcher — who is asked, that
  the deepest wins, that only one is, that it happens before the menu and not
  after, that nothing happens when no menu opens, and that the menu key does the
  same — and six in `:widgets` for what a list's and a tree's rows do when asked.

### The one widget nothing would ever have spoken

- **A toast says it is a live region**
  ([ADR-0225](adr/0225-a-toast-says-it-is-worth-interrupting-for.md)), which is
  §7's phrase and a claim `Role` and `accessibleName` cannot make between them.
  Every other widget in the catalog is announced because something *happens to
  it* — the focus lands on a button, a reader walks onto a row — and the reader's
  own cursor is the event. A toast has none: nobody focuses it, nobody has to
  click it, and it is gone in five seconds.
- **`Semantics.live()`**, answering `OFF`, `POLITE` or `ASSERTIVE` and defaulting
  to off, so no existing widget changed. `Role` gained `STATUS` — a region that
  reports what just happened, which is neither a `GROUP` (a boundary with content
  in it) nor a `DIALOG` (somewhere the user is until they leave).
- **`ASSERTIVE` has no consumer**, deliberately: interrupting is for something
  that must be dealt with before anything else, and a toast is dismissible and
  transient by construction. It exists because a vocabulary of two would make
  "polite" look like a default rather than a choice.
- **Rarity is a test rather than a convention.** `SemanticsSweepTest` asserts that
  `ToastBox` is the *only* class in the catalog overriding `live()`, so a widget
  that later decides it deserves interrupting has to go there and say why.
- **Nothing announces anything yet.** The bridge is M5, exactly as for every
  other widget's role and name. What changed is that the remaining work needs no
  decision from the catalog — a toast raised today already carries everything an
  announcement would read.

### The animation no picture could have caught

- **`AnimationSweepTest`**
  ([ADR-0226](adr/0226-a-golden-cannot-see-an-animation-that-never-ran.md)) — the
  second sweep, after `SemanticsSweepTest`, that enforces something no golden can
  show. A golden drives `render` by hand and never asks whether the frame loop
  *would have*, so a widget that answers `isAnimating` with `false` while it fades
  produces perfect pictures of an animation that never runs.
- **Two rules.** A widget holding a `Phase` declares `isAnimating` — structural,
  and scoped to things that implement `Paints`, because a `State` and a value
  record may both hold a phase and neither is asked for a frame. And every
  declaration of `isAnimating` has a test *in its own package* that names the
  method, which catches the animations a `Phase` does not describe: a tab's
  transition is a number, a scrollbar's fade is an idle clock.
- **The second rule is deliberately weak about what is asserted.** An arch test
  cannot tell a good assertion from a bad one; it can tell that there is one,
  which is the difference between finding this late and not at all.
- **It found a gap on its first run.** `ScrollViewport` and `ScrollFade` had no
  `isAnimating` assertion anywhere. `ScrollFadeTest` now covers §2.4's fade curve
  and both ends of the frame contract — that it keeps asking through the idle
  period and the fade, that it *stops* once the bars are gone (the opposite
  failure, and just as real), and that bars held open by the pointer are still
  rather than moving.

### The word the tree did not have

- **`Widget.nothing()`**
  ([ADR-0227](adr/0227-a-widget-may-describe-nothing.md)) — a widget that
  describes no box, no space and no selector. Every `build` has to return a
  widget, so a widget with nothing to show could only draw an empty box (which
  takes no room of its own and is still a child, so a `column` with a `gap` puts
  the gap round the thing that vanished) or be described away by its parent
  (which moves the decision to the application, which is what `bind=` exists to
  spare).
- **No new branch anywhere.** The mechanism was already there: a node that is
  neither `Styled` nor `Paints` and has no children contributes no box, which is
  how every composition node works. What was missing was a name. A singleton leaf
  and a static method — a method rather than a constant because a `static final`
  on `Widget` holding one of its own subtypes is a class-initialisation cycle.
- **It is still an element**, holding its state, its place in the reconciler and
  its subscription. That is the point: a widget that describes nothing this frame
  and something the next is one node whose value changed.
- **`message bind=`**, which was the entry that asked for all this. A blank value
  is no banner; `text` stays the fallback for *no binding*, not for a blank one,
  because an empty error property means there is no error. The dismissed case
  converged on the same word and stopped leaving a gap behind it.
- **`field-message` is deliberately not converted.** Switching its empty styled
  box for a nothing changes the spacing of every form — five golden images say
  so — which is a design decision about §4's "message slot" rather than a bug fix.

### The frame loop that never slept

- **`collapse` and `carousel` ask their `Phase` now**
  ([ADR-0228](adr/0228-a-phase-is-asked-whether-it-is-still-running.md)). §1.7
  promises "the frame loop is fully idle when no animation is active", and it was
  false for any window with an open `collapse` on it and for **any window with a
  carousel at all** — that one reported an animation from its first frame and
  never stopped.
- **The bug is one substitution.** Both handed their moving part a function of the
  clock and decided at *build* time whether there was an animation, so
  `isAnimating` answered "were you built in a state where you could move" rather
  than "are you still moving". A phase settles itself on the frame that finishes
  it; a `DoubleUnaryOperator` closing over one cannot say whether it has.
  `message` was already built the right way.
- **Two things the entry had not predicted.** A section shut half way through its
  arrival keeps an `ENTERING` phase that nothing will ever read again, so
  `CollapseSection` guards on `open`. And `CarouselTest`'s own `animating()` case
  asserted the bug — written against the implementation rather than against §1.7.
- **The wasted frame went with it.** A separate entry recorded, as harmless, that
  every clock-driven arrival costs one frame because the renderer asked
  `isAnimating` *before* drawing. A phase learns it has finished by being **read**,
  and reading happens in `render` — so asking afterwards is one line and one frame
  of every animation in the toolkit.
- **A new `IdleLoopTest`**, asserting on the renderer rather than on a part,
  because the renderer is what the frame loop asks. It also writes down the two
  legitimate reasons a loop stays awake that made it hard to write: a CSS
  transition starts on the frame that *observes* the changed style, and opening a
  `collapse` rotates a chevron under one.
- **`AnimationSweepTest` fired on this change**, naming both widgets the moment
  they gained a `Phase` component — the sweep from ADR-0226 doing its job on the
  first real change after it landed.

### The rank that was missing, not the rank that was unused

- **A semantic hue has four ranks now**
  ([ADR-0229](adr/0229-a-hue-has-a-rank-for-words-as-well-as-for-lines.md)): the
  hue as a fill, `-fill` for words on it, `-line` for a stroke on a surface, and
  `-text` for **words** on a surface. The names say what each is for rather than
  how it was made.
- **The survey that asked for this found the opposite of what it expected.** Six
  rules drew ink in a bare hue and only one was a line — `field:invalid`'s border,
  which now takes `-line`. The other four were words, and §1.2's floor for words
  is 4.5:1 where `-line` is derived against 3:1. Pointing them at `-line` would
  have moved them from clearly wrong to quietly wrong: `--gb-danger-line` is
  3.53:1 on the dark theme's surface.
- **The worst measurement was 2.04:1** — `statistic-delta.up` in the light theme,
  a green nobody can read. The HUD's over-budget red was 3.95:1 on its own plate.
- **The HUD gets its own two tokens**, identical in both themes, beside the
  `--gb-hud-text` and `--gb-hud-bg` that already were: its plate lies over the
  application's colours, so a theme-varying hue is wrong on it — the light
  theme's `-text` red is a *dark* red, and a dark red on a near-black plate is an
  absence rather than a warning.
- **Eight golden images changed**, each of which had been recording a colour below
  §1.2's floor faithfully for months.
- **And the survey became a lint.** `noBareHueDrawsInk` reads `controls.css` for
  `color:`/`border-color:` set to a bare hue. `ContrastTest`'s opening note
  refuses to parse CSS, and rightly — for a *contrast* claim. For a *coverage*
  claim only the source can answer, which is why they are separate tests.

### A notification, an event, and the difference between them

- **`PointerRouter.onPointingChanged` takes a list of listeners**
  ([ADR-0230](adr/0230-a-notification-has-listeners-and-an-event-has-one.md)) and
  hands back a `Subscription`. The entry that tracked it said a second listener
  needed "a decision about what it means for two things to react to one hover",
  and the decision is that there is nothing to decide: what is delivered is a
  **notification**, not an event. Nothing is passed, nothing can be consumed, and
  each listener reads the router for itself.
- **An event would be the thing worth refusing** — one carrying a target, or
  consumable — and is what ADR-0105's objection was aimed at. The rule is written
  down now: if the thing delivered can be *consumed*, one listener; if it is only
  a nudge to go and look, a list.
- **The slot had a bug nobody had noticed.** A setter named `onPointingChanged`
  reads like a registration and behaved like an assignment, so a second caller
  silently dropped the first — a tooltip that stops appearing, with nothing
  anywhere saying why.

### The menu that stayed where the window used to be

- **A popup is placed again after a resize**
  ([ADR-0231](adr/0231-a-popup-is-placed-again-when-its-anchor-moves.md)), which
  is what `Popup.move` had been waiting for since ADR-0104.
- **Half the entry's premise was wrong**, and finding out which half is most of
  the work. A popup sits at an **offset from its owner**, so *moving* the window
  carries it along — the platform does that. A **resize** moves the thing it was
  anchored to, and nothing told it.
- **An id is worth more than a rectangle.** A popup opened against an anchor id
  re-resolves it against the frame the resize produced, so it follows a heading
  that moved; one opened against a caller's rectangle keeps that rectangle,
  because the caller said where.
- **It happens at the end of the next paint, not in the resize handler** — the
  part that is easy to get wrong and impossible to notice. `anchor(id)` answers
  from the capture the *last* paint produced, which during the resize handler is
  still the old window's: re-placing there would put every menu back where its
  heading used to be.
- **The test fails by exactly 200 pixels without the fix**, and writing it found a
  trap worth recording: a run bounded by `--frames` finishes in whatever
  wall-clock time the machine takes, so a callback scheduled 300ms out can arrive
  after the loop has gone and read a live-looking `anchor()` from a dead launcher.
  It schedules by **turns of the event loop** instead.
- **What is still open** is written into the entry: a window *move* does not
  re-clamp, because there is no `BackendEvent.Moved`; and a `popover` does not
  follow a scrolling anchor, because the anchor is what would have to report it.

### The modal that trapped the keyboard and let the mouse through

- **Modality is one flag**
  ([ADR-0232](adr/0232-modality-is-one-flag-and-not-a-scrim.md)). It was two
  mechanisms, and `Handles.isModal` said so in as many words: "the pointer is not
  this flag's business" — a dialog is unreachable by mouse because its *scrim*
  covers the window. That is modality by geometry, and a widget that declared
  itself modal without a scrim trapped the keyboard and let every click through.
- **The rule now**: while a modal is mounted, the pointer reaches its **subtree**
  and its **ancestors**, and nothing else. The ancestors are the point rather than
  a loophole — a scrim is the panel's *parent*, and a click on it is what closes
  the dialog. An ancestor is on the path from the modal to the root; a button in
  the application is neither on it nor inside the modal.
- **Enforced in `elementAt`**, the one place every pointer entry point resolves a
  target, so presses, releases, wheels and **hovers** obey it together — a control
  behind a dialog that lit up under the pointer would claim to be pressable when
  it is not.
- **The paint-order rule is written down too.** The topmost painted region taking
  the pointer was already true and unasserted; it is on `elementAt` now with a
  test that fails if it stops being.
- **Found once per frame**, beside the regions, which is ADR-0054's rule applied:
  input is answered against the frame the user can see, so the tree that frame
  came from is the tree to ask — and one walk per paint rather than one per mouse
  move.
- **The test's overlay deliberately does not fill the window**, which is what
  makes it a test of the rule rather than of the geometry: a filling scrim takes
  every press whether or not anything is modal.

### The submenu that took its parent with it

- **`Escape` closes the innermost popup; a press outside closes the stack**
  ([ADR-0233](adr/0233-escape-steps-out-of-one-menu.md)). The two gestures mean
  different things and the launcher had been running the same code for both, so
  opening `File → Recent` and pressing `Escape` closed the menu as well as the
  submenu — and there is nothing to reopen it with but the mouse.
- **Finding which handler was at fault was most of the work.** A `Popup` watches
  its own window and closes only itself, which is correct and never runs: since
  ADR-0189 no popup holds the platform keyboard, so `Escape` arrives at the
  **owner** window, whose watcher dismissed everything.
- **The innermost popup is not always the one that goes.** A tooltip is
  `lightDismiss(false)` and refuses, so the walk looks past it rather than
  stopping — otherwise `Escape` would do nothing with a menu open underneath.
  `dismissedByInput` reports whether it closed, which is what makes that
  expressible.
- **Focus loss still closes everything**, because the application is no longer in
  front and there is no chain to step out of.

### The controller that turned out to be a timer and an ordering

- **The overlay lifecycle survey is done**
  ([ADR-0234](adr/0234-the-overlay-lifecycle-is-a-departure-and-a-phase.md)), and
  the answer is two objects rather than one controller. §1.7's `opening → open →
  closing → removed` was a specification with no subject until the widgets it
  describes existed; they do, and the table of how each of the seven arrives and
  departs is what settles it.
- **The arrival needs nothing shared.** `Phase` is already the whole of it — a
  beginning stamped on the first frame that draws, a duration and a settle — and
  six widgets use it without wanting more.
- **The departure was the same code twice.** `dialog` and `message` each held two
  flags, a timer and six lines, and independently got the same four rules right:
  idempotence (two handlers on a save dialog is two saves), two flags that mean
  different things (using one for both is why a closing dialog once never faded),
  stop-drawing-before-telling (it matters for one frame), and gone-at-once with no
  host or under reduced motion.
- **`Departure` is that, and it is still not an `AnimationController`.** ADR-0081
  refused one for `spinner`, ADR-0178 refused one for a toast's reflow; this is
  what was left after both. It drives no value, interpolates nothing and owns no
  clock — it owns a timer and an ordering, which is the part that was duplicated.
- **`toast` and `tab` are deliberately not converted.** A toast's departure ends
  when its stack's queue says so and a tab's ends inside `render`; forcing them
  through this would be ADR-0092's warning about generalising from two examples
  that already agree.
- **The refactor is behaviour-preserving**, which the dialog and message suites —
  golden images included — say by passing unchanged. `DepartureTest` is eleven
  cases: one per rule, and one per way a rule was once broken.

### A wrong reason, repeated in four places

- **"Nothing in this toolkit clips" was false**
  ([ADR-0235](adr/0235-a-cut-label-needs-nowrap-not-text-overflow.md)). `option`,
  `select-value`, `ProgressFill` and a `TODO.md` entry all said it in almost the
  same words; `overflow: hidden` has shipped since ADR-0114, is read by Yoga *and*
  the painter, reaches hit testing, and is used by `text-input`, `text-area`,
  `scroll` and four CSS rules.
- **So clip a menu row and be done — except it does not work**, and why is the
  finding. `Box.text` is a **measured leaf**: narrowing the box it is in
  re-measures the paragraph at the narrower width, so the label **wraps** instead
  of overflowing and there is nothing left to clip. That is why ADR-0148's fix was
  `flex-shrink: 0` rather than a clip.
- **Three attempts, all recorded.** Clipping the row cropped the showcase's 20px
  icon in its 16px column (a golden caught it in one run); a shrinking clip box
  around the label reintroduced the wrap ADR-0148 had fixed; adding
  `align-items: center` to that box fixed a *different* bug found on the way — a
  `Box.of()` wrapper defaults to Yoga's `column`, where `align-items` is the
  horizontal axis — and did nothing about the wrapping.
- **The missing property is `white-space: nowrap`, not `text-overflow`.** With it a
  clip works and an ellipsis becomes reachable; without it no arrangement of
  `overflow` and `flex-shrink` can cut a label, because the label is never too long
  for the box it is in.
- **No behaviour changed and four comments did.** Shipping `overflow: hidden`
  where the build stays green only means no golden covers a label that long —
  which would have risked turning an overflow into a two-line wrap with nothing
  demonstrating an improvement. Two `TODO.md` entries keep their subject and lose
  their reason.

### The wheel that stopped at the first thing that could hear it

- **A knob consumes what it moved, and nothing else**
  ([ADR-0236](adr/0236-a-wheel-is-consumed-by-whatever-it-moved.md)). Two
  `TODO.md` entries had been holding this open since ADR-0089 from either end —
  "a knob inside a scroll view is still untested" and "`Kind.WHEEL` had exactly
  one consumer, and it showed" — and they close together, because they were the
  same fact stated twice.
- **There were two rules for one event, and only one of them was written down as
  a rule.** `ScrollViewport` had answered it in ADR-0116 — "returns whether
  anything actually moved, which is what the caller turns into consuming the
  event, and therefore what decides whether an ancestor scroller gets a turn" —
  while `Knob.wheel` consumed everything it was handed. With nothing above a knob
  for an unconsumed wheel to reach, the difference could not show. Put a knob in a
  list and it shows at once: pinned at its maximum it swallowed every upward
  scroll and the list stopped dead under the pointer.
- **The comparison is against what `ask` would pass on**, not against the raw
  arithmetic, and that is the detail that decides whether the rule is right or
  merely plausible. A stepped knob two from its end on a grid of five still moves
  those two — `snap(clamp(98 + 5))` is 100 and differs from 98 — where comparing
  the raw 103 against the maximum would have thrown the last part-step away.
- **Only the direction with nowhere to go chains.** A knob at its maximum still
  takes a wheel that turns it down, so a control being used does not let the list
  lurch out from under it halfway through. And a knob nobody is listening to is
  not a place a scroll stops: `disabled`, or a null `onChange`, means the value
  cannot change, so the event is not consumed.
- **`KnobChainingTest` is the first test in the catalog to drive a wheel through a
  real bubble between two widgets** — four cases through the real router against
  painted regions. The arrangement is what took the work: the list is **scrolled
  off its top before every case**, because the direction a knob at its maximum
  rejects is the one that scrolls a list *up*, so against a list left at its top
  the viewport's own edge rule would have refused the wheel and the test would
  have passed before the fix for a reason that had nothing to do with it.
- **Three of the new assertions were checked against the old code** by
  neutralising the range comparison and re-running, which is the only thing that
  distinguishes a test of this shape from one that never ran.
- **What it turned up and did not close**: a *disabled* control swallows a wheel
  outright, because `PointerRouter.dispatch` returns before the chain is built
  when the target sits in a disabled subtree — so the `scroll` above it never gets
  a turn. Measured, not deduced. That cut is ADR-0059's, it is right for a click
  and wrong for a wheel, and whether it should be per event kind is a decision
  about the router rather than about `knob`. It is in `TODO.md`.

### The shape that was a function of the last motion

- **The cursor now follows the frame, not only the pointer**
  ([ADR-0237](adr/0237-the-pointer-state-follows-the-frame.md)). A `TODO.md`
  entry had carried its own fix since ADR-0057 — "re-run `cursorAt` after each
  paint against the last known position" — with the condition "it is worth doing
  when something can actually change that way". Something can:
  `cursor: not-allowed` ships on every disabled control, and the sequence that
  reaches it is a button disabling itself in its own press handler while the
  person deciding whether to click holds still.
- **What was missing was a place to remember where the pointer is.** The router
  had three position fields and all three are gesture-scoped: `pressOriginX`/`Y`
  span a press-to-release and are `NaN` outside one, which is precisely what made
  them useless here. The fourth outlives a gesture and is set from every entry
  point that carries a position — moved, pressed, released, wheeled — so a window
  whose first event is a click is not left with nowhere to ask about.
- **`NaN` means "we do not know", twice**: before the pointer has ever arrived,
  and after it has left, which is another window's pointer or none at all. Both
  skip the recompute rather than asking about a point the pointer is not at. The
  capture freeze is reached *through* rather than around, so a repaint during a
  drag does not thaw the shape mid-gesture.
- **Measuring it turned up a second half the entry did not name, and a comment
  that denied it.** `:hover` and `:active` had exactly the same staleness, while
  `mark` said "a control that was hovered before it became disabled does not keep
  the state — which is a real sequence, because a button commonly disables itself
  in its own press handler". It did keep it. Clearing is not suppressed, but
  nothing called it: `updateHover` returns early when the element under the
  pointer has not changed, so the wash survived every later move *within* the
  control and went away only when the pointer left it — on the exact sequence the
  comment named as the reason it was safe.
- **So it is one defect and not two.** Fixing only the cursor would have shipped a
  control drawing its hover wash while its cursor said `not-allowed`, which is
  more confusing than either mistake alone, and §2.1 already says a disabled
  control must not light up — no decision left to defer.
- **`restate()` is `mark(…, true)` and nothing else**, because `mark` already
  knows the rule: a set on a disabled element becomes a clear, so re-asserting
  what the pointer is over sets the state where the control is live and takes it
  away where it is not, in one call with no second branch. **No `ENTERED` or
  `EXITED` is emitted** — nothing entered or exited anything, and a tooltip
  opening because a list repainted would be a worse bug than the one being fixed.
- **This runs once per frame rather than once per motion**, which makes the
  edge-triggering in `setCursor` and `setPseudoClass` load-bearing in a way it was
  not before. A 120 Hz repaint over a still pointer is 120 comparisons and no
  platform calls, and a test asserts it.
- **Eight cases, three of them checked against the old code.** `mark`'s comment
  is now true, which matters more than it sounds: it was describing an intention
  as an achievement, and that is the kind of comment that stops the next person
  looking.

### The gesture that was never about the control it was over

- **A wheel chains past a dead control**
  ([ADR-0238](adr/0238-a-wheel-chains-past-a-dead-control.md)), which closes the
  entry ADR-0236 had opened one commit earlier. A disabled knob in a scrolling
  column stopped the list dead under the pointer, and no widget could fix it:
  `dispatch` returned **before the chain was built** when the target sat in a
  disabled subtree, so the `scroll` above was never offered the event.
- **The cut is ADR-0059's and its argument is about the thing being aimed at.** A
  click on a disabled button must not become a click on the row holding it, and a
  disabled control still hit-tests so a click cannot fall through to what is
  painted behind it. Both stay. What the argument does not cover is a **wheel**,
  which is not aimed at a control at all — it is aimed at whatever scrolls, and a
  browser, GTK and Qt all deliver it to the scroller. Nobody puts the pointer on
  a dead control in order to scroll; they put it on the list, and the dead
  control happens to be under it.
- **So the disabled cut is per event kind, for one kind.** A press, a release and
  a click are refused exactly as before; a wheel builds the chain and **drops its
  disabled prefix**. The dead subtree still handles nothing — the trimmed chain
  never reaches it — and what changes is only who gets a turn afterwards.
- **The prefix is a fact rather than an assumption.** The chain is deepest-first
  and `isDisabled` walks *up*, so it is true from the target to the outermost
  disabled ancestor and false at every step above; `dropWhile` is exact in one
  pass. A wholly disabled tree trims to nothing, which is the old behaviour
  reached by the new route.
- **`isInput` is untouched.** Taking `WHEEL` out of "the user *doing* something"
  is a one-character diff and the wrong one: the same predicate decides whether
  the disabled subtree is skipped at all, so the disabled knob would have started
  turning.
- **Why nothing caught it, which is the part worth keeping.**
  `DisabledPropagationTest` has covered "the wheel is refused too" since ADR-0077
  — against a disabled `form` holding a `button` and **nothing above it**. With
  no live ancestor, "the subtree refuses the wheel" and "the wheel is swallowed"
  produce identical logs. The old assertion is unchanged and still passing,
  because what it actually claims is still true.

### The floor nobody was standing on

- **§1.2's non-text half is measured**
  ([ADR-0239](adr/0239-a-mark-is-measured-against-the-box-it-is-drawn-in.md)).
  `ContrastTest` has enforced 4.5:1 for text since ADR-0087; the other floor —
  3:1 for anything that is *not* text — had nothing behind it, and ADR-0088's
  argument that the accent ramp did not need to move rested on exactly that
  unenforced number.
- **The open question had a simpler answer than it looked.** "What counts as the
  background of a mark drawn onto its own box" is **its own box**: a mark takes
  the `color` of the element it is drawn in, and that element supplies its own
  `background`. For every mark in the catalog *the same rule sets both* — a
  checked tick is `--gb-checkbox-mark-checked` on `--gb-checkbox-bg-checked`,
  both from `check-indicator:checked`. So a pair is one `ComputedStyle`'s two
  properties, and this stays a cascade test like the sweeps beside it.
- **Three sweeps, because there are three shapes of question**: a mark against
  the box it is drawn in (nine pairs), a ring against the surface behind it (the
  focus ring and the spinner, each on all three surfaces), and a control against
  that surface.
- **The last one is a maximum, and that is the part that took thinking.** A
  control offers two means of being identified at once — a fill that differs from
  the surface and an edge around it — and §1.2 asks that *some* means clears the
  floor, not that every one does. Measuring the two separately was the first
  version and it reported `--gb-border` failing on every surface in both themes,
  which is a decorative divider doing exactly what a 1px separator is meant to
  do. The maximum tells the two roles of one token apart without needing two
  tokens.
- **Nineteen pairs are below the floor**, and they are **recorded rather than
  fixed**. `KNOWN_FAILURES` is empty because ADR-0088 fixed the seven text pairs
  it found; the same move is not available here, because every one of these is a
  theme colour and sliding a ramp changes what the toolkit looks like — a design
  decision with a golden-image tail rather than a test's to take. They sit in
  three exact-set lists on `KNOWN_FAILURES`' terms, each carrying its
  measurement, so none can be parked quietly and any that gets fixed fails the
  test until it is taken out.
- **The worst is §2.2's focus ring**, below 3:1 on all three surfaces of the
  light theme (1.74, 2.00, 1.64) — the one mark in the system with no second
  means of being seen. Twelve of the nineteen are control boundaries, and their
  shape is one fact: `--gb-checkbox-bg` **is** `--gb-surface-2` in the dark
  theme, so an unchecked box on a `group-box` differs from its backdrop by
  nothing at all.
- **The class comment stopped saying "the exemption list is empty."** It was true
  of the text sweep and is now only true of the text sweep, which is the same
  kind of overstatement ADR-0237 had just finished correcting in `mark`.

### The ring that had no picture of itself

- **`--gb-focus` follows the accent on the light theme**
  ([ADR-0240](adr/0240-the-ring-follows-the-accent.md)), which pays the first and
  worst of the nineteen debts ADR-0239 recorded a commit earlier. §2.2's ring was
  1.74:1 on `--gb-bg`, 2.00:1 on `--gb-surface` and 1.64:1 on `--gb-surface-2` —
  below §1.2's floor on every surface the theme paints.
- **It was the one to fix first for a reason that is not the size of the number.**
  A focus ring is the only mark in the system with **no second means of being
  seen**: a control that is hard to make out still has its label, its shape and
  its position, and a keyboard user who cannot see the ring has nothing.
- **The cause was a ramp left behind rather than a colour anyone chose.** Both
  themes set the ring to their accent — except the light theme's accent had
  already moved down the Frost ramp from `--nord8` to `--nord10` *for contrast*,
  and the ring kept the pale one. Setting it to `--nord10` gives 3.50, 4.03 and
  3.31, and it is a palette value rather than an invented one, which the theme
  files' own two-tier doctrine asks for.
- **The gap it exposed is the more useful half.** Changing a shipped colour moved
  **no golden at all** — not because the change is invisible, but because every
  focus golden in the catalog is `NORD_DARK`: `segmented-focus`, `menu-focus`,
  `menubar-focus`. §2.2's ring had no picture of it on the one theme where it was
  broken, which is why nothing caught it and why nothing would have caught it
  coming back. `segmented-focus-light` is new and is the catalog's first.
- **`ContrastTest`'s exact-set lists worked on their first use.** Emptying the
  token without emptying `RINGS_BELOW_FLOOR` failed the build, which is exactly
  the property ADR-0239 built them for: a pair that gets fixed fails the test
  until it is taken off the list.
- **Sixteen non-text pairs remain**, in `MARKS_BELOW_FLOOR` and
  `BOUNDARIES_BELOW_FLOOR`. They are control fills and one accent-on-border pair,
  they move goldens in bulk rather than one at a time, and `TODO.md` carries them
  as a single entry for that reason.

### The guarantee that stopped where the extensibility began

- **A theme can be audited by whoever wrote it**
  ([ADR-0241](adr/0241-a-theme-can-be-audited-by-whoever-wrote-it.md)).
  `ContrastTest` has measured the two themes the toolkit ships since ADR-0087,
  and §10 lets an application replace every alias token — so §1.2's promise
  stopped exactly where §10's extensibility began: the toolkit guaranteed legible
  colour, handed the application the means to replace all of it, and then had
  nothing to say.
- **`css.contrast` is a new package in `:core`, and exported.** `:core` because a
  theme is, and because an application should not have to depend on the widget
  catalog to find out its colours are unreadable. Its own package because it is
  neither a stage of the engine nor a value type — it is a question asked *about*
  a resolved cascade, which is the shape ADR-0172 gave the other four.
- **The pairs are found by convention rather than listed**, and this is the
  decision the entry did not anticipate. A hard-coded list of the toolkit's own
  pairs would check a custom theme's *overrides* and miss everything it added.
  The design system already names pairs consistently, so the rule is every
  `--gb-<name>-bg` with a matching `--gb-<name>-text` — and an application
  following the same convention for `--gb-mycard-bg` is checked for free. The
  surface pairs are stated beside it, because `--gb-text` on `--gb-bg` is the one
  relationship the convention cannot express.
- **Two details decide whether it works on a real theme.** Values are
  **substituted**, for ADR-0195's reason: a theme written the ordinary way says
  `--gb-badge-warning-bg: var(--gb-warning)`, and reading raw tokens would decide
  that is not a colour, skip the pair, and audit a real theme as having nothing
  to check. And a **translucent** pair is skipped rather than scored, because
  what it composites over decides the answer — `--gb-hud-bg` is `#1c212ae6` and
  is the shipped example, with a test saying so, because the rule is only
  credible if the toolkit's own tokens are subject to it.
- **Both shipped themes audit clean at seventeen pairs each**, asserted as a
  count as well as a set: a sweep that quietly stopped finding pairs would
  otherwise pass by measuring nothing at all.
- **`ContrastTest` lost its private arithmetic and its two literal floors.** They
  are `Contrast`'s now, so the number CI asserts and the number an application
  audits against cannot drift apart — an audit and a sweep that disagreed would
  be worse than either alone.
- **It deliberately does not cover the non-text floor.** `NON_TEXT_FLOOR` is
  exported and unused here: which token is a *mark* is not something a naming
  convention can tell, and the sixteen non-text pairs below the floor stay
  `TODO.md`'s.

### The unit that meant one number everywhere

- **`em` is the element's own computed font size**
  ([ADR-0242](adr/0242-em-is-the-elements-own-size.md)), which closes an entry
  open since ADR-0066. `CssLength.Context` was always the right shape —
  `(fontSize, rootFontSize)`, one read by each unit — and nothing ever built one
  **per element**: `WidgetRenderer` holds a single instance for the whole tree
  and hands it to every `ComputedStyle.of` call, so `em` was one constant at
  every depth.
- **Two passes, because CSS has one exception.** `1.2em` on `font-size` means "a
  fifth larger than my parent", since the value being computed cannot be its own
  input; on anything else it means "a fifth larger than my own text". One pass
  with one context cannot say both. `font-size` is resolved first against the
  parent's size, everything else against the size that produced.
- **It needed no plumbing.** The parent's size is `parent.typography().size()`,
  already passed in for inheritance — the fix is entirely inside the method that
  had been given everything it needed all along. The undeclared case needs no
  branch either: a node that says nothing has whatever it inherited, which is
  exactly what `em` should resolve against.
- **Measuring it turned up a number the entry did not mention.**
  `CssLength.Context.DEFAULT` is `(16, 16)` and `Typography.INITIAL`'s size is
  **13**. So `1em` was not the parent's size, not the element's own, and not any
  size the toolkit actually renders text at — two constants with no relationship
  and nothing making them agree.
- **`Transform` was the same bug in a second place**, and had said so in a
  comment naming the gap: it reached for `Context.DEFAULT` directly. It takes a
  `Context` now, threaded from `ComputedStyle.with`, which had one all along —
  two public call sites, both in `ComputedStyle`.
- **No shipped rendering changed, and that was checked rather than assumed.** Not
  one `em` or `rem` appears in `nord-dark.css`, `nord-light.css`, `controls.css`
  or the showcase's sheets, and the golden corpus passes untouched.
- **One existing test changed meaning and was rewritten.** "em multiplies the
  font size in force" passed `Context(20, 16)` with no parent and asserted 30 —
  the old semantics, on an element whose computed size was 13. It declares
  `font-size: 20px` now and asserts the same 30 for a reason that is true.
- **What is left is `rem`**, which reads the *configured* root size rather than
  the root element's computed one. They agree unless a root declares a
  `font-size`, and nothing in the catalog does; recovering it needs a third thing
  threaded down, because a node is handed its parent's style and not the root's.

### The warning that was a stream

- **A missing token says itself once**
  ([ADR-0243](adr/0243-a-missing-token-is-a-message-not-a-stream.md)), which
  closes an entry open since ADR-0121 and applies ADR-0216's answer one stage
  earlier in the same pipeline. A stylesheet is **static**, so a `var()` that
  resolves to nothing cannot resolve on the next frame either — but a style is
  resolved per element per invalidation, so one missing token reported itself
  sixty times a second for as long as the screen it was on kept moving. Two of
  them survived long enough to reach a user, which is what a log nobody can read
  costs.
- **The mechanism is `ComputedStyle`'s, and the field is not.** That one is
  static, because a record with static factories has nowhere else to put it, and
  it needs a public `forgetReportedDrops()` so tests in two modules can clear it.
  A `StyleResolver` is an object, built per stylesheet set and living as long as
  its renderer — so **once per resolver is once per stylesheet**, which is what
  the entry asked for and comes out better three ways: a theme swap reports
  again (what the *new* theme is missing is news), a test is isolated by
  constructing its own, and nothing leaks between unrelated sheet sets in one
  JVM.
- **Keyed by property and element type**, which refines the entry's "per
  property". The same token failing on `button` and on `text` is two facts, and
  which types it reaches is the blast radius somebody debugging it wants —
  bounded either way, because a stylesheet has finitely many declarations and a
  tree finitely many types. A **cycle** is keyed by the property name alone,
  because a custom property referring to itself is a fact about the property and
  not about whichever element asked first.
- **`substitute` and `expandVar` stopped being static**, so the cycle report
  could reach the field. Neither had a caller outside the class, so the change is
  invisible.
- **The assertion is a counter, and the comment says why.** Only `slf4j-api` is
  on the classpath, so there is no appender to read the log back from, and a
  logging backend bought for one assertion would be a dependency this does not
  need. `reportedDrops()` is package-private for exactly one test.
- **`descend` walks depth, not siblings**, which two of those tests got wrong
  first and which cost a `NoSuchElementException` to find out. They build a fresh
  `window > type` tree per case now.
- **The drop itself is unchanged.** Only the report is once — making the drop
  conditional would be a stylesheet that behaved differently on the second frame.

### The property the document already claimed

- **`align-self` resolves and reaches Yoga**
  ([ADR-0244](adr/0244-a-child-may-say-where-it-sits.md)), which closes an entry
  open since ADR-0111 and takes one of the two things `stack` is blocked on.
- **The entry was wrong twice, in the toolkit's favour.** §8's layout list reads
  `align-items/self/content` and the sentence naming what is unimplemented said
  only `flex-basis` — so the *document* claimed this worked, and what was missing
  was the implementation rather than the sanction. And `Align.AUTO` was already
  waiting: the enum's own comment says "`AUTO` only means anything for
  `align-self`", a value that existed for a property that did not.
- **The price the entry quoted was real and already insured.** 47 positional
  argument lists across two records — 22 withers on `ComputedStyle`, 25 clean
  sites on `Box` — and `alignItems` and `alignSelf` are **the same type**, so a
  swap between them compiles, runs, and is wrong. `RecordWitherTest` has existed
  since ADR-0181 for exactly this: it asks every wither to set its component to
  the value it already holds and requires the record back unchanged, which no
  transposition survives. Its premise is that no two components of one type hold
  equal values, so the fixtures give `alignItems` `FLEX_END` and `alignSelf`
  `CENTER`.
- **The clean sites were scripted**, one identifier per argument, inserted at a
  fixed index; the four carrying inline commas were edited by hand. A test
  written the last time somebody paid this price is what made that safe.
- **Five layout tests, four of which fail against the old code**, asserted
  against **Yoga's own output** rather than against the record — the property is
  one line in `RenderObject` and the whole risk is whether that line runs, so a
  test reading `box.alignSelf()` back would pass on a box nothing laid out.
  `auto` is asserted indistinguishable from saying nothing, and beside it a check
  that a non-`auto` value really does move the child, because the reason those
  two agree must not be that nothing is wired at all.
- **No golden moved**, because nothing in the catalog declares `align-self` yet.
  That is the honest state of a property added for the widget that will want it —
  the tab strip's `+` is the case that found the gap, and changing it is its own
  diff.

### Three answers, and one of them was a question about CSS

- **`--gb-surface-2` stays**
  ([ADR-0245](adr/0245-the-second-surface-stays-and-says-so.md)). The entry had
  asked whether it should keep existing after three widgets mistook it for an
  elevation, and said that needed a look at what still reads it. The look found
  **five** readers and not one wants a direction: a default `badge`'s fill, a
  `scrollbar` on hover, a `group-box-title` band, a `skeleton-bar` and a
  collapsed `split-divider`. All five want a plate merely *distinct* from what is
  under it, which is what the token promises — the three that were wrong wanted
  "raised" or "sunken" and have their own tokens now.
- **The trap is asserted rather than described.** `ThemeTest` holds
  `--gb-surface-raised` to never being darker than `--gb-surface` and
  `--gb-surface-sunken` to never being lighter, on both themes — and asserts that
  **`--gb-surface-2` takes opposite directions in the two files**, up on dark and
  down on light. That is exactly why each of the three consumers looked right to
  whoever wrote it and wrong to everybody on the other theme. When a class of
  mistake has happened three times, the test to write is not one that checks the
  three fixed sites but one that checks the property they violated.
- **Text has a capture phase**
  ([ADR-0246](adr/0246-text-has-a-capture-phase-now-that-something-wants-one.md)),
  and the entry's own condition for adding one was met. It had named the fix —
  `Handles` had an `onKeyCapture` and no `onTextCapture` — and refused to build it
  on spec, "because a capture phase is a routing rule and inventing one for a
  single consumer is how a router grows two". `select`'s open list is the
  consumer: it lives in a second window with its own router and an `option`
  focused, so the letters stopped at a row that does not know what typing means.
- **It removes an asymmetry nobody had written down.** `dispatchKey` has captured
  root-first and then bubbled since the beginning; `textInput` only bubbled. One
  event kind had a phase the other did not, for no recorded reason. `SelectList`
  now reads letters on the way down and calls the **same** `typeahead` the closed
  control calls, so `n`, `n`, `n` cycles the same options in the same order
  either way — one implementation rather than two that drift. Blank text is left
  alone, because a space in an open list means "pick this one" everywhere else.
- **`start` and `end` are taken, because they are not aliases**
  ([ADR-0247](adr/0247-start-is-css-and-flex-start-is-yoga.md)). The entry called
  them CSS's aliases and left acceptance open; the word is what decided it.
  `align-items: start` is **CSS** — Box Alignment Level 3 — and Yoga has only
  `flex-start`, so this was not a toolkit picking one spelling among two
  conveniences but one **dropping a declaration the specification allows** and
  telling the author they had made a typo. It filled the Panels screen's console
  for long enough to need deduplicating before anybody asked whether the
  declaration was actually wrong.
- **Two entries, applied after the enum's own lookup**, so a constant named
  `START` could never be shadowed by a mapping written for a different enum.
  `left` and `right` stay refused for a reason rather than an omission: they are
  `justify-content` only and are *not* `start`/`end` under RTL, so §2.4's bidi
  support means the toolkit cannot promise they stay equivalent.
- **Two tests changed meaning and were rewritten**, both in the group that exists
  *because* of this typo: its example of "a value the toolkit has not got" was
  `align-items: start`, and now has to be one it really has not got.

### Two caches, and what each was actually comparing

- **Only the inherited half is handed down**
  ([ADR-0248](adr/0248-only-the-inherited-half-is-handed-down.md)). ADR-0142
  stopped a node handing its children a new style instance for an unchanged
  value; what it compared was the **whole record**, including the transform — so
  a `scroll` moving an offset re-resolved every node inside the viewport on every
  frame of a gesture, for a change none of them could see.
- **The notion the entry wanted already existed.** It said the fix "needs a
  notion of which properties inherit, which the cascade has and `ComputedStyle`
  does not" — but `inheritingFrom` is exactly that list and is two lines, `color`
  and `typography`, with a comment enumerating what is deliberately *not* there.
  What was missing was reading it twice, which is what `inheritsSameAs` does,
  beside it, so a property that starts inheriting has to be added to both.
- **The difficulty was not the comparison.** `stableStyle`'s return did two
  unrelated jobs — the children's cache key *and* what the node paints — so
  loosening it in place would have handed back an older instance carrying last
  frame's transform and then painted with it: a scrolling viewport frozen at its
  first offset while every child cached happily. Two variables, because there are
  two jobs, and the test for it was written against the mistake. Folding the
  roles back together fails it, which was checked rather than assumed.
- **A rule that can name a type, does**
  ([ADR-0249](adr/0249-a-rule-that-can-name-a-type-does.md)). ADR-0152's saving
  is that a rule for `button` is never looked at for a `text`, and it is worth
  what the stylesheet lets it be. The entry assumed the toolkit's own sheets were
  type-first; measuring found **16 of 340** rules naming none, in two families
  rather than a scattering.
- **Seven were `tour`'s parts**, and every one *was* matching a known type
  without saying so: the tour builds them from plain `Text` and `Button` widgets
  carrying a class, so `text.tour-title` matches exactly what `.tour-title`
  matched and lands in a bucket. Seven rules, one word each, and **no golden
  moved** — which is the evidence it changed what the cascade looks at rather
  than what it finds.
- **The other eight cannot be qualified and should not be**: the typography scale
  is seven ranks an application puts on whatever it likes, which is what makes it
  a scale rather than a widget's parts, and `:root` is the theme's token layer.
  `RuleBucketTest` holds them as an **exact set** rather than a threshold, and
  reports the *selectors* rather than a count — the difference between a failure
  that names `.tour-title` and one that says a number went up. Beside it, an
  assertion that the sheet is large and nearly all of it bucketed, because a
  check listing eight selectors would pass against a stylesheet of eight rules.

### The widget that turned out to be nine lines

- **`stack` is built** ([ADR-0250](adr/0250-a-stack-is-one-child-in-flow.md)),
  which closes §1's last core-group gap but `image` and an entry whose own final
  sentence had become "what `stack` still wants is `stack`" once
  [ADR-0244](adr/0244-a-child-may-say-where-it-sits.md) took its last blocker.
- **The first child stays in flow and the rest are `position: absolute`.** That
  is the whole widget, and each half answers what the other cannot: something has
  to give the stack a size, because a box whose children are all out of flow is a
  box of nothing — and an overlay must not resize what it sits on. It also means
  a stack of one child is that child in a box, so wrapping an existing widget in
  one is a change that cannot move it.
- **It positions nothing, and that is the point.** §1 asks for children
  "positioned by alignment or absolute insets" and both already worked: an
  absolute child with no inset is placed by the container's `align-items` and
  `justify-content` and by its own `align-self`, and one with an inset goes where
  it says. This is the case `ComputedStyle.INITIAL`'s inset comment has been
  describing since before anything could reach it — *"the difference only shows
  on an absolute node, where zero would stretch it and undefined leaves it where
  the alignment put it"*. `stack` is the widget that finally shows it.
- **Ten tests, six of which fail against a stack that positions nothing**, all
  against Yoga's own output, because every claim a stack makes is a claim about
  where boxes ended up. The markup path goes through the real catalog rather than
  constructing the record: `stack` is in §1's `core` list, so what is under test
  is the registration.
- **The tests wrap the stack in a row that does not stretch it**, and that is
  load-bearing. The root box is always laid out at the frame's size, so a stack
  tested *as* the root is 300 wide whatever its children do and every size
  assertion passes for the wrong reason — found by writing the assertions first
  and watching four of them come back 300.
- **No stylesheet rule ships for it**, which is `row`'s and `column`'s
  arrangement exactly: a stack sets no colour, no padding and no gap, and where
  its overlays land is the application's to declare.

### Two things §2.4 said that nothing could hear

- **A widget may read a token** ([ADR-0251](adr/0251-a-widget-may-read-a-token-and-a-nested-scroller-is-named.md)),
  and the entry asking for it was **half stale when it was written**.
  `Paints.Context.color` has read a resolved custom property since ADR-0195 —
  that is how a chart gets `--gb-chart-1…8` — so what was actually missing was
  the same door for a *number*. `length` is it, deliberately still narrow on
  `color`'s terms: lengths and colours and nothing else, because both are values
  the cascade already parses and a general token accessor would invite a widget
  to reimplement the parser.
- **Reading it was not the hard half.** The wheel arrives at `onPointer`, where
  there is no context to ask — so `ScrollViewport` reads `--gb-scroll-line` in
  `render` and **banks** it into `ScrollState` through the shape `onMeasured`
  already had. A frame late by construction, which is ADR-0117's bargain
  unchanged: a paint always precedes an input, so a real window has spent that
  frame before anybody can turn a wheel. Guarded on the value having changed,
  because the callback sets state and a `setState` every frame is a rebuild every
  frame.
- **The override test paints twice and says why.** The first paint banks the
  token; the rebuild after it is what puts the value on the widget the router
  hands the wheel to. Writing it with one frame is what found that, and the
  comment is there so the next reader does not "fix" it.
- **A nested same-axis scroller says so, once.** §2.4 rules them out and nothing
  enforced it — and chaining means such a pair behaves *reasonably* rather than
  badly, so the ban cost nothing and the author heard nothing, which is the worst
  shape a rule can have. `BuildContext.findAncestorState` is the whole
  implementation: it exists for `scrollIntoView` and answers this with nothing
  added, which is why it is asked in `ScrollState.build` rather than by teaching
  the renderer about scroll views.
- **It is a diagnostic and not a refusal.** The arrangement still works, because
  turning a canon rule into a crash is worse than the rule going unheard — the
  author's problem was that nobody told them. Deduplicated by axis for ADR-0243's
  reason: `build` runs per element per invalidation, and a document that nests in
  four places has one mistake rather than four.
- **`list` is unchanged and its entry stays open**, which is the honest half.
  `ListView.virtualized(h)` takes the height as an API argument, and the number
  decides which rows to build in `children()` — so a value banked from `render`
  would be a frame late in the one place a frame late means building the *wrong
  rows*. The door `scroll` needed is not the one `list` needs.

### The state a window could be put into and never asked about

- **A window can be maximized, restored and asked**
  ([ADR-0252](adr/0252-a-window-is-maximized-when-the-platform-says-so.md)).
  `Application.maximized()` was a creation flag and nothing else: it became
  `SDL_WINDOW_MAXIMIZED` and after that nobody involved knew whether the window
  still was one.
- **`isMaximized()` answers what the platform last *reported*, not what was last
  asked**, and that is the question the entry left open. It follows from what
  ADR-0221 already established — maximized is a *state* rather than a size — and
  from the fact that every platform routes the ask through a window manager that
  may refuse it, delay it or grant it in part. A flag set on the way out would be
  a lie the moment one did.
- **The cost is stated rather than hidden**: between `maximize()` and the event,
  `isMaximized()` is still false. That is a window which has been asked and has
  not yet agreed, and there is no third answer that is true — asserted by a test,
  because it is the kind of thing a later reader would "fix".
- **It is also what makes the feature worth having.** An application can learn
  that the **user** maximized it, which is what a "remember my window size"
  preference needs and which no amount of tracking one's own calls can produce.
  `HeadlessWindow.reportMaximized` is the route that does not start with the
  application, and the test for it is the one that matters most.
- **The export list and the C shim both had to learn the new names**, and that
  refusal earned its keep immediately. `SDL_EVENT_WINDOW_MAXIMIZED` is `0x20A`
  and `RESTORED` is `0x20B` — derived by counting an unnumbered C enum from the
  last explicit value, which is precisely the arithmetic that is silently wrong.
  `LayoutVerificationTest` refuses a constant declared in Java that nothing
  verifies against the compiled library, and it checked both.
- **`GoldberryRuntime`'s switch is exhaustive over a sealed interface**, so
  adding the event failed the compile until it was routed — the design working
  rather than an inconvenience.

### Not started

Client-side decorations, the rest of §4 —
the pickers, `code-input` and autocomplete. §7 is **complete**, mechanism and
all: §3's **sibling reflow** is built, and it was the last thing the group owed.
All of §4's leftovers reuse
`TextEdit` and `EditHistory` for their editing and `field` for their contract,
which are the parts with rules in them, so each is ordinary widget work now.
Everything outstanding is in [TODO.md](TODO.md).

## M4 — GPU

**Not started.** `canvas3d` and the GPU composition path.

## M5 — Hardening

**Not started.** Text editing depth, the AccessKit bridge, IME preedit, docs, and the
0.1 release.

## Content modules

**None started, and none scheduled.** `docs/content-widgets.md` specifies eleven
optional modules — HTML/markdown, PDF, plotting, code, terminal, vector, media,
camera, microphone, emoji and the parked web engine — and the plan they now sit
in is `docs/ARCHITECTURE.md` §11.1 with
[ADR-0190](adr/0190-a-content-module-brings-its-own-natives.md) under it. No
Gradle subproject, no artifact and no line of code exists for any of them.

Two of the eleven are not modules at all in what ships, and both were decided
before the document was written: the **chart** widgets belong to `:widgets`
([ADR-0014](adr/0014-single-widgets-module.md)), and the **emoji** font belongs
to core's text stack — which is where core picks up the one licence obligation an
application cannot discharge with a notice file. Both are in
`ARCHITECTURE.md` §17.1 as disagreements rather than edits.

What is built that they would stand on, stated so the estimate is honest:

- **A borrowed pixel buffer wrapped as a `BLImage` costs nothing to hand over**
  ([ADR-0031](adr/0031-blend2d-and-the-borrowed-buffer.md)), which is exactly the
  handover PDFium, ThorVG and libVLC each want.
- **A leaf render object measured by a callback** is what `html-view`,
  `pdf-view` and `camera-view` all are — the same shape `text` already uses,
  where Yoga asks and the widget answers.
- **A repaint boundary is a subtree's own raster**
  ([ADR-0071](adr/0071-a-layer-is-a-subtrees-raster.md)), so a page, a video
  frame or a camera preview updating off the UI cadence is a layer that
  re-uploads rather than a tree that rebuilds.
- **Golden-image CI is deterministic on three OSes**, so every one of these
  widgets is testable without hardware — which is why camera and microphone
  specify synthetic sources rather than acquiring them later.

And the two facts that make the first one cost more than it reads:

- **The export list has no rounded geometry, and it has gradients now.** 211
  symbols reach Java, and the twenty-five `bl_context_*` among them are the ones
  the toolkit's own painter uses — `bl_context_save` arrived with `canvas`
  ([ADR-0193](adr/0193-a-canvas-is-a-second-clip-depth.md)) and the three
  fill-style entries with a chart's gradient fill
  ([ADR-0207](adr/0207-a-fill-may-be-a-ramp.md)), which is two of the three
  things this line used to name. A native litehtml container still needs more, so
  `goldberry-html` still starts by widening `libgoldberry`'s paint surface — but
  it starts from a list that already has the gradients it would have added first,
  which is what "shared work" was a prediction about.
- **None of the 59 `SDL_*` symbols is audio or camera.** "Zero new natives" is
  true of the binary and not of the surface — which is no longer a prediction:
  `tray-icon` reached that file first and paid eleven symbols for it
  ([ADR-0191](adr/0191-a-tray-is-a-menu-somebody-else-draws.md)), and camera and
  microphone are the same widening again.

## Module layout

| Module | Artifact | Contents |
|---|---|---|
| `:common` | `goldberry-common` | What both halves need and neither owns: `Logs`, which every logger in the toolkit comes from so that SLF4J's own no-provider warning is quiet before the first one is created ([ADR-0023](adr/0023-logging-and-the-example-as-a-subproject.md)), and `Startup`, the timeline of what happened before the first pixel ([ADR-0028](adr/0028-the-start-up-timeline.md)). **The lowest module**: it requires nothing of Goldberry's, which is what lets `:natives` and `:core` both use it. It exists because they cannot both reach into the other — `:core` requires `:natives`, so shared code used to have to live inside the native layer and be exported from it ([ADR-0174](adr/0174-what-both-halves-need-is-its-own-module.md)) |
| `:natives` | `goldberry-natives-{platform}-{arch}` | Hand-written FFM bindings, owning wrappers, and the CMake superbuild that produces `libgoldberry` |
| `:core` | `goldberry-core` | The engines and the contracts — the widget/element/render trees, style, layout, text, icons, paint, the backend SPI, and the two backends `headless` and `sdl3` ([ADR-0041](adr/0041-three-platforms-four-artifacts-two-backends.md)). **No widgets**: `text`, `row`, `column`, `panel` and `spacer` lived here until they had a catalog to belong to ([ADR-0092](adr/0092-a-primitive-is-a-widget-like-any-other.md)) |
| `:widgets` | `goldberry-widgets` | The widget catalog — controls, containers, menus, charts — plus the showcase screens that serve as the visual regression corpus. **One module, a package per control** — `docs/core-widgets.md`'s groups (`…widgets.controls` and `…widgets.overlay`, with `form`/`panel`/`nav`/`collection` as they are built) and one package inside each for every widget and its parts. Half a reversal of ADR-0014, and the second level is what makes ADR-0065's rule a boundary the compiler enforces rather than a convention: a `slider-thumb` is now invisible outside `…controls.slider`, where before "package-private" meant "visible to the whole catalog" ([ADR-0091](adr/0091-one-module-a-package-per-control.md)) |
| `:weaver` | *not published* | The weaver, in two halves. **Catalog**: collects a module's `@Markup` widgets into a `WidgetCatalog` and declares it — every build runs this, because nothing finds annotated classes at run time ([ADR-0131](adr/0131-a-widget-package-announces-itself.md)). **Models**: rewires a `@Model`'s `@Bind` fields into bindings and writes its `@Action` call sites, with the JDK's class-file API — only a **GraalVM native image** runs this, since an ordinary jar binds the same annotations reflectively ([ADR-0155](adr/0155-a-jar-binds-at-run-time-an-image-is-woven.md)). Build-time only, like `:assets`: it runs between `compileJava` and `jar`, never reaches a runtime classpath and has no `module-info` ([ADR-0125](adr/0125-a-raw-field-is-woven-into-a-binding.md), [ADR-0126](adr/0126-actions-are-bound-by-lambdametafactory.md)) |
| `:gpu` | `goldberry-gpu` | `canvas3d` and the GPU composition path |

`:assets` is a fifth subproject and is not published: it is the build-time
tool that fetches the pinned fonts and icon set and compiles Lucide's 1544 SVGs
into a path table, which `:core` packages
([ADR-0033](adr/0033-assets-are-fetched-and-compiled-not-committed.md)).

`:example` is the sixth and is not published either: it is the showcase, and it
runs on the module path so that what the module graph exposes to an application is
exercised rather than assumed ([ADR-0023](adr/0023-logging-and-the-example-as-a-subproject.md)).

Every module logs through SLF4J and binds no implementation. An application that
adds one gets the toolkit's diagnostics; one that adds none gets silence, SLF4J's
own no-provider warning included. At `TRACE` the toolkit reports a start-up
timeline, the modules it resolved, and per-frame timings
([ADR-0028](adr/0028-the-start-up-timeline.md)).

Every module ships a `module-info.java`. That is not decoration: the module graph
is what enforces the rule that raw `MemorySegment` never escapes `:natives`, and
it is what makes `--enable-native-access` targetable under JEP 472. See
[ADR-0007](adr/0007-jpms-modules-enforce-the-native-boundary.md).

It is also what decides where shared code goes. `:core` requires `:natives`, so
anything both of them need has to sit below both — which is why logging and the
start-up timeline are a module rather than a package, and why `:core` names
`:common` directly instead of taking it through the native layer
([ADR-0174](adr/0174-what-both-halves-need-is-its-own-module.md)):

```
:common ← :natives ← :core ← :widgets
   ↖________________________/
```

## The call layer

**Done.** Every C function the toolkit binds is a **holder**: a small final class
holding that function's address, with its unbound `MethodHandle` as a
`private static final FD_<symbol>` and a `call` that takes ordinary Java types.
The holders of one library are grouped in a `…Calls` record, which is what a
binding class keeps instead of forty `MemorySegment` fields
([ADR-0173](adr/0173-a-bound-function-is-a-holder-and-its-handle-is-a-constant.md)).

```java
// before -- three things that are one thing
private final MemorySegment contextEnd;
this.contextEnd = Downcalls.symbol(lookup, "bl_context_end");
check("bl_context_end", (int) Downcalls.INT__PTR.invokeExact(contextEnd, context));

// after
check("bl_context_end", calls.contextEnd().call(context));
```

- **The binding classes lost a quarter to a half of their lines** — `Yoga` 658 →
  409, `Blend2D` 821 → 561, `SdlVideo` 837 → 653, `HarfBuzz` 393 → 249, `Sdl`
  296 → 198 — and all of it was plumbing. Thirty-six per-shape invocation
  helpers are gone with it.
- **A record is one subject, not one library.** `Blend2DCalls` was forty-six
  functions; it is now `ImageCalls`, `ContextCalls`, `PathCalls`, `FontCalls` and
  `RuntimeCalls`, and the 821-line `Blend2D` binding split the same way into
  `Blend2dImage`, `Blend2dContext`, `Blend2dPath`, `Blend2dFont` and
  `Blend2dRuntime` — 78 to 248 lines each, one per wrapper. Yoga, HarfBuzz and
  SDL's records are split the same way; their binding classes hold several.
- **Every `call` names its parameters and says what they are.** `call(a1, a2)`
  is now `call(context, rect, argb)`, with a summary, the C prototype, and a
  `@param` for each — 134 functions' worth, recovered from the call sites that
  already named them and then written out.
- **A failure names the function it was.** `Blend2D`'s four shared `invoke`
  helpers reported `"a Blend2D call"` for any of the eighteen symbols that went
  through them, because a shared helper had no way to know which.
- **[ADR-0161](adr/0161-a-downcall-handle-is-a-constant-or-it-is-not-a-call.md)'s
  rule is kept more strictly, not relaxed.** A holder's handle is `static final`
  and is read *inside* the method that invokes it; and because there is now one
  handle per function rather than one per shape, no call site reaches a constant
  through a parameter at all. That was the compromise the per-shape helpers
  forced, and it is gone.
- **The holders live in packages that contain nothing else**, and those packages
  are what `--initialize-at-build-time` names. Measured, because the alternative
  fails silently: a handle `static final` on a nested class whose *enclosing*
  class is named in the flag runs at **4538 ns/call** against **8**. The image
  builds, runs and paints correctly at a fortieth of the speed.
- **Verified end to end.** A native image built from the packaged
  `goldberry-natives` jar — the shipped `native-image.properties`, nothing added —
  calls through a holder at **9.84 ns/call**, against 10 ns on the JVM.
- **`HolderShapeTest`** replaces `DowncallsTest`. The old one could only check
  that a *name* matched its layouts, because nothing tied either to a call site.
  A holder states its signature twice — once in layouts, once in Java types, in
  one class — so the check is now that the two agree. It walks the compiled
  classes rather than listing them.

The cost is about 3200 lines of holder code, uniform and uninteresting, and a new
symbol now needs a holder class rather than a field and a lookup.

## Package layout

**Done.** `:widgets` had been split by group and then by control
([ADR-0091](adr/0091-one-module-a-package-per-control.md),
[ADR-0065](adr/0065-a-part-is-styleable-and-not-constructible.md)); `:core` and
`:natives` had not, and four packages carried a third of the toolkit —
`…goldberry.css` at 23 types, `…goldberry.backend` at 21, `…natives.yoga` at 22,
`…natives.blend2d` at 20. A package that size is a folder, not a boundary.

Every package is now named for **the part its contents play**
([ADR-0172](adr/0172-a-package-is-a-role-and-the-module-is-the-fence.md)), and
`docs/ARCHITECTURE.md` §2.1 is the map.

| Module | Packages before | After | Largest package |
|---|---|---|---|
| `:core` | 15 | 35 | 10 |
| `:natives` | 7 | 15 | 12 |
| `:widgets` | 38 | 39 | 11 |

- **The CSS engine is a compiler, so it reads like one** — `css.parse`,
  `css.select`, `css.cascade`, `css.value`, with `css` itself holding the sheet an
  application loads and the `ComputedStyle` it produces.
- **Input is split by the part it plays** — what arrives (`input.event`), the
  vocabulary an accelerator is written in (`input.key`), the snapshot it is routed
  against (`input.hit`), and the interfaces a widget implements to hear any of it
  (`input.handler`).
- **A native library is split where the foreign memory stops.** The wrappers that
  hold a handle stay beside the binding class they are the only callers of; the
  enums, which map a C constant to a Java name and touch nothing, get packages of
  their own. `MeasureCallback`, `MeasureProbe`, `SdlWindowHandle`,
  `SdlEventBuffer` and `SdlEventWatch` were each moved out and moved back the
  moment they turned out to traffic in `MemorySegment`.
- **Eleven members became public**, each with a doc comment saying why. The one
  worth watching is `Frame.end()`: it used to be unreachable from outside its
  package and is now merely wrong to call, so the frame enforces its own lifetime
  instead — ending twice is a no-op, painting afterwards throws.
- **Two splits were tried and reverted.** `WidgetRenderer` reads and writes
  `Element`'s package-private style cache, so it is the element tree's own paint
  pass rather than a neighbouring role. And the root `…goldberry` package keeps
  its ten types because `Launcher` and `GoldberryRuntime` make twenty-one calls
  into `Window`'s package-private event intake — a toolkit whose `Window` offers
  an application a `handlePointerMoved` has published its event loop by accident.

Two things now hold this in place that are tests rather than prose, and both were
checked against a deliberate break:

- **`ExportedSurfaceTest`** (`:natives`) reads the module's own descriptor and its
  own class files and fails if any member reachable from outside mentions a
  `MemorySegment`. It discovers its subject rather than listing it, so a package
  added next month is checked next month. This is `docs/ARCHITECTURE.md` §3.1
  becoming a check instead of a claim.
- **`WrittenNamesTest`** (`:weaver`) resolves every class name the weavers write
  into bytecode as text. Splitting `bind` turned `ModelWeaver`'s one package
  prefix into three, and nothing in the compiler would have caught getting that
  wrong: the weave would succeed and a woven native image would fail to start much
  later with a `NoClassDefFoundError` naming a package that no longer exists.

The moves were made by `tools/refactor/move_package.py`, which is kept in the
tree. A package move is four edits, and the fourth is the one nobody does by
hand: the file *left behind* that used a type without an import, because it used
to share a package with it.

## Native artifacts

Every artifact is built on a native runner ([ADR-0012](adr/0012-native-ci-runners-with-a-pinned-glibc.md));
there is no cross-compilation toolchain. Four runners produce four artifacts, one
each, with no cross-targeting anywhere
([ADR-0041](adr/0041-three-platforms-four-artifacts-two-backends.md)).

| Target | Built on | Output |
|---|---|---|
| `linux-x64` | `ubuntu-24.04` + `manylinux_2_28_x86_64` | `libgoldberry.so` |
| `linux-aarch64` | `ubuntu-24.04-arm` + `manylinux_2_28_aarch64` | `libgoldberry.so` |
| `windows-x64` | `windows-2022`, MSVC `-A x64` | `goldberry.dll` |
| `macos-aarch64` | `macos-14` | `libgoldberry.dylib` |

Windows on ARM and macOS on Intel are **not built**. `NativePlatform` refuses
those two pairs at construction, so the failure names the decision rather than a
missing resource.

The manylinux container is not incidental: it pins the glibc floor at **2.28**,
so the Linux artifacts run on anything from RHEL 8 onward. Building on a stock
`ubuntu-24.04` would link against glibc 2.39 and refuse to load on RHEL 8/9,
Debian 12, or Ubuntu 22.04. **A locally built library is therefore not the
published artifact** — it links against the developer's own glibc.

Building on native runners produces artifacts, not test coverage, so CI also runs
the Java tests against the real library on each platform.

