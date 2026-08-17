# Status

Tracked against the milestone ladder in `docs/ARCHITECTURE.md` §16.

| Milestone | State | |
|---|---|---|
| Foundation | **done** | Multi-module Gradle (Groovy DSL), version catalog, convention plugins, JPMS module graph, JDK 25 toolchain, JUnit 6, licence disclosure, decision log |
| M0 — Skeleton | **done** | **The superbuild links on all four targets.** Blend2D, AsmJit, SDL3, Yoga and HarfBuzz statically combine into one `libgoldberry` exporting exactly the symbols on the export list and nothing else — both Linux targets in CI's manylinux containers, `macos-aarch64` on an Apple Silicon runner, and **`windows-x64` under MSVC**. The layout probe passes against the real library, and Yoga's measure callback crosses in both directions including the `YGSize` struct-by-value return ([ADR-0017](adr/0017-proving-the-struct-by-value-upcall.md)), so the hand-written binding mechanism is proven end to end. **Yoga's node API is bound**, and the callback is now driven by real layout passes rather than by a C probe written for the purpose ([ADR-0029](adr/0029-yogas-node-api-and-who-owns-a-node.md)). SDL3's lifecycle, error and version calls are bound and tested against the real library ([ADR-0018](adr/0018-sdl-conventions-stop-at-the-boundary.md)). The backend SPI, the `headless` backend and the `sdl3` backend are in `:core`, with fractional DPI correct by construction ([ADR-0019](adr/0019-the-backend-spis-first-cut.md)) and background work on virtual threads that completes on the UI thread ([ADR-0020](adr/0020-one-ui-thread-and-virtual-threads-behind-it.md)). **The showcase opens a window and presents frames** ([ADR-0021](adr/0021-the-example-is-a-separate-build.md)), through a `Window` front door that names no backend and builds no event loop ([ADR-0022](adr/0022-window-is-the-front-door.md)). **Windows closed the milestone**: `goldberry.dll` builds, `:natives:test` passes against it with `goldberry.native.required=true` so nothing skips, and the golden images match — which answers the MSVC `/INCLUDE:` and `.def` branch of the export machinery and Win64's 4-byte `long` at the same time ([ADR-0041](adr/0041-three-platforms-four-artifacts-two-backends.md)) |
| M1 — Vertical slice | **started** | **Blend2D rasterizes the frame, HarfBuzz shapes the text.** `Frame` no longer writes pixels by hand: it wraps the platform's own buffer in a `BLImage` without copying it, scales the context by the display factor so coordinates stay logical and fractional edges antialias rather than snap, and blends with alpha that now means something ([ADR-0031](adr/0031-blend2d-and-the-borrowed-buffer.md)). The showcase paints through it. Shaping takes UTF-16 straight from a Java `String`, so the cluster indices point back into the caller's own text ([ADR-0032](adr/0032-shaping-is-utf16-in-glyphs-out.md)). **Text draws.** Blend2D's font chain is bound and a `GlyphRun` reaches the rasterizer: `Font` in `:core` owns a HarfBuzz font and a Blend2D one over the same bytes, shapes in design units and puts the size on the Blend2D font alone, so the font matrix is the only thing that converts ([ADR-0034](adr/0034-one-size-and-the-design-unit-crossing.md)). The showcase draws two lines of Inter, and the tests assert *where* the ink landed — the inked span matches the measured width, which fails by a factor of 128 if either side of that crossing is wrong. **And text takes part in layout.** A `Paragraph` shapes once and wraps with arithmetic over that one `GlyphRun`, so its measure function answers Yoga from inside a layout pass without shaping again ([ADR-0036](adr/0036-the-paragraph-is-shaped-once-and-wrapped-many-times.md)). A `Box` with text is a measured leaf: the showcase's body wraps to whatever width the sidebar leaves it, and its siblings are positioned against the height that comes back. Two numbers are written down in that layout — the bar's height and the padding — and everything else comes from content. **The cache and the benchmarks are done** ([ADR-0037](adr/0037-what-the-text-path-costs.md)): `./gradlew benchmark` measures the text path, and the numbers say the upcall crossing is ~0.3 µs, a memoised wrap 0.02 µs, and shaping 56 µs — so `ParagraphCache` caches shaping and nothing else. **Painting is now multithreaded, and icons draw.** Blend2D rasterizes a frame across up to four workers on any surface over 400×300, which takes a 960×640 paint from 0.47 ms to 0.34 ms and a 4K one from 6.0 ms to 2.3 ms; a threaded frame is asserted pixel-identical to a synchronous one at every worker count ([ADR-0042](adr/0042-blend2ds-workers-and-how-many.md)). Blend2D's path API is bound and Lucide's 1544 icons reach the screen as stroked paths, all of them asserted to parse ([ADR-0043](adr/0043-icons-are-stroked-paths.md)). And a typeface is loaded once rather than once per size: `FontFace` holds the shaper and Blend2D's face, so a second size costs 4.4 µs instead of 681 and no second copy of the file ([ADR-0044](adr/0044-one-face-many-sizes.md)). **The 60 fps claim now holds at the tail, not just the median.** A 960×640 frame with a wrapped paragraph used to run at a 7.86 ms median and a 14.18 ms p95 — a factor of two in hand on the median and none at the tail. Pacing the loop to the display ([ADR-0047](adr/0047-a-frame-nobody-sees-costs-full-price.md)) took that to a **3.13 ms median and a 4.28 ms p95**, which is 3.9× of headroom where there was effectively none; the old numbers reproduce exactly when the pacer is turned off with `-Dgoldberry.frame.rate=0`, which is what they were measuring. Two thirds of that frame was work thrown away on frames the display never scanned out. **What remains of the claim is breadth, not budget**: it is still one machine, and that machine is a VirtualBox VM. The milestone asks for Linux, macOS and Windows. **Yoga and Blend2D now meet**: `BoxPainter` lays a flexbox tree out and fills the result, setting Yoga's point scale factor from the display scale so computed edges land on physical pixels — the first code for which the fractional-DPI claim is a mechanism rather than an intention. Inter, JetBrains Mono, OpenMoji and Lucide's 1544 icons are fetched at build time, pinned by checksum, and packaged into `goldberry-core` ([ADR-0033](adr/0033-assets-are-fetched-and-compiled-not-committed.md)) |
| M2 — Widgets & style | **started, engines done** | **The CSS engine is done, end to end.** A hand-written tokenizer and parser for the §8 subset, matching right-to-left with backtracking, the four fixed cascade layers, custom properties and `var()` — ending at a `ComputedStyle` that carries typed values and nothing else ([ADR-0049](adr/0049-the-css-engine-stops-at-computedstyle.md)). `Box.style(ComputedStyle)` is the join the property split was stated for: layout properties land on the fields Yoga reads, paint properties on the ones Blend2D reads. **Nord light and dark ship** as custom-property layers — two files whose only selector is `:root`, so switching a theme repaints widget rules that never mention a colour (§10). **Golden-image CI runs on all three platforms**: six scenes driven through the whole pipeline, compared with a per-channel *and* an area tolerance, because Blend2D JITs its pipelines per CPU and bit-equality across AVX2 and NEON is not a promise anyone made ([ADR-0050](adr/0050-golden-images-have-a-tolerance.md)). **KDL 2.0 parses and inflates**, including the §9 example document as a test, with a registry that refuses unknown nodes by position; and **hot reload works for stylesheets and markup alike** — strict on first load, forgiving on every reload, because a file being edited is broken more often than it is whole ([ADR-0051](adr/0051-kdl-is-parsed-here-and-reloading-is-forgiving.md)). **All three trees now exist.** Widgets are immutable records; the element tree persists across rebuilds and is what the cascade talks to, so `:hover` survives a parent re-describing its child; state lives on the element, `setState` mutates immediately and defers the rebuild, and ten calls in one handler cost one build ([ADR-0052](adr/0052-state-lives-on-the-element-and-rebuilds-are-deferred.md), which closes the gap ADR-0004 left open). The render tree is materialized as a `Box` tree per frame rather than retained ([ADR-0053](adr/0053-the-render-tree-is-a-box-tree-for-now.md)). **Five primitives ship** — `text`, `row`, `column`, `panel`, `spacer` — and **the parity invariant of §11 is enforced**: each is a Java record, a KDL node and CSS-selectable by type, id and class, with a test asserting the Java-built and KDL-built values are equal. A golden image runs the whole stack, KDL to pixels. **Pointer input routes.** A box carries an opaque owner tag, so a rectangle on screen leads back to its element; hit testing runs against the snapshot taken while painting rather than a fresh layout, because a pointer event is about what the user can see. Dispatch is capture → target → bubble with `consume()`, `:hover` moves along the whole ancestor chain and only where it differs, `:active` follows the press, and focus walks up to the nearest focusable ancestor with `:focus` and `:focus-visible` kept distinct ([ADR-0054](adr/0054-hit-testing-runs-against-the-painted-frame.md)). The sdl3 backend translates all of it — motion, buttons, wheel, keys and committed text — and `GoldberryRuntime` drives the router from a real window. **§7's remaining gaps are closed.** The wheel arrives in lines, fractional and positive down, with SDL's away-from-the-user sign and the "natural scrolling" inversion both undone at the boundary, so a widget never sees either ([ADR-0056](adr/0056-the-wheel-is-lines-and-the-sign-is-ours.md)). **A press captures the pointer** until the release, so a drag that leaves a widget still reaches it and `:active` cannot get stuck; an explicit capture outlives the release, for a gesture that does ([ADR-0058](adr/0058-a-press-captures-the-pointer.md)). **The cursor rides on the painted box**: `cursor: pointer` resolves through the cascade onto the rectangle, and hit testing reads it back off whatever the pointer is over — so inheritance is the stack of rectangles rather than the element tree, and it freezes during a drag ([ADR-0057](adr/0057-the-cursor-rides-on-the-painted-box.md)). And **accelerators are bound per window**, `router.shortcut("Ctrl+S", ...)`, fired after the focused chain declines the key so a text field keeps its own `Ctrl+A`; letters and digits joined `Key` for exactly this, since a modified letter produces no text event anywhere. Tab and Shift+Tab traverse in document order. **And the catalog has started.** `button` ships in `:widgets` — a Java record, a KDL node and a CSS type, with a test asserting the first two produce equal values; variants are classes because that is the one spelling Java, KDL and CSS can all use; the metrics are the design system's in the toolkit-base layer and the colours are component tokens in each theme, because a hover lightens on Nord dark and darkens on Nord light ([ADR-0059](adr/0059-a-control-is-a-record-a-node-and-a-rule.md)). It activates on a **click** — a synthetic event the router raises only when a press and its release land on the same node, so dragging off to cancel works — and on `Space`/`Enter`, ignoring repeats. The `action` half of §9 is wired: markup names an action and an `Actions` registry resolves it, strict by default so a typo fails at inflation rather than producing a button that silently does nothing. `padding` grew CSS's 1–4 value shorthand and its four longhands on the way, because `padding: 0 12px` is the button's own metric. **`button` is finished, not started**: label, icon, or both — an icon is a `Box` now, which closes the question ADR-0043 left open, and it turned out to need no measure function because an icon is built at a size and that size *is* its intrinsic one. `disabled` refuses every route to the action, drops the button out of the Tab order and matches `:disabled`, which is the one pseudo-class a widget owns rather than the router. Markup names an icon against a registry for the same reason it names an action: an `Icon` owns native memory, and a document reloaded on every keystroke would leak one per reload. **Four golden images** cover the variants on both themes, the five states side by side, and the icon layout — the check that catches a padding on the wrong edge, which no value assertion can. **And the showcase is a widget tree**: bar, sidebar, wrapped prose and a row of buttons, with `setState`, theme switching, `Ctrl+T`, focus that survives a rebuild, and `:hover` that repaints itself. **And `bind` is done, which closes the second half of §9's wiring.** A `Property<T>` is a cell with listeners and nothing else — `get`, `set`, `subscribe` — and `set` does nothing when the value is unchanged, which is what makes two properties mirroring each other settle instead of recursing. `Bindings` is the third registry beside `Actions` and `Icons` and is deliberately the same shape: markup names a path, the registry resolves it, strict by default. **A path is `prefs.frost` and nothing else** — the §17 fork is settled at dotted paths, enforced by the registry, so `bind="!prefs.frost"` fails at inflation with the text quoted rather than producing a control that silently never updates ([ADR-0062](adr/0062-bind-is-a-path-and-nothing-else.md)). The binding lives on the widget and the subscription on its element, so a bound node has no wrapper element and `panel > text` styles it exactly like an unbound one; a change marks the element dirty by the same route `setState` does, so three changes in one frame cost one build. `text bind="user.name"` works from KDL and from Java, with the parity test extended to cover it, and the showcase's sidebar carries a line that follows a property nothing in the tree owns — set from a virtual thread, redrawn without anything reaching into the widgets. **And binding is one-way, which is a change to §9**: a widget is handed the read-only `Observable` half of a property, so markup can read a value and not write it, and what the user did travels back up as an action — `checkbox bind="prefs.frost" change="toggleFrost"`. A control is therefore controlled in the React sense: the tick moves when the application sets the property, not when the pointer lands. §9's "one/two-way" is amended to say one-way, deliberately and on the record ([ADR-0063](adr/0063-data-flows-down-events-flow-up.md)). **And the paint layer can now draw what the design system asks for.** `border-radius`, `border`, `outline` and `opacity` reach `Box`, and a rounded rectangle is built from four cubics through the already-exported `bl_path_cubic_to` rather than from a new Blend2D symbol — so the corner works on every target on the first CI run instead of the one after the export list found out ([ADR-0064](adr/0064-a-rounded-rectangle-is-four-cubics.md)). **`button` complies with its own metrics row** (§3): radius 8, the design system's focus ring — 2px `--gb-focus` at a 2px offset, following the radius, written once for every control rather than per control — and `:disabled` as **45% opacity rather than a colour remap** (§2.1), so a disabled `danger` button still reads as dangerous where eight muted tokens had made every disabled button look alike. Removing the remap exposed that a disabled control still lightened under the pointer; CSS would spell the fix `:not(:disabled):hover` and `:not()` is not in §8's subset, so `PointerRouter` refuses to *set* `:hover` or `:active` on a disabled widget — one choke point, every control, forever. **And `checkbox` ships**: three states with `:indeterminate` as its own pseudo-class, because two cannot describe three and folding mixed into `:checked` makes every rule that meant "the tick is showing" silently wrong; a tick and a dash drawn by the painter rather than by an `Icon`, since a widget is a value and an `Icon` owns native memory; a click target that includes the label; `Space` and deliberately not `Enter`, which belongs to a dialog's default action. Its glyph is the first **part** — `check-indicator` is CSS-selectable and **not** KDL-constructible, a stated exception to the parity invariant rather than an oversight in it, because a part has no existence outside its parent and one `ComputedStyle` cannot carry two backgrounds ([ADR-0065](adr/0065-a-part-is-styleable-and-not-constructible.md)). The value is **controlled** in the sense ADR-0063 settled: a click on a bound checkbox whose handler does nothing moves neither the property nor the tick, and a test asserts exactly that. **And the cascade inherits, which closed a bug and a gap at once.** A checkbox's label rendered black on the dark theme, because `StyleResolver` inherited custom properties and nothing else: the label is a `text` child element no rule names, so it resolved to `ComputedStyle.INITIAL`'s black. `button` had never shown it, because it copies `style.color()` onto its child boxes by hand and bypasses the cascade. `color` and the typography now inherit down the element tree — and `cursor` deliberately does not, because it already inherits through the stack of painted rectangles (ADR-0057), and two mechanisms for one property disagree the first time a box has no element behind it. `WidgetRenderer` resolves styles on the way down and builds boxes on the way up, which is the shape inheritance forces. **§1.4's type scale ships**, and it was the blocker's other half: every typography token is a size, a line height and a weight, and all three inherit. `font-family`, `font-size`, `font-weight` and `line-height` reach `ComputedStyle`; a `Fonts` book caches faces by family+weight and fonts by (face, size), because a widget tree is re-rendered every frame and a heading at 20px would otherwise re-parse Inter sixty times a second. **A weight is a face, not an axis**: Inter ships as a variable file *and* as its SemiBold static instance, because instancing `wght` needs symbols in both HarfBuzz and Blend2D and therefore three new export branches — the machinery that has caught the same local-symbol bug three times — while §1.4 specifies exactly two weights and Principle 3 forbids improvising a third ([ADR-0066](adr/0066-a-weight-is-a-face-and-color-inherits.md)). **`button` is now fully compliant with its §3 row**: `body-strong` was the last of the four things `controls.css` said it could not express. The theme tokens were also wrong and are now §1.4's exactly — `heading` was 16 where the table says 15, `body` was 14 where it says 13, there were no line-height tokens at all, and `docs/ARCHITECTURE.md` §10.1 carried a *different* table with a `label` token at weight 500 that no shipped face can draw; §1.4 won and §10.1 records that it did. **And the controls move.** §1.7's motion language ships: a frame `Clock`, the three duration tokens, the two easing keywords with a bezier solver that cannot overshoot, and CSS `transition` resolved by the cascade like any other property. Animated values live in a **per-node overlay applied at paint and never written back into computed style** — the sentence the whole design hangs off, because a cascade that saw the halfway colour as the node's real one would diff *that* against the target and start again from it, giving a control that approaches its hover colour and never arrives. Retargeting starts from the current animated value, so a pointer leaving a button halfway through a fade returns from where the colour is rather than jumping. The whitelist is a **closed enum** — `opacity`, `background-color`, `border-color`, `color` — and `transition: width 200ms` is a *dropped declaration with a warning naming it* rather than a rule that silently never fires, because animating a width would run Yoga every frame of every transition. Colours interpolate in **OKLCH**, which is measurable rather than decorative: Nord's danger red and success green have a channel spread of 54 at their sRGB midpoint and 109 at their OKLCH one. §1.7's "press applies in 0ms, release fades out" needed no new mechanism — the timing that applies is the one on the style being moved *to*, so a zero duration on `:active` and a fade on the resting rule is the whole of it. **The frame loop stays idle**: `renderer.isAnimating()` is what an application asks another frame on, so a window at rest costs nothing and nothing polls. And the virtual clock is what makes any of it testable — `button-hover-midway.png` is three buttons showing the start, the middle and the end of one transition in a single frame, which is a picture no wall clock can take ([ADR-0067](adr/0067-motion-is-an-overlay-on-a-frame-clock.md)). **And the first composite ships, which closes §7.2.** `radio` and `radio-group` are the third and fourth controls, and the first widget that is a *set* rather than a control — so three things that were trivially true for `button` and `checkbox` stop being true. **Traversal:** a group of six options is **one Tab stop** with the arrow keys roving inside it, which is what `docs/design-system.md` §7.2 asks for and what nothing could express, since `moveFocus` collected every focusable node in document order and a radio is one. `Handles.focusScope()` is the whole opt-in, and both halves are the **router's** by the argument already written on Tab: which node an arrow reaches is a property of the group's shape, and the radio the focus is on cannot see its siblings. Arrows are handled after the focused chain declines the key, so a slider stepping its value keeps its own. Both axes rove, because the group's direction is the stylesheet's and input cannot know which pair the user is looking at. **Where Tab re-enters is derived from `:checked`, not remembered** — the decision the record is worth writing for. The obvious implementation is a stored roving position, and it is wrong in a way that only shows later: it is a second piece of state beside the selection, and the two disagree the first time an application sets the value itself, returning the user to the option they last *looked at* rather than the one that is *on*. No event would fix it, because a property being set does not know a router exists. Derived, **the selection is the roving position**; there is nothing to invalidate, nothing to leak when an element unmounts, and one test — focus leaves, the model changes underneath, Tab comes back to the new selection — that the stored version fails. **The invariant:** "exactly one is on" is a fact about the set, so the group applies it on every build and `selected` is deliberately not a KDL attribute, since a document that could mark one option could mark two. A value no option carries selects nothing rather than guessing the first. **And selection follows focus through the application**, not inside the widget: an arrow raises the change and does not move the tick, so a group whose handler does nothing moves the ring and stays put — ADR-0063 applied to a composite. The `fromKeyboard` half of the new `onFocusChanged` is load-bearing rather than decoration: a mouse focus deliberately does not select, or a press moving focus and the click that follows would each fire the change. `Actions` gains a **valued** binding, the first action told which one — `Consumer<String>` over the `value` the document already wrote, with a plain `Runnable` still resolving against it and a valued action *refused* for a `press=` rather than called with an invented argument. `radio-indicator` is the **second part**, which is where ADR-0065 asked that its argument be made again rather than assumed; it holds, and the circle needed no new drawing code — `border-radius: 8px` on a 16px box is one, through the four cubics ADR-0064 already ships, so no native symbol was added and `Box.Mark.DOT` finally has a caller. Five golden images across both themes, and one of them is what caught that options were stretching to the group's full width: a column's flex children stretch on the cross axis, so the focus ring and the click target ran out across empty space while `.inline` kept hugging its label — the same widget with two hit targets depending on a class, which no value assertion would have shown ([ADR-0073](adr/0073-a-composite-is-one-tab-stop.md)). **And `radio` is finished against both specification documents, not just built.** Reading §1.3, §1.5, §2.1, §2.2 and §3.1 against what had shipped turned up five divergences, four of which `checkbox` shared — §3 gives the two controls **one metrics row**, so a rule true of one and not the other is a spec that has stopped being true. Both now carry §1.5's small-control `border-radius: 4px`, which §2.2's ring follows rather than drawing a square one beside `button`'s 8px; both change a **surface** on hover rather than only a border (§2.1's "one surface step"); the group's gap is §1.3's 8 for related controls rather than the 4 it shipped with, and `.inline` takes 16 because side by side each glyph-plus-label is a unit and at 8 a label sits as close to the next option's glyph as to its own. The fourth was a **bug**: `:active` was set on the single deepest element a press landed on, so no control had a working pressed state at all — see below. The fifth was §3.1's check/dot **scale**, the last unimplemented row in that table, which needed the mark to stop being a mark. Two more golden images, one of them a frame 80 ms into a moving selection. **And `--gb-density` ships, at four controls rather than at thirteen** — the cost is per control, so it is three edits now and ten later. Every control sizes itself from `--gb-control-height`; `density-compact.css` is a three-token `:root` block in the theme layer, because that layer is defined by what it holds rather than by what it is called and a fifth would differ from the fourth in name alone. `Density.REGULAR` ships **no stylesheet**: a default is the absence of an override, and a `density-regular.css` restating 32 would be one number in two files. Padding, gap and radius stay literal and are asserted to, because §1.3's density row names heights and nothing else. Compact is **below §1.3's own 32×32 hit-target floor** and that is the trade rather than an oversight — bounded by the glyph staying 16px, so it costs margin around the target and not a smaller target ([ADR-0074](adr/0074-density-is-a-token-swap-and-regular-is-no-stylesheet.md)). **And `toggle` ships, which is the first widget with a *gesture*.** Everything before it responded to a click, a key or a focus change — all single events. A drag is a sequence, and a widget is a value rebuilt every frame with nowhere to keep one, so the `Toggle` that sees the release is a different object from the one that saw the press. **The router reports the origin**, as `PointerEvent.dragX()`, by the argument already written on Tab and on arrow keys — the router owns what the widget cannot see — and because the interval a drag offset is defined over is exactly the implicit capture ADR-0058 already spans. It is **`NaN` and not zero** with no button held, because zero is a real answer (a press that did not move) and `Math.abs(NaN) >= 8` is `false`, so an event with no gesture reads as "not a drag" through the arithmetic rather than through a guard. The rule is one comparison against half of §3's travel: past 8px the value is the **direction dragged**, under it the value flips — so dragging right on a switch already on asks for **on**, which is what a naive "toggle on release" gets wrong. It is also the only control that acts on a release rather than a click, because a switch has no cancel gesture: dragging off it *is* the interaction. `toggle-track` and `toggle-thumb` are the fifth and sixth parts, the thumb by ADR-0073's argument that the unit of independent movement is a node — a `transform` applies down its subtree, so a thumb drawn onto the track would slide the track with it. Where it travels to is the stylesheet's, not Java's. **And the colour question took two wrong answers before the right one, both of which looked like a geometry bug**: the thumb appeared to be breaking out of the pill, and measured off the image it never was — it is exactly concentric and 2px inside all the way round. What the eye read was the thumb merging with the window *across* those 2px. `nord0` was identical to `--gb-bg`; `nord3` was merely near it. Every dark value in Nord is near `--gb-bg`, so on a **light** accent pill there is no dark thumb that works, and the fix is not a thumb colour at all: the dark theme's on pill is **`nord10` rather than `--gb-accent`**, and the thumb is the same near-white in both states. That is the one place a control departs from the shared accent ramp, and the geometry earns it — a checkbox can use a light accent because nothing sits inside its fill, and a toggle cannot because something does. **All of it was caught by looking at a golden image and none of it by a test**, which is now three occasions; a colour that equals another colour is a passing assertion, and a disc that is provably inside its container can still look like it is not ([ADR-0075](adr/0075-a-gestures-origin-is-the-routers.md)). **And every metric in §3 is now actually fixed.** Reported as "the knob is outside the pill when I resize the window", and it was not a toggle bug: Yoga runs with CSS's defaults, so **every node had `flex-shrink: 1`** and a `width: 36px` was a *preferred* width a cramped row could take back. §8 lists `flex-grow/shrink/basis` and only grow was implemented, so there was no way to say otherwise. Measured at 40px of room: a switch's pill 36 → **16** while its 16px thumb did not move, a checkbox's glyph 16 → **10**, a radio's the same and drawn as an *ellipse* since `border-radius` follows the box, and in a short column a control's hit target 32 → **13** — §1.3's 32×32 floor gone. The reported symptom was the only one of the four visible at a glance. `flex-shrink` is implemented now with **no native symbol and no new binding** — `YGNodeStyleSetFlexShrink` was already exported and bound, so the gap was in the CSS engine alone — and the controls declare `flex-shrink: 0` once over a type list, because the rule is "a control's metrics are fixed" and a copy per control is how that stops being true. **The label deliberately still shrinks**: text is the one thing in a control that should give, and a `text` that refused would push the glyph out of the window rather than ellipsing. Six golden scenes were **sized by the bug** — 300×132 for content needing 136, which fitted only because the options were being squashed. The test frames are deliberately absurd, because a regression here is a function of window size and a test at a plausible size is the one that cannot fail ([ADR-0076](adr/0076-a-glyph-does-not-negotiate.md)). **And `slider` ships, with `fader` as its vertical class** — the sixth control, and the first whose value is a **number rather than a state**. Every control before it has a value a stylesheet can name; `toggle-track:checked toggle-thumb { transform: translate(16px) }` is literally how a switch's thumb moves, and that stops working the moment the value is 37.4. So the thumb is placed by **flex ratio** — fill, thumb, rest, with the grow factors carrying the value — and `transform` is not merely awkward here but *unable*: CSS percentages inside `translate` are a proportion of the moving box, so `translate(50%)` moves the thumb by half a thumb rather than to the middle of the track. The ratio yields the **filled portion for free**, as a box the cascade can reach. The second half is `PointerEvent.local()`, the direct sibling of ADR-0075's `dragX()`: where an event landed **inside the widget currently handling it**, re-pointed per handler because dispatch bubbles — a press on the thumb targets the thumb while the slider wants the position along *itself*. The control **snaps and clamps so no application has to**, and each of the three rules is a choice: steps count from `min` (so a 1..10 slider stepping by 2 can reach 1), an arrow offers the next *reachable* value rather than the current plus a step (nothing snaps a value on the way in, because that would be the control overruling the model), and the **ends are always reachable** even when the range is not a whole number of steps. It is also the first control that relies on ADR-0073 putting scope traversal after the focused chain — and it consumes an arrow even when the value did not move, because a slider at its maximum still owns `Right` and letting it through would move focus off the control being adjusted ([ADR-0079](adr/0079-a-continuous-value-is-placed-by-ratio.md)). Still to come: the other seven controls, custom image cursors, and tick marks and a value label for the slider |
| M3 — Shell | not started | Menus, popups, tray, dialogs, scroll, forms, CSD, charts, widget showcase |
| M4 — GPU | not started | `canvas3d`, GPU composition |
| M5 — Hardening | not started | Text editing depth, AccessKit bridge, IME preedit, docs, 0.1 release |

