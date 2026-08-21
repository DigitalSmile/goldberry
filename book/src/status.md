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
| [M3 — Shell](#m3--shell) | **started** | Both places an overlay can go, `menubar`, §5's containers, the whole `scroll` family, and §4's first field — with the clipboard and text input the platform had never been asked for |
| [M4 — GPU](#m4--gpu) | not started | `canvas3d`, GPU composition |
| [M5 — Hardening](#m5--hardening) | not started | Text editing depth, AccessKit bridge, IME preedit, docs, 0.1 release |

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
- **`F10`, not `Alt`, and the reason is in the type.** §8 asks for "`Alt`-style
  keyboard activation"; a bare `Alt` is a *modifier released with nothing in
  between*, and a `Shortcut` here is a key plus modifiers — `Key` has no `ALT` to
  name, because `Shortcut`'s own constructor refuses one that can never fire.
  `F10` is the companion binding on every platform that has the `Alt` one, and it
  **opens** the first heading rather than focusing it, because there is no
  `Host.focus` and a binding that did nothing visible would read as broken rather
  than as missing.
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

### Not started

Tray, dialogs, client-side decorations and charts, and the rest of §4 —
`text-area`, the pickers, `code-input` and autocomplete. All of those reuse
`TextEdit` and `EditHistory` for their editing and `field` for their contract,
which are the parts with rules in them, so each is ordinary widget work now. Everything outstanding is in
[TODO.md](TODO.md).

## M4 — GPU

**Not started.** `canvas3d` and the GPU composition path.

## M5 — Hardening

**Not started.** Text editing depth, the AccessKit bridge, IME preedit, docs, and the
0.1 release.

## Module layout

| Module | Artifact | Contents |
|---|---|---|
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