## Module layout

| Module | Artifact | Contents |
|---|---|---|
| `:natives` | `goldberry-natives-{platform}-{arch}` | Hand-written FFM bindings, owning wrappers, and the CMake superbuild that produces `libgoldberry` |
| `:core` | `goldberry-core` | Widgets, style, layout, text, icons, paint, the backend SPI, and the two backends — `headless` and `sdl3` ([ADR-0041](adr/0041-three-platforms-four-artifacts-two-backends.md)) |
| `:widgets` | `goldberry-widgets` | The widget catalog — controls, containers, menus, charts — plus the showcase screens that serve as the visual regression corpus |
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

## Known open questions

These are tracked in the decision log and need answers before the milestones they
block can be scheduled honestly.

**Where the documents disagree with each other** — as opposed to with the code —
is listed in `docs/ARCHITECTURE.md` §17.1. `docs/design-system.md` and
`docs/core-widgets.md` are the authority; the architecture document is a summary
of them and records where it knowingly departs. The five open ones are the
platform primary modifier for accelerators, pixel-precise wheel deltas, whether
the catalog is one module or two, `text style=` against `text class=`, and a
disabled container disabling its descendants.

- ~~**`Sdl3Backend.translate`'s `MOUSE_WHEEL` branch has never run.**~~
  **Answered: it runs, through the real SDL, on every CI run.** A test cannot turn
  a wheel — but `SDL_PushEvent` can, which is what the call is for. A fabricated
  `SDL_MouseWheelEvent`, written at the offsets the layout probe has already
  checked against the compiled C, goes onto SDL's own queue, comes back out of the
  ordinary pump and takes the shipping route: the real `translate`, the real
  window lookup, the real sink. The tests assert the sign is inverted exactly once
  (SDL's y is positive away from the user, the SPI's is positive down the
  document), that "natural scrolling" is undone before that rather than after,
  that a touchpad's fractions survive, and that the position comes from the wheel
  arm's own fields — reading it through the motion arm's accessor returns 3.0
  where the answer is 120.0, because the vertical delta lands at exactly that
  offset. Under SDL's `dummy` video driver, so it needs no display and runs on all
  three platforms. The **cursor** half was already answered: the showcase sets
  `Cursor.CROSSHAIR` at start-up, so `SDL_CreateSystemCursor` and `SDL_SetCursor`
  really run. —
  [ADR-0061](adr/0061-the-events-a-test-cannot-produce-are-pushed.md),
  [ADR-0056](adr/0056-the-wheel-is-lines-and-the-sign-is-ours.md),
  [ADR-0057](adr/0057-the-cursor-rides-on-the-painted-box.md)
- **"Pixel-precise wheel deltas" is not reachable through SDL.** §7.1 asked for
  them with a line-based fallback; SDL reports only detents, as floats. Wayland
  and macOS both have a pixel axis underneath and SDL does not surface it. What
  ships is lines with the touchpad's fractions preserved, which is honest but is
  not what the architecture document originally promised — reaching the real thing
  means going around SDL to the platform. —
  [ADR-0056](adr/0056-the-wheel-is-lines-and-the-sign-is-ours.md)
- ~~**Group opacity is a multiply, not a layer.**~~ **Answered: it is a layer.**
  A node with `opacity < 1` **and children** is composited through an offscreen
  raster drawn at full strength and faded once, which is what CSS specifies.
  `group-opacity.png` is two overlapping squares under a parent at 50%, and the
  test asserts the overlapping pixel *equals* the non-overlapping one — true for
  a layer, false for a multiply. A translucent **leaf** keeps the cheap path
  deliberately: its own shapes can overlap each other, but by a fraction of a
  level on an antialiased edge, and an allocation and a blit per faded label is a
  poor trade. Three goldens with a `:disabled` control at 45% moved, and the diff
  is confined to that control — the correction, reviewed rather than accepted. —
  [ADR-0071](adr/0071-a-layer-is-a-subtrees-raster.md),
  [ADR-0064](adr/0064-a-rounded-rectangle-is-four-cubics.md)
- ~~**`body-strong` is not drawn, and no control uses a weight.**~~ **Answered:
  a weight is a face.** `Inter-SemiBold.ttf` is extracted beside the variable
  file, `font-weight` resolves to one of two shipped faces in the cascade, and a
  button's label is Inter 600 at 13/18. Instancing the `wght` axis would have
  been the smaller download and needed symbols in both HarfBuzz and Blend2D —
  three export branches, answered only by a CI run across four targets — while
  §1.4 ships exactly two weights. The axis stays a real optimisation for the day
  an intermediate weight is specified. —
  [ADR-0066](adr/0066-a-weight-is-a-face-and-color-inherits.md)
- **`text` has no `style="body"` attribute.** `docs/core-widgets.md` §2 asks for
  one; what ships is `class="body"`, which is the same thing spelled the way CSS
  already spells it. Whether a second spelling earns its keep is a question for
  when `field` and `form` need labels. —
  [ADR-0066](adr/0066-a-weight-is-a-face-and-color-inherits.md)
- **`em` and `rem` do not resolve against the node's own `font-size`.** They use
  `CssLength.Context`'s fixed numbers, so `font-size: 1.2em` means 1.2 × 16 and
  not 1.2 × the parent's size. Nothing in the toolkit's own stylesheets uses
  `em`, so it has no effect today — but it is wrong, and the typography scale is
  what makes it reachable. —
  [ADR-0066](adr/0066-a-weight-is-a-face-and-color-inherits.md)
- ~~**Nothing animates.**~~ **Answered for the properties that can.** The frame
  clock, the curves, the overlay, the whitelist, OKLCH interpolation and reduced
  motion all ship, and the frame loop goes idle the frame after a transition
  ends. What is left of §1.7 is listed below rather than here. —
  [ADR-0067](adr/0067-motion-is-an-overlay-on-a-frame-clock.md)
- ~~**`transform` is in §1.7's whitelist and is not implemented.**~~
  **Answered, and the trap it named is what the change is about.** `transform`
  and `transform-origin` parse, cascade, apply down the box subtree the way
  `opacity` does, animate through the overlay, and — the part worth the separate
  record — **route input through the inverse of the matrix the painter used**,
  computed once while painting rather than re-derived on the input path. A
  transform the painter applies and hit testing ignores produces no error and no
  wrong pixel: the control is drawn where the stylesheet asked and simply does
  not respond where it looks like it should. **No new native symbol crosses the
  boundary**: `bl_context_apply_transform_op` was already exported for the
  display scale, and `BL_TRANSFORM_OP_ASSIGN` replaces the context's matrix
  rather than composing onto it — so the stack is accumulated in Java, which is
  also what makes it invertible. Blend2D's `save`/`restore` are not exported and
  turned out not to be needed. A computed `transform` is the **function list**,
  not a matrix, because `translate(50%)` and the `50% 50%` origin default are
  proportions of a box that has no size until Yoga has run — and because halfway
  between `rotate(0)` and `rotate(180deg)`, interpolated entry by entry, is a
  collapsed box rather than a right angle. —
  [ADR-0068](adr/0068-the-transform-stack-is-java-side.md)
- ~~**The check mark still does not scale.**~~ **Answered, and `transform` was
  never what was missing.** §1.7 and §3.1 specify the checkbox tick and the radio
  dot as "scale 0.6→1 + opacity"; the opacity half shipped with ADR-0067 and the
  scale did not arrive with `transform`. The reason is that a `Box.Mark` is drawn
  **onto** the box carrying it, so scaling the indicator scaled the 16px glyph
  with it — the ring grew with the tick. The mark is now a cascade node of its
  own (`check-mark`, `radio-dot`), which makes them the third and fourth parts
  and the first justified by something other than "two surfaces need two
  backgrounds": two things must **move** independently, and the unit of
  independent movement is a node. The mark is built in *every* state and hidden
  with `opacity`, because a node that appears with the value has no previous
  style to move from and would snap. `radio-group-scaling.png` is the frame at
  80 ms of 160, one dot growing in and the one it replaced shrinking out, and
  what it asserts is that **all three rings are the same 16px circle** — which is
  precisely what the naive fix gets wrong. §3.1 now has no unimplemented row for
  any shipped control. —
  [ADR-0073](adr/0073-a-composite-is-one-tab-stop.md),
  [ADR-0068](adr/0068-the-transform-stack-is-java-side.md),
  [ADR-0065](adr/0065-a-part-is-styleable-and-not-constructible.md)

- ~~**`:active` was set on one element, so no control had a pressed state.**~~
  **Fixed.** `:hover` walked the ancestor chain from the beginning; `:active` was
  set on the single deepest element the press landed on — so pressing a
  checkbox's 16px glyph lit up `check-indicator`, pressing its label lit up
  `text`, and `checkbox` itself matched only in the sliver of padding between
  them. `checkbox:active` had been in `controls.css` since the control shipped
  and was very nearly a dead rule. §2.1 requires every control to render a
  pressed state, and one that depends on which of its own parts you hit does not
  have one. Found by trying to write the radio's pressed appearance, not by a
  test — and the test that now covers it asserts the *ancestor*, which is the
  half the original test never looked at. —
  [ADR-0073](adr/0073-a-composite-is-one-tab-stop.md)

- ~~**An unnamed key crashed the window.**~~ **Fixed.** `keyPressed` built a
  `Shortcut` from every key that reached it, to use as a map key. `Shortcut`
  refuses to hold `Key.UNKNOWN` — an accelerator on it could never fire — so the
  `IllegalArgumentException` went up the UI thread with nothing above it. Not an
  edge case: `Key` names the keys a *shortcut* might use, so every letter, digit
  and punctuation mark that arrives as text is `UNKNOWN`, and the crash was one
  keystroke away at all times. The accelerator tests never saw it because they
  only ever pressed keys that had names. —
  [ADR-0073](adr/0073-a-composite-is-one-tab-stop.md)

- ~~**A checkbox was invisible on the surface it normally sits on.**~~ **Fixed,
  and the reason CI missed it is the interesting half.** `--gb-checkbox-bg` was
  `nord1`, which is `--gb-surface`; the light theme's was `#ffffff`, which is
  *its* `--gb-surface`. The token's own comment gives the mistake away — "one
  step up from the window" was measured against `--gb-bg`, and almost nothing
  sits directly on the window. Both glyphs now take the **button's** ramp on each
  theme rather than one of their own, which is the scale §2.1's "one surface
  step" is already defined by. **Every golden image in this repository paints on
  `--gb-bg`**, so a control that disappears on `--gb-surface` was invisible to
  the entire suite; `controls-on-surface-{dark,light}.png` add the missing axis
  rather than one more scene. —
  [ADR-0073](adr/0073-a-composite-is-one-tab-stop.md),
  [ADR-0050](adr/0050-golden-images-have-a-tolerance.md)

- ~~**`--gb-density` is not implemented.**~~ **Answered, and deliberately at four
  controls rather than at thirteen.** §1.3's `regular | compact` ships: every
  control sizes itself from `--gb-control-height`, and `density-compact.css` is a
  three-token `:root` block in the **theme layer** — the same slot as
  `nord-light`, because that layer is defined by what it holds rather than by
  what it is called, and a fifth cascade layer would differ from the fourth in
  its name and nothing else. The layer is also what makes the override work: both
  blocks are `:root`, so specificity ties and `layer` is the only term left to
  separate them, which is why the test asserts the layer rather than the resolved
  height. **`Density.REGULAR` ships no stylesheet at all** — regular is not
  something an application applies, it is what the toolkit already is, and a
  `density-regular.css` restating 32 would be one number in two files, which is
  the arrangement that produced both the §10.1 typography table and the
  checkbox's private surface ramp. **Padding, gap and radius stay literal**,
  asserted so: §1.3's density row names heights and list rows, and tokenising the
  rest "for symmetry" invents a scale the design system does not have.
  `--gb-density` itself is a **marker rather than the mechanism**, because a
  keyword cannot select a number in §8's subset. Every existing golden is
  byte-identical, which is the check that the token swap was a refactor; two new
  ones are the same scene at both densities. The showcase switches on `Ctrl+D`
  and **not one widget in that file mentions a height**, which is the whole of
  what "token-conformant apps adapt with zero code" claims. Named rather than
  implied: **compact is below §1.3's own 32×32 hit-target floor**, deliberately —
  the floor is the *regular* default rather than an invariant, the trade is what
  a density preference *is*, and it is bounded by the glyph staying 16px so
  compact costs margin around the target rather than a smaller target. —
  [ADR-0074](adr/0074-density-is-a-token-swap-and-regular-is-no-stylesheet.md),
  `docs/design-system.md` §1.3

- **`--gb-list-row-height` has no consumer.** It ships with the density because
  the density a `list` will have to honour is decided there rather than in the
  widget, and an application building its own rows today has the token it would
  otherwise hard-code — the argument ADR-0037 made for `ParagraphCache`. `list`
  is M3. —
  [ADR-0074](adr/0074-density-is-a-token-swap-and-regular-is-no-stylesheet.md)

- **Nothing has a minimum size, so overflow is silent.** `flex-shrink: 0` stops a
  control being squashed and does not stop it being *clipped*: a window narrower
  than its content now overflows rather than deforming, which is CSS's behaviour
  and is what a scroll view or an ellipsis is for. Neither exists yet. M3's
  problem, named here because ADR-0076 is what makes it visible. —
  [ADR-0076](adr/0076-a-glyph-does-not-negotiate.md)

- **`flex-basis` is the last of §8's `flex-grow/shrink/basis` still unimplemented**,
  and `:core`'s five primitives all still shrink — right for containers, and
  unexamined for `spacer`, which presumably wants to keep a fixed size. —
  [ADR-0076](adr/0076-a-glyph-does-not-negotiate.md)

- **A slider has no tick marks and no value label.** §3 asks for both as
  optional. The label is the awkward one: it would sit inside the control's own
  box, so the pointer-to-value mapping would stop being "along the control" and
  would need the *track's* rectangle rather than the slider's — which is a reason
  to be glad `local()` is computed per handler rather than once per event. —
  [ADR-0079](adr/0079-a-continuous-value-is-placed-by-ratio.md)

- **`fader`'s dB scale is not implemented.** §3 asks for "optional dB scale
  mapping", which is a non-linear value curve — the same shape of thing `knob`
  will want for its taper. It belongs on the widget as a mapping function and was
  not invented for one caller. —
  [ADR-0079](adr/0079-a-continuous-value-is-placed-by-ratio.md)

- **A slider maps the pointer over its full width**, so at the extremes the
  thumb's centre is up to 8px from the finger. Mapping over the *travel* needs the
  thumb's width, which is the stylesheet's and not the widget's. The mapping is
  monotonic and reaches both ends exactly; closing the gap means a widget being
  told a resolved metric, which is a bigger door than this is worth. —
  [ADR-0079](adr/0079-a-continuous-value-is-placed-by-ratio.md)

- **A toggle's thumb does not follow the pointer during the drag.** It slides to
  its new position when the gesture ends rather than tracking the finger, so a
  drag reads as a switch-with-a-threshold rather than as a thing being pushed. A
  real switch tracks. Doing it needs the thumb's position to come from the
  pointer rather than from `:checked` — an animated value the *widget* supplies,
  for which there is no route today. `slider`, whose §3.1 row is "drag: 1:1, no
  animation", is where that has to be built. —
  [ADR-0075](adr/0075-a-gestures-origin-is-the-routers.md),
  [ADR-0067](adr/0067-motion-is-an-overlay-on-a-frame-clock.md)

- **The toggle does not shrink with a compact density**, read off §3 rather than
  decided: the rows with a compact value carry it in parentheses and the `toggle`
  row does not, so the pill stays 36×20 while the row around it takes
  `--gb-toggle-height`. Whether a 28-tall row holding a 20-tall pill is what
  §1.3 intends is a question for whoever writes the compact screenshots. —
  [ADR-0075](adr/0075-a-gestures-origin-is-the-routers.md),
  [ADR-0074](adr/0074-density-is-a-token-swap-and-regular-is-no-stylesheet.md)

- **Nothing detects the density the user wants**, exactly as with reduced motion:
  an application that knows sets it, and SDL exposes no query for either. Density
  is more often the application's own preference than an OS setting, so this one
  may never need detecting. —
  [ADR-0074](adr/0074-density-is-a-token-swap-and-regular-is-no-stylesheet.md),
  [ADR-0067](adr/0067-motion-is-an-overlay-on-a-frame-clock.md)

- ~~**Arrow-key group navigation inside composites does not exist.**~~
  **Answered, as a mechanism rather than as a radio group.** `Handles.focusScope()`
  makes a subtree one Tab stop with the arrows roving inside it, and `tabs`,
  `menu`, `select`'s popup list and a toolbar all get it by returning `true` from
  one method. The router owns both halves, by the argument already written on
  Tab — traversal is a property of the tree and not of any node in it — and the
  test is written against bare widgets in `:core` rather than against `radio`,
  because the next three users will look nothing like a radio. —
  [ADR-0073](adr/0073-a-composite-is-one-tab-stop.md)

- ~~**A focus scope has no axis.**~~ **Answered.** `Handles.focusScope()` returns
  a `FocusScope` — `NONE`, `HORIZONTAL`, `VERTICAL` or `BOTH` — and `radio-group`
  is the one composite in the catalog that legitimately answers `BOTH`, because
  its direction is its stylesheet's and `.inline` flips it. The **axis is the
  widget's** even though traversal stays the router's: the router cannot know
  what a widget means by the other pair, and the widget cannot see its own
  siblings. It only matters on the path where the widget **declines** the key,
  which is why a boolean survived four controls — arrows reach the focused chain
  first, so a menu bar that handles `Down` itself works either way. The failure it
  prevents is a menu item with no submenu declining `Right` and a `BOTH` scope
  quietly sliding focus to the next item: the user asked to open something and
  the selection moved instead, with no error anywhere. `Home` and `End` belong to
  no axis and reach the ends of any scope, because they name a position in the
  set rather than a direction on screen. Four widgets unblocked by an enum. —
  [ADR-0078](adr/0078-a-focus-scope-has-an-axis.md),
  [ADR-0073](adr/0073-a-composite-is-one-tab-stop.md)

- ~~**A disabled group fades correctly only by an explicit undo.**~~ **Answered,
  and the undo is deleted rather than generalised.** A rule whose only job was to
  undo its own mechanism was the mechanism saying it was the wrong one. —
  [ADR-0077](adr/0077-disabled-propagates-for-input-and-not-for-paint.md)
- ~~**Layer promotion does not exist, so every animating frame repaints the
  window.**~~ **Answered.** A promoted subtree is rasterized at full strength and
  untransformed, so its alpha and matrix apply to the *blit* — and a group that is
  only fading or moving now **keeps its raster**, which is the case §1.7 wanted
  promotion for and which ADR-0071 shipped without. One flag had been answering
  three questions: does the screen differ (damage), does an *ancestor's* raster
  differ (yes, it bakes in this node's finished blit), does *this* raster differ
  (no, alpha and matrix are the composite's). A descendant's opacity **is** baked
  in, which is why it could not be fixed by dropping `opacity` from one
  comparison. Measured on the showcase's tree at 45%: **a frame of the fade is
  199 µs against 554 µs**, 2.8×. `RenderTree.layersRepainted()` is public because
  a cached raster and a fresh one produce the same image, so no pixel assertion
  can tell them apart — which is exactly how the bug survived a test file written
  about layer caching. —
  [ADR-0072](adr/0072-a-partial-repaint-needs-a-promise.md),
  [ADR-0071](adr/0071-a-layer-is-a-subtrees-raster.md)

- ~~**Damage tracking says what to upload, not what to paint.**~~ **It paints
  what changed now.** `bl_context_clip_to_rect_d` and
  `bl_context_restore_clipping` are the third and fourth new exports, and
  `RenderTree.paint(frame, damage)` clips to the damage — **367 µs to 117 µs** on
  a frame where one small box changed. Read that carefully: the damaged area was
  0.23% of the window and the saving is 3.1×, not 400×, because the clip saves
  *rasterization* while the tree walk still visits every box for Blend2D to clip
  away. Skipping the traversal too is a further change and is not made.
  Correctness rests on a **promise the SPI now makes**:
  `BackendWindow.retainsFrameContents()`, false by default so a backend that says
  nothing gets a full repaint. `Window` checks three things that fail
  independently — the promise, the buffer's *identity* (a backend may retain and
  still rotate between two), and the size — plus a fourth case where the backend
  lends nothing and the buffer is `Window`'s own, which retains by construction. A
  clipped repaint is asserted **pixel-identical** to a full one across a whole
  frame, because otherwise damage is a rendering bug with a performance excuse. —
  [ADR-0072](adr/0072-a-partial-repaint-needs-a-promise.md)

- **How damage is computed, and the bug a resize found in it.** Each render
  object remembers where it was, and a node that changed damages the union of
  where it **was** and where it **is** — both, because damaging only the new
  position leaves the old drawing on screen. It reads the node's *own* changed
  flag rather than its subtree's, or a parent whose child moved would report the
  whole window. **A resize broke it in the field**: a remembered rectangle
  belongs to the previous frame, so the union fits neither when a window is
  dragged a pixel narrower, and the backend refused the frame mid-drag. Damage is
  now clamped on the way out rather than only where each rectangle is computed —
  and the regression test resizes by **one pixel**, because that is what a drag
  produces and a test that jumped by fifty would have passed against a fix that
  only handled large changes. Every damage test had used a single frame size,
  which is the natural thing to write and the one case that cannot fail. —
  [ADR-0071](adr/0071-a-layer-is-a-subtrees-raster.md),
  [ADR-0072](adr/0072-a-partial-repaint-needs-a-promise.md)

- **Four symbols were added to the export list**, the first since it caught its
  third local-symbol bug: `bl_context_blit_image_d` and
  `bl_context_set_global_alpha` for layers, then `bl_context_clip_to_rect_d` and
  `bl_context_restore_clipping` for the partial repaint. Nothing else was needed — the offscreen pixels
  are a `PixelBuffer` allocated in Java and wrapped with the already-exported
  `bl_image_init_as_from_data`, which is the principle the export list states in
  its own comment. `BlendLayerTest` is seven pixel assertions that cannot pass
  unless both really exported, and the ELF, MSVC `.def` and Mach-O branches are
  answered by the next CI run rather than by argument. —
  [ADR-0071](adr/0071-a-layer-is-a-subtrees-raster.md),
  [ADR-0018](adr/0018-sdl-conventions-stop-at-the-boundary.md)
- **The overlay enter/exit lifecycle and the imperative `AnimationController`
  are specifications without subjects.** `opening → open → closing → removed`
  applies to menus, popovers, tooltips, dialogs and toasts; the controller drives
  indeterminate progress, the spinner and toast reflow. None of those widgets
  exist, so both are M3 work rather than M2 gaps. —
  [ADR-0067](adr/0067-motion-is-an-overlay-on-a-frame-clock.md)
- **Reduced motion is obeyed but not detected.** `renderer.reducedMotion(true)`
  collapses every transition; nothing reads the OS setting, because SDL exposes
  no query for it. An application that knows sets it. —
  [ADR-0067](adr/0067-motion-is-an-overlay-on-a-frame-clock.md)
- ~~**A disabled container does not disable its descendants.**~~ **Answered, and
  the sentence turned out to have two halves that pull apart.**
  `docs/core-widgets.md` says "disables its descendants for **input and
  semantics**" — and deliberately not for paint, which is where the double-fade
  came from. **Input propagates**: no press, click, wheel, focus or key reaches a
  descendant of a disabled container. **Paint does not**: `:disabled` stays on the
  node that declared it, because the container's own 45% already fades everything
  under it (opacity multiplies down a subtree) and a descendant that also matched
  would land at 20%. It costs nothing in expressiveness, since §2.1 requires
  disabled to be opacity and never a colour remap. The effective value is
  **derived by walking up the ancestors, not stored** — ADR-0073's lesson applied
  again: a second copy of a fact the tree already holds disagrees the first time
  something changes without telling the thing that cached it. The **router is the
  choke point**, one guard in `dispatch` plus `isFocusable`, so a control written
  without its own `disabled` check is still unavailable — and the **keyboard
  needed no guard at all**, because focus is the only route a key has, so one line
  about focus covers `onKey`, `onKeyCapture` and `onText` together. The cut is
  input versus **observation**: enter, exit, motion, hit testing and the cursor
  all still work, which is what keeps ADR-0059's two cases — a click that must not
  fall through, and a tooltip explaining *why* something is unavailable. `form`,
  `group-box` and a `dialog` in its `closing` phase all get this for free. —
  [ADR-0077](adr/0077-disabled-propagates-for-input-and-not-for-paint.md),
  [ADR-0059](adr/0059-a-control-is-a-record-a-node-and-a-rule.md)
- **The rounded corners and the transforms have only been rasterized on
  linux-x64.** Blend2D JITs its pipelines per CPU, so the four cubics and the
  eleventh golden's rotations and skews on AVX-512, on Apple Silicon's NEON path
  and under MSVC are answered by the next CI run rather than by argument — which
  is what the golden images' per-channel *and* area tolerance is for. The
  transform half also rests on `BLMatrix2D` being six consecutive doubles in the
  order `matrix(a, b, c, d, e, f)` writes them, which the layout probe now checks
  against the compiled library on every target because the operand crosses as
  `void*` and a reordered union would produce a skewed frame and `BL_SUCCESS`. —
  [ADR-0064](adr/0064-a-rounded-rectangle-is-four-cubics.md),
  [ADR-0068](adr/0068-the-transform-stack-is-java-side.md),
  [ADR-0050](adr/0050-golden-images-have-a-tolerance.md)
- **Nothing recomputes the cursor when the tree changes under a still pointer.**
  A widget that becomes disabled without the pointer moving keeps the shape it
  had. The fix is re-running `cursorAt` after each paint against the last known
  position; it is worth doing when something can actually change that way. —
  [ADR-0057](adr/0057-the-cursor-rides-on-the-painted-box.md)
- ~~**The state and rebuild API.**~~ **Answered.** The stateful-widget
  lifecycle, rebuild scheduling and dirty-marking are settled: state lives on the
  element, `setState` mutates immediately and defers the rebuild, and the tree
  flushes dirty elements once per frame. —
  [ADR-0052](adr/0052-state-lives-on-the-element-and-rebuilds-are-deferred.md),
  [ADR-0004](adr/0004-three-tree-retained-declarative-model.md)
- ~~**KDL 2.0 Java parser.**~~ **Answered, by writing one.** No third-party
  parser was adopted: the tokenizer and parser are hand-written for the §9
  subset, with the §9 example document as a test. —
  [ADR-0051](adr/0051-kdl-is-parsed-here-and-reloading-is-forgiving.md),
  [ADR-0005](adr/0005-css-subset-and-kdl-as-the-contracts.md)
- ~~**`YGSize` struct-by-value upcall returns.**~~ **Answered, and now driven by
  Yoga itself.** A Java upcall returning `YGSize` by value is called from C and
  arrives intact; the return segment is allocated once per callback rather than
  per call, and an exception thrown by a measure function is held and rethrown in
  Java instead of taking the process with it. The node API is bound, so the
  callback is invoked by real layout passes with the constraints the flexbox
  algorithm arrived at — not by a C probe written for the purpose. Proven on
  linux-x64; the checks run on every target in CI, so the other five are answered
  by the next run rather than by argument. —
  [ADR-0017](adr/0017-proving-the-struct-by-value-upcall.md),
  [ADR-0029](adr/0029-yogas-node-api-and-who-owns-a-node.md)
- ~~**Windows has never been built.**~~ **Answered.** All four targets link, and
  all three export branches are now exercised rather than argued about: the ELF
  version script on both Linux targets, the Mach-O `-u,_symbol` /
  `-exported_symbols_list` pair on `macos-aarch64`, and the MSVC `/INCLUDE:` and
  `.def` branch on `windows-x64`. The Windows leg builds `goldberry.dll`, runs
  `:natives:test` against it with `goldberry.native.required=true` so a skipped
  test cannot pass for a passing one, and matches the golden images — which is
  also what answers Win64's 4-byte `long`, the one thing no other target could
  catch. **What Windows has not done is open a window**: the leg links the
  library and runs the Java tests, exactly the hole
  [ADR-0039](adr/0039-macos-needs-the-first-thread.md) describes for macOS. The
  showcase image workflow is what would close it. —
  [ADR-0012](adr/0012-native-ci-runners-with-a-pinned-glibc.md),
  [ADR-0041](adr/0041-three-platforms-four-artifacts-two-backends.md)
- **Layout verification has not yet passed in CI.** The first run's verify jobs
  failed without running a test, and the fix — verify the downloaded artifact,
  and fail rather than skip when it is absent — has been tested locally against
  every path but has not itself been through CI. —
  [ADR-0016](adr/0016-verify-the-artifact-and-never-skip-the-check.md)
- **The Wayland preference is evidence from one compositor.** SDL chooses X11 on
  a Wayland session unless the compositor advertises `wp_fifo_manager_v1`, which
  GNOME's Mutter does not; Goldberry asks for `wayland,x11` instead, because
  XWayland resizes visibly worse. Confirmed on GNOME only — KDE, Sway and the rest
  are untried, and the driver is logged at start-up so a report can say which one
  it got. —
  [ADR-0027](adr/0027-prefer-wayland-fall-back-to-x11.md)
- **The macOS window opens, and the CI leg still would not have caught it.**
  `gradlew run` failed with "No available video device", which points at the
  superbuild and was not the superbuild: macOS drives AppKit from the process's
  first thread and the java launcher does not put `main` there. The showcase
  passes `-XstartOnFirstThread` on macOS and `Sdl3Backend` appends the
  explanation after SDL says no — as a diagnosis rather than a precondition,
  since a JVM embedded on the real main thread would lack the launcher's
  environment variable and would work anyway. The hole that hid it is still
  open: the macOS leg links the library and runs the `:natives` tests, and never
  opens a window. —
  [ADR-0039](adr/0039-macos-needs-the-first-thread.md)

- ~~**Live resize stalls on Windows and macOS.**~~ **Taken, and half proven.**
  Both platforms run a modal loop during a resize gesture, so SDL does not return
  from event pumping until the drag ends and frames stopped with it. Goldberry now
  installs an `SDL_AddEventWatch` callback and **draws from inside it**: SDL keeps
  pumping events within the platform's loop, and a watch is called from inside
  that pump, so it is the one place a frame can be produced while the platform
  holds the thread. Four guards decide whether it does anything — the UI thread,
  an active sink, re-entrancy, and the event type — and each is there because a
  watch is called in circumstances a pump never is; the resize the queue then
  delivers a second time is coalesced away rather than laid out twice. **What CI
  proves is the whole mechanism except the platform**: a test pushes an event from
  inside an event handler, which is the same state a modal loop creates, and
  asserts that the resize *and* a frame come out of the watch re-entrantly. What
  is left is that Windows' and macOS' loops really do pump during a drag — SDL's
  own documented behaviour, and a human with a mouse is what would confirm it. —
  [ADR-0060](adr/0060-a-resize-draws-from-inside-sdls-event-watch.md),
  [ADR-0024](adr/0024-a-repaint-must-wake-the-loop.md)
- **"Starts in milliseconds" is still unproven.** The timeline exists and the
  first numbers are in ADR-0028 — `SDL_Init(VIDEO)` is ~99ms and dominates, while
  mapping `libgoldberry` is under 2ms — but they were measured under `gradle run`,
  which adds a launcher and its own JVM. The headline claim needs the example
  launched directly. —
  [ADR-0028](adr/0028-the-start-up-timeline.md)
- ~~**Blend2D and AsmJit have no release tags.**~~ **Answered.** Neither upstream
  has ever cut one, so both are pinned by **commit SHA** instead — Blend2D at
  `6dbc2ce` and AsmJit at `0bd5787`, the pair that has actually built, linked and
  passed the tests. All six upstreams now resolve to exactly one commit, so the
  build is reproducible. What remains before publishing is the licence texts. —
  [ADR-0030](adr/0030-pin-blend2d-and-asmjit-by-commit-sha.md)
- **The layout registry is now mostly constants, not layouts.** Seven struct
  layouts and 61 constant rows, 48 of them Yoga enumerators. The struct half has
  a known limit — `YGSize` is identical on all six targets, so its row proves
  nothing the round trip in
  [ADR-0017](adr/0017-proving-the-struct-by-value-upcall.md) does not — but the
  constant half is where the value is: `YGAlignCenter` is 2 and `YGJustifyCenter`
  is 1, and a Java constant that drifts from either produces a layout that is
  wrong on every platform at once and never an error. —
  [ADR-0010](adr/0010-hand-written-ffm-bindings.md),
  [ADR-0029](adr/0029-yogas-node-api-and-who-owns-a-node.md)
- **The export machinery has now caught the same class of bug three times.**
  `--exclude-libs,ALL` forced static-archive symbols local, so `SDL_Init` linked
  in without being exported; removing the flag fixed it, because a version
  script cannot promote a symbol already marked hidden. Blend2D then hit the
  identical wall from the other side: a static build defines `BL_STATIC`, which
  makes `BL_API` expand to nothing, so the superbuild's global `hidden`
  visibility applied to every Blend2D function. All 13 linked in and arrived
  **local** — `nm -D` showed none of them while `nm` showed them all as `t`.
  HarfBuzz then did it a third time and more bluntly: `HB_EXTERN` is defined as
  bare `extern`, with no visibility attribute at all, so all 24 of its symbols
  went local too. Fixed by giving both targets default visibility; the version
  script's `local: *` still gates the output. The fix is a loop rather than two
  blocks, because the next static upstream will probably need it as well. The equivalent question on the MSVC `.def`
  and Mach-O `-exported_symbols_list` branches is still answered by the next CI
  run rather than by argument — and the Mach-O branch has the *same* dependency
  on visibility that this fix addresses. —
  [ADR-0018](adr/0018-sdl-conventions-stop-at-the-boundary.md),
  [ADR-0031](adr/0031-blend2d-and-the-borrowed-buffer.md)

- ~~**Shaping itself is unverified: there is no font to shape with.**~~
  **Answered.** Inter, JetBrains Mono and OpenMoji are fetched at build time,
  pinned by version and SHA-256, and packaged into `goldberry-core`
  ([ADR-0033](adr/0033-assets-are-fetched-and-compiled-not-committed.md)).
  Shaping now runs against real outlines: real glyph ids rather than `.notdef`,
  a proportional face measurably different from a monospace one, and emoji
  resolving through OpenMoji. Right-to-left glyph *reordering* is still
  unchecked — it needs a script the bundled faces cover. —
  [ADR-0032](adr/0032-shaping-is-utf16-in-glyphs-out.md)

- **Painting is not what a frame spends its time on.** Measured on linux-x64 at
  960×640 under Wayland, over sixty frames: acquiring the buffer costs
  130–400 µs, **painting 0.6–3.6 ms** (typically ~1.3), and **presenting
  1.5–21 ms** (typically ~10). Present dominates by roughly an order of
  magnitude. **What it is doing is now known** — and it is not, as this entry
  used to say, mostly waiting on the compositor. SDL's Wayland driver implements
  no window surface, so `SDL_GetWindowSurface` falls back to a hidden
  `SDL_Renderer`: every present is a copy into a streaming texture, a render
  pass, and a swapchain wait. At 960×640 that splits about 1.05 ms of copy,
  0.7 ms of render-and-present, and 4.8 ms of blocking — three quarters of
  present is a block rather than work
  ([ADR-0046](adr/0046-what-present-actually-does.md)). **The largest of those is
  fixed**: the loop was running at ~105 fps into a 59.96 Hz panel and throwing
  two frames in five away. Goldberry now asks SDL to hold each present until
  vertical blank, and where that request is ignored the loop paces itself to the
  refresh rate read off the window's current display —
  `SDL_GetDisplayForWindow` and `SDL_GetCurrentDisplayMode`, with
  `SDL_DisplayMode` verified against the compiled library by the layout probe.
  Paced, present falls from 5.51 ms to 1.20 ms — the block does not shrink, it
  disappears, leaving exactly the CPU that was always underneath — paint falls
  with it from 2.25 ms to 1.61 ms, and the UI thread spends 165 ms of each second
  in the frame path instead of 862, showing the same frames
  ([ADR-0047](adr/0047-a-frame-nobody-sees-costs-full-price.md)). What is left:
  **damage
  tracking**, now worth under a millisecond a frame; and **owning the renderer**,
  the only route to the zero-copy path
  ([ADR-0031](adr/0031-blend2d-and-the-borrowed-buffer.md)) was believed to have.
  Blend2D's `thread_count` is a fourth, and only matters if paint ever becomes
  the bottleneck. —
  [ADR-0031](adr/0031-blend2d-and-the-borrowed-buffer.md),
  [ADR-0046](adr/0046-what-present-actually-does.md),
  [ADR-0047](adr/0047-a-frame-nobody-sees-costs-full-price.md)

- **A build with no network cannot produce a usable `goldberry-core`.** The
  bundled fonts and icons are fetched from upstream releases and cached, so this
  bites once per checkout rather than once per build — but a jar assembled
  without the asset step contains a toolkit that cannot render text. The build
  already needed network for the native superbuild, so no new constraint; it is
  written down because the failure is far from its cause. —
  [ADR-0033](adr/0033-assets-are-fetched-and-compiled-not-committed.md)

- ~~**Nothing draws a glyph or an icon yet.**~~ **Both do.** `bl_font_*` and
  `bl_context_fill_glyph_run_d_rgba32` were bound first; the path API followed —
  seventeen symbols, one per SVG command, plus the three stroke options an icon
  needs because Lucide is drawn in strokes rather than fills. `SvgPath` reads the
  table's path data with SVG's own number grammar, and every one of the 1544
  icons is asserted to parse and produce geometry. **What is still open is that
  an icon is not a `Box`**: the showcase draws them over its sidebar rather than
  laying them out in it, because nothing decides an icon's intrinsic size until
  the widget model does. —
  [ADR-0043](adr/0043-icons-are-stroked-paths.md),
  [ADR-0004](adr/0004-three-tree-retained-declarative-model.md)

- **The units between the two text libraries are a convention, not a checked
  fact.** HarfBuzz reports positions in whatever scale its font was set to;
  Blend2D multiplies them by `size / units-per-em`. Both are right, and applying
  a size on both sides applies it twice — 128&times; for Inter at 16 points —
  which draws text off the edge of the window and returns `BL_SUCCESS`. The
  layout table cannot catch this: it is an agreement *between* two libraries, not
  a fact about either. What holds it is `Font` owning both objects and never
  scaling the shaper, plus a test that compares the inked span against the
  measured width. Anything that builds a `ShapedFont` and a `BlendFont` by hand
  can still get it wrong. —
  [ADR-0034](adr/0034-one-size-and-the-design-unit-crossing.md)

- ~~**A `Font` costs two copies of the font file, and there is one per size.**~~
  **Two copies per *face* now, not per size.** `FontFace` holds HarfBuzz's whole
  font — which is size-independent because Goldberry never scales the shaper —
  and Blend2D's data and face; `Font.on(face, size)` adds only the object the
  size lives on. A second size measures at 4.4 µs against 681, and four sizes of
  Inter cost three megabytes rather than twelve. Faces are owned explicitly
  rather than cached globally, because these objects are thread-confined and a
  per-thread cache of native memory has no hook that would ever free it. What
  remains is the two copies themselves: each library owns its own memory, and
  neither takes a borrowed buffer for font data. —
  [ADR-0044](adr/0044-one-face-many-sizes.md)

- ~~**Nothing measures text for layout yet.**~~ **It does.** A `Paragraph` shapes
  once and wraps with arithmetic, and its measure function reports a height to
  Yoga through the `YGSize` upcall. What is still ahead is bidi run splitting —
  right-to-left text is **refused at construction** rather than mis-wrapped,
  because HarfBuzz returns those glyphs in visual order and prefix sums taken in
  logical order would measure the wrong ones — and font fallback between the UI
  and emoji slots, which makes a paragraph several runs rather than one. —
  [ADR-0036](adr/0036-the-paragraph-is-shaped-once-and-wrapped-many-times.md)

- **A line boundary keeps a kern it should drop.** Each line is a slice of the
  whole paragraph's single shaping, so the kern between the last character of one
  line and the first of the next is included where a per-line shaping would drop
  it. A fraction of a pixel at the end of a line, in exchange for wrapping that
  costs no shaping at all. Re-shaping only the final lines, and only for painting,
  is the fix if it ever shows. —
  [ADR-0036](adr/0036-the-paragraph-is-shaped-once-and-wrapped-many-times.md)

- ~~**The paragraph cache is a one-entry memo.**~~ **Both caches exist, and the
  numbers say why.** `ParagraphCache` holds shaped paragraphs keyed by
  `(font, text)`; the width memo stays inside each `Paragraph`. Shaping is 56 µs
  and a cache hit is 0.05 µs, while a memoised wrap is already 0.02 µs — so
  shaping is the only part worth a cache, and caching layouts would save nothing.
  The cache has **no consumer yet**, because nothing rebuilds a widget tree; it
  exists because the measurement says it will be needed the moment something does.
  §6's third key component, the width bucket, is the per-paragraph memo, and the
  "resolved text style" is a `Font` until the CSS engine has something better. —
  [ADR-0037](adr/0037-what-the-text-path-costs.md)

- ~~**A fresh upcall stub per text box per frame is the largest cost of text in a
  layout pass.**~~ **Answered: the render tree is retained.** `RenderObject` owns
  a `YGNode` that survives the frame and keeps its measure callback for as long as
  the paragraph behind it is the same instance. Measured on a showcase-shaped tree
  with seven measured leaves at 960×640: **layout and walk fall from 190 µs to
  7.2 µs**, and a whole frame from **354 µs to 148 µs**. The 7.2 µs row is the one
  that had to be won — it hands over a *fresh box tree every frame*, as a real
  application produces, and it matches the do-nothing case because every Yoga
  setter is guarded by a comparison against the box already applied. Yoga dirties
  a node when a style is **set**, not when it changes, so an unguarded retained
  tree would cost exactly what a thrown-away one costs plus the memory management.
  Retention also introduced this repository's first keep-state bug, caught by its
  own equivalence test: **Yoga does not dirty a node when its measure function is
  replaced**, so a paragraph swapped for longer text reported the height cached
  for the old one — six lines of prose laid out as one, with no error anywhere. —
  [ADR-0069](adr/0069-the-render-tree-is-retained.md),
  [ADR-0037](adr/0037-what-the-text-path-costs.md),
  [ADR-0004](adr/0004-three-tree-retained-declarative-model.md)

- **A window was laying its tree out twice per frame**, once to paint and once to
  find out where it had painted, and nobody had noticed. `HitTest.capture` took a
  frame and a box tree and built a whole second Yoga tree to answer.
  `HitTest.capture(RenderTree)` reads the pass `update` already ran. —
  [ADR-0069](adr/0069-the-render-tree-is-retained.md),
  [ADR-0054](adr/0054-hit-testing-runs-against-the-painted-frame.md)

- ~~**The cascade is now the largest term in a frame.**~~ **Answered: it resolves
  invalidated nodes, which is what §5 always said it did.** A node's resolved
  style is cached on its element and checked by identity against two things — the
  **resolver**, so a theme swap or a hot reload invalidates everything at once
  with no event to remember to fire; and the **inherited style**, so a parent that
  re-resolved hands its children a different instance and they re-resolve without
  being told. Invalidation is a **subtree**, because a descendant combinator means
  a node's own match depends on an ancestor's state:
  `checkbox:hover check-indicator` restyles the indicator while the checkbox's own
  style need not change at all, and that rule is in `controls.css` today. One
  hook — `setPseudoClass` — covers `:hover`, `:active`, `:focus`, `:disabled`,
  `:checked` and `:indeterminate`, and fires only on an actual change, which
  matters because the renderer mirrors three of them onto every styled element
  every frame. **The CPU a frame spends before rasterizing falls from 148 µs to
  3.5 µs** — 354 µs to 3.5 µs taken with the retained render tree, a factor of a
  hundred. —
  [ADR-0070](adr/0070-the-cascade-resolves-invalidated-nodes.md),
  [ADR-0052](adr/0052-state-lives-on-the-element-and-rebuilds-are-deferred.md)

- **Rasterization is now the frame, and there is nothing else of consequence
  left.** With Blend2D pinned to one thread a 960×640 frame is about 320 µs,
  essentially all of it painting; threaded, it spreads over four workers. Two
  rounds of removing CPU work have made damage tracking and layer promotion the
  honest next target rather than one option among several. —
  [ADR-0070](adr/0070-the-cascade-resolves-invalidated-nodes.md),
  [ADR-0069](adr/0069-the-render-tree-is-retained.md)

- **`customPropertiesFor` still walks to the root**, re-running the whole cascade
  at every ancestor, so it is *O(depth × rules)* where it could be *O(rules)*. The
  style cache amortises it almost to nothing, but a first frame and every
  invalidated subtree still pay it. Worth doing when a deep tree makes a first
  frame visible. —
  [ADR-0070](adr/0070-the-cascade-resolves-invalidated-nodes.md)

- **Painting now dominates a frame, and half of that reversal is a driver change.**
  Over 119 frames at 960×640 with text: buffer 0.18 ms, **paint 5.10 ms**, present
  1.92 ms, total 7.86 ms median, 14.18 ms at p95, and 3 frames of 119 over the
  16.67 ms budget. ADR-0031 had paint at ~1.3 ms and present at ~10 ms and
  concluded present dominated by an order of magnitude. Text is what moved paint;
  **X11 rather than Wayland is what moved present**, since these frames were
  measured on X11 after the Wayland run crashed the compositor. The like-for-like
  Wayland measurement is still owed, and nothing here made present faster.
  ~~Blend2D's `thread_count` was parked in ADR-0031 as "only matters if paint
  ever becomes the bottleneck"; on these numbers it has.~~ **Taken.** Up to four
  workers, on any surface over 400×300. —
  [ADR-0037](adr/0037-what-the-text-path-costs.md),
  [ADR-0031](adr/0031-blend2d-and-the-borrowed-buffer.md),
  [ADR-0042](adr/0042-blend2ds-workers-and-how-many.md)

- ~~**The isolated paint benchmark and the in-app paint number disagree by
  10×.**~~ **Answered: it is `present`.** A frame that follows a present costs
  about four times what the same frame costs painted back-to-back — 2.19 ms
  against 0.57 ms, measured by skipping present and changing nothing else — and
  the benchmark never presents. It was **not** the borrowed compositor buffer,
  which was the standing hypothesis: painting into a heap buffer measured 2.28 ms
  against the surface's 2.22 ms. Nor the icons (+0.01 ms), the display server
  (Wayland 2.22, X11 2.07), the compositor (SDL's `dummy` driver 2.00), or the
  environment at all — the benchmark's own loop, run *inside* the live
  application between two real frames, came out at 0.49 ms while those frames
  cost 2.06 and 2.25. The mechanism is cache and TLB pollution; a synthetic
  96 MB eviction between iterations reproduces 1.6× of the 3.8×. —
  [ADR-0045](adr/0045-a-frame-is-not-a-benchmark-iteration.md)

- **Present costs 6.6 ms with no compositor to wait for.** The question
  ADR-0045 opened while closing another.
  [ADR-0031](adr/0031-blend2d-and-the-borrowed-buffer.md) measured present at
  ~10 ms and concluded "most of it is waiting on the compositor rather than
  copying". Under SDL's `dummy` video driver — no compositor, no display, no
  surface to hand anyone — present still measures **6.6 ms**, essentially the
  same as under Wayland. Whatever that time is, the explanation on record is
  wrong, and present is the largest single term in a frame. —
  [ADR-0031](adr/0031-blend2d-and-the-borrowed-buffer.md),
  [ADR-0045](adr/0045-a-frame-is-not-a-benchmark-iteration.md)

- **The compositor still dies, and shutting down cleanly did not stop it — the
  core dump says whose bug it is.** The entry below concluded that exiting with a
  live Wayland surface was the trigger and that `Goldberry.shutdown()` was the
  fix. The showcase has called `shutdown()` ever since, and GNOME Shell crashed
  twice more on 2026-08-17. `/var/crash` had the core, and it names the frame:

  ```text
  wl_event_loop_dispatch                    libwayland-server
    → wl_client_destroy                     libwayland-server
      → <destroy listener>                  libmutter-14
        → g_signal_handler_disconnect       libgobject
          → g_type_check_instance           ← SIGSEGV
  ```

  Mutter, tearing down a departing client, disconnects a signal handler on a
  GObject that `g_type_check_instance` rejects — an instance already finalized.
  **That is unambiguously a compositor bug**: `wl_client_destroy` runs whenever
  *any* client goes away, for any reason, and surviving it is the one thing a
  compositor cannot be excused from. Our own process exits 0 with no JVM crash
  log, having destroyed its window and called `SDL_Quit` first. The nearest
  exported symbol below the faulting frame is `meta_xwayland_signal`, 2.2 KB
  back, so the crashing function is a static one in Mutter's Xwayland area —
  suggestive, not conclusive, and not enough to file upstream on its own.

  What is left for this repository is **not** a fix but a defence: nothing should
  be able to open a real surface by accident. See the entry below on the two
  unreliable ways to ask for a headless run. Reproducing this deliberately costs
  the developer their session, so it is not something to iterate on casually.
  gnome-shell 46.0-0ubuntu6~24.04.14, Ubuntu 24.04, under VirtualBox/vmwgfx.

- **Both ways to run the showcase headlessly were broken, and one of them cost a
  desktop session.** `goldberry.backend.videoDriver` existed and was *not* in
  `:example`'s forwarded-property list, so `-Dgoldberry.backend.videoDriver=dummy`
  reached the Gradle daemon and stopped there — the exact failure the comment
  beside that list already described for `goldberry.log.level`. The obvious
  fallback, `SDL_VIDEODRIVER=dummy` in the environment, does not work either: a
  `JavaExec` fork inherits the **daemon's** environment rather than the one
  `gradlew` was invoked with, so it applies or does not depending on how the
  daemon happened to be started — which reads as flaky rather than as broken. A
  run intended to be headless therefore opened a real Wayland surface and took
  GNOME Shell down with it. The property is now forwarded, and
  `./gradlew run -Pgoldberry.backend.videoDriver=dummy` is the checked way to
  drive the showcase without a compositor.

- **The toolkit never shut SDL down, and a compositor died of it.**
  `Sdl3Backend.close()` destroys every window and calls `SDL_Quit`; nothing called
  it. `Goldberry.run()` returning does not shut the runtime down — its contract
  says so — and `Goldberry.stop()` ends the loop with the window still open, so
  the showcase exited with a live Wayland surface and let the socket close. GNOME
  46's Mutter then crashed unwinding the connection, in
  `wl_client_destroy` → its destroy listener → `g_signal_handler_disconnect`, on a
  GObject already freed. **That is a compositor bug** — every killed process
  disconnects abruptly and a compositor has to survive it — but disconnecting
  properly is right regardless, and the showcase now calls `Goldberry.shutdown()`.
  Open: whether `run()` should shut down on return, which would change a
  documented contract. Seen once, on GNOME 46.0 under VirtualBox/vmwgfx, after
  SDL3 moved from `release-3.2.0` to `release-3.4.14` in the same session. —
  [ADR-0022](adr/0022-window-is-the-front-door.md)

- ~~**Every frame damages the whole window.**~~ **Answered for the upload.**
  Something now knows which parts changed: the retained render tree remembers
  each node's rectangle and reports the union of old and new for whatever moved.
  What is still true is that the *painting* is full-frame — see the damage entry
  above for why that needs an SPI change rather than more code here. —
  [ADR-0071](adr/0071-a-layer-is-a-subtrees-raster.md),
  [ADR-0004](adr/0004-three-tree-retained-declarative-model.md)

- **AsmJit's W^X handling on Apple Silicon is now reachable.**
  [ADR-0002](adr/0002-cpu-rasterization-with-blend2d.md) flagged that Blend2D
  JIT-compiles its pipelines and that macOS needs `MAP_JIT` and
  `pthread_jit_write_protect_np`. Nothing triggered it until now, because
  nothing created a rendering context. The first frame the macOS build paints is
  the test. — [ADR-0031](adr/0031-blend2d-and-the-borrowed-buffer.md)
- ~~**CMake arguments live in five places.**~~ **The refs do not any more.**
  `CMakeLists.txt` reads `gradle/libs.versions.toml` itself, so a ref bump is one
  edit and there is no default to drift from; a floating ref is refused at
  configure time. The manylinux container never needed a JDK to read the catalog,
  only something that can parse a text file. `checkPinnedRefs` is inverted — it
  asserts no copy has come back, across *every* workflow rather than three, which
  is what would have caught `example.yml` pinning Blend2D to a floating `master`.
  The rest of the argument list — build type, install prefix, target id — is
  still kept in step by hand. —
  [ADR-0035](adr/0035-the-catalog-is-the-only-place-a-ref-lives.md)
- **No licence text is vendored yet.** Every file in `licenses/` is a placeholder.
  `./gradlew checkLicenses -Pgoldberry.releaseCheck=true` fails until they are
  copied verbatim from the pinned upstream revisions. —
  [ADR-0015](adr/0015-licensing-and-third-party-disclosure.md)

- **Nothing is publishable yet: there are no publications.** §15 says the four
  classifier jars and `goldberry-core`, `-widgets`, `-gpu` go to Maven Central
  under `io.github.digitalsmile`. The half that exists is the artifact half —
  `release.yml` reuses the three per-OS workflows in one run, so all four
  libraries are built and downloaded into one job, and `:natives:nativeJars`
  packages them into classifier jars from `-Pgoldberry.artifactsDir`. The half
  that does not exist is publishing: **no subproject applies `maven-publish`**, so
  there is no publication, no POM, no javadoc or sources jar, no signing, no
  Central credentials and no `publish` task. The two `PublishToMavenRepository`
  lines in `assets` and `example` disable something that was never configured.
  `release.yml` therefore ends at `upload-artifact`, and it has still never run. —
  `docs/ARCHITECTURE.md` §15, [ADR-0009](adr/0009-publish-under-io-github-digitalsmile.md)
