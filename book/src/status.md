# Status

Tracked against the milestone ladder in `docs/ARCHITECTURE.md` §16.

| Milestone | State | |
|---|---|---|
| Foundation | **done** | Multi-module Gradle (Groovy DSL), version catalog, convention plugins, JPMS module graph, JDK 25 toolchain, JUnit 6, licence disclosure, decision log |
| M0 — Skeleton | **done** | **The superbuild links on all four targets.** Blend2D, AsmJit, SDL3, Yoga and HarfBuzz statically combine into one `libgoldberry` exporting exactly the symbols on the export list and nothing else — both Linux targets in CI's manylinux containers, `macos-aarch64` on an Apple Silicon runner, and **`windows-x64` under MSVC**. The layout probe passes against the real library, and Yoga's measure callback crosses in both directions including the `YGSize` struct-by-value return ([ADR-0017](adr/0017-proving-the-struct-by-value-upcall.md)), so the hand-written binding mechanism is proven end to end. **Yoga's node API is bound**, and the callback is now driven by real layout passes rather than by a C probe written for the purpose ([ADR-0029](adr/0029-yogas-node-api-and-who-owns-a-node.md)). SDL3's lifecycle, error and version calls are bound and tested against the real library ([ADR-0018](adr/0018-sdl-conventions-stop-at-the-boundary.md)). The backend SPI, the `headless` backend and the `sdl3` backend are in `:core`, with fractional DPI correct by construction ([ADR-0019](adr/0019-the-backend-spis-first-cut.md)) and background work on virtual threads that completes on the UI thread ([ADR-0020](adr/0020-one-ui-thread-and-virtual-threads-behind-it.md)). **The showcase opens a window and presents frames** ([ADR-0021](adr/0021-the-example-is-a-separate-build.md)), through a `Window` front door that names no backend and builds no event loop ([ADR-0022](adr/0022-window-is-the-front-door.md)). **Windows closed the milestone**: `goldberry.dll` builds, `:natives:test` passes against it with `goldberry.native.required=true` so nothing skips, and the golden images match — which answers the MSVC `/INCLUDE:` and `.def` branch of the export machinery and Win64's 4-byte `long` at the same time ([ADR-0041](adr/0041-three-platforms-four-artifacts-two-backends.md)) |
| M1 — Vertical slice | **started** | **Blend2D rasterizes the frame, HarfBuzz shapes the text.** `Frame` no longer writes pixels by hand: it wraps the platform's own buffer in a `BLImage` without copying it, scales the context by the display factor so coordinates stay logical and fractional edges antialias rather than snap, and blends with alpha that now means something ([ADR-0031](adr/0031-blend2d-and-the-borrowed-buffer.md)). The showcase paints through it. Shaping takes UTF-16 straight from a Java `String`, so the cluster indices point back into the caller's own text ([ADR-0032](adr/0032-shaping-is-utf16-in-glyphs-out.md)). **Text draws.** Blend2D's font chain is bound and a `GlyphRun` reaches the rasterizer: `Font` in `:core` owns a HarfBuzz font and a Blend2D one over the same bytes, shapes in design units and puts the size on the Blend2D font alone, so the font matrix is the only thing that converts ([ADR-0034](adr/0034-one-size-and-the-design-unit-crossing.md)). The showcase draws two lines of Inter, and the tests assert *where* the ink landed — the inked span matches the measured width, which fails by a factor of 128 if either side of that crossing is wrong. **And text takes part in layout.** A `Paragraph` shapes once and wraps with arithmetic over that one `GlyphRun`, so its measure function answers Yoga from inside a layout pass without shaping again ([ADR-0036](adr/0036-the-paragraph-is-shaped-once-and-wrapped-many-times.md)). A `Box` with text is a measured leaf: the showcase's body wraps to whatever width the sidebar leaves it, and its siblings are positioned against the height that comes back. Two numbers are written down in that layout — the bar's height and the padding — and everything else comes from content. **The cache and the benchmarks are done** ([ADR-0037](adr/0037-what-the-text-path-costs.md)): `./gradlew benchmark` measures the text path, and the numbers say the upcall crossing is ~0.3 µs, a memoised wrap 0.02 µs, and shaping 56 µs — so `ParagraphCache` caches shaping and nothing else. **Painting is now multithreaded, and icons draw.** Blend2D rasterizes a frame across up to four workers on any surface over 400×300, which takes a 960×640 paint from 0.47 ms to 0.34 ms and a 4K one from 6.0 ms to 2.3 ms; a threaded frame is asserted pixel-identical to a synchronous one at every worker count ([ADR-0042](adr/0042-blend2ds-workers-and-how-many.md)). Blend2D's path API is bound and Lucide's 1544 icons reach the screen as stroked paths, all of them asserted to parse ([ADR-0043](adr/0043-icons-are-stroked-paths.md)). And a typeface is loaded once rather than once per size: `FontFace` holds the shaper and Blend2D's face, so a second size costs 4.4 µs instead of 681 and no second copy of the file ([ADR-0044](adr/0044-one-face-many-sizes.md)). **The 60 fps claim now holds at the tail, not just the median.** A 960×640 frame with a wrapped paragraph used to run at a 7.86 ms median and a 14.18 ms p95 — a factor of two in hand on the median and none at the tail. Pacing the loop to the display ([ADR-0047](adr/0047-a-frame-nobody-sees-costs-full-price.md)) took that to a **3.13 ms median and a 4.28 ms p95**, which is 3.9× of headroom where there was effectively none; the old numbers reproduce exactly when the pacer is turned off with `-Dgoldberry.frame.rate=0`, which is what they were measuring. Two thirds of that frame was work thrown away on frames the display never scanned out. **What remains of the claim is breadth, not budget**: it is still one machine, and that machine is a VirtualBox VM. The milestone asks for Linux, macOS and Windows. **Yoga and Blend2D now meet**: `BoxPainter` lays a flexbox tree out and fills the result, setting Yoga's point scale factor from the display scale so computed edges land on physical pixels — the first code for which the fractional-DPI claim is a mechanism rather than an intention. Inter, JetBrains Mono, OpenMoji and Lucide's 1544 icons are fetched at build time, pinned by checksum, and packaged into `goldberry-core` ([ADR-0033](adr/0033-assets-are-fetched-and-compiled-not-committed.md)) |
| M2 — Widgets & style | **started, engines done** | **The CSS engine is done, end to end.** A hand-written tokenizer and parser for the §8 subset, matching right-to-left with backtracking, the four fixed cascade layers, custom properties and `var()` — ending at a `ComputedStyle` that carries typed values and nothing else ([ADR-0049](adr/0049-the-css-engine-stops-at-computedstyle.md)). `Box.style(ComputedStyle)` is the join the property split was stated for: layout properties land on the fields Yoga reads, paint properties on the ones Blend2D reads. **Nord light and dark ship** as custom-property layers — two files whose only selector is `:root`, so switching a theme repaints widget rules that never mention a colour (§10). **Golden-image CI runs on all three platforms**: six scenes driven through the whole pipeline, compared with a per-channel *and* an area tolerance, because Blend2D JITs its pipelines per CPU and bit-equality across AVX2 and NEON is not a promise anyone made ([ADR-0050](adr/0050-golden-images-have-a-tolerance.md)). **KDL 2.0 parses and inflates**, including the §9 example document as a test, with a registry that refuses unknown nodes by position; and **hot reload works for stylesheets and markup alike** — strict on first load, forgiving on every reload, because a file being edited is broken more often than it is whole ([ADR-0051](adr/0051-kdl-is-parsed-here-and-reloading-is-forgiving.md)). **All three trees now exist.** Widgets are immutable records; the element tree persists across rebuilds and is what the cascade talks to, so `:hover` survives a parent re-describing its child; state lives on the element, `setState` mutates immediately and defers the rebuild, and ten calls in one handler cost one build ([ADR-0052](adr/0052-state-lives-on-the-element-and-rebuilds-are-deferred.md), which closes the gap ADR-0004 left open). The render tree is materialized as a `Box` tree per frame rather than retained ([ADR-0053](adr/0053-the-render-tree-is-a-box-tree-for-now.md)). **Five primitives ship** — `text`, `row`, `column`, `panel`, `spacer` — and **the parity invariant of §11 is enforced**: each is a Java record, a KDL node and CSS-selectable by type, id and class, with a test asserting the Java-built and KDL-built values are equal. A golden image runs the whole stack, KDL to pixels. **Pointer input routes.** A box carries an opaque owner tag, so a rectangle on screen leads back to its element; hit testing runs against the snapshot taken while painting rather than a fresh layout, because a pointer event is about what the user can see. Dispatch is capture → target → bubble with `consume()`, `:hover` moves along the whole ancestor chain and only where it differs, `:active` follows the press, and focus walks up to the nearest focusable ancestor with `:focus` and `:focus-visible` kept distinct ([ADR-0054](adr/0054-hit-testing-runs-against-the-painted-frame.md)). The sdl3 backend translates all of it — motion, buttons, wheel, keys and committed text — and `GoldberryRuntime` drives the router from a real window. **§7's remaining gaps are closed.** The wheel arrives in lines, fractional and positive down, with SDL's away-from-the-user sign and the "natural scrolling" inversion both undone at the boundary, so a widget never sees either ([ADR-0056](adr/0056-the-wheel-is-lines-and-the-sign-is-ours.md)). **A press captures the pointer** until the release, so a drag that leaves a widget still reaches it and `:active` cannot get stuck; an explicit capture outlives the release, for a gesture that does ([ADR-0058](adr/0058-a-press-captures-the-pointer.md)). **The cursor rides on the painted box**: `cursor: pointer` resolves through the cascade onto the rectangle, and hit testing reads it back off whatever the pointer is over — so inheritance is the stack of rectangles rather than the element tree, and it freezes during a drag ([ADR-0057](adr/0057-the-cursor-rides-on-the-painted-box.md)). And **accelerators are bound per window**, `router.shortcut("Ctrl+S", ...)`, fired after the focused chain declines the key so a text field keeps its own `Ctrl+A`; letters and digits joined `Key` for exactly this, since a modified letter produces no text event anywhere. Tab and Shift+Tab traverse in document order. **And the catalog has started.** `button` ships in `:widgets` — a Java record, a KDL node and a CSS type, with a test asserting the first two produce equal values; variants are classes because that is the one spelling Java, KDL and CSS can all use; the metrics are the design system's in the toolkit-base layer and the colours are component tokens in each theme, because a hover lightens on Nord dark and darkens on Nord light ([ADR-0059](adr/0059-a-control-is-a-record-a-node-and-a-rule.md)). It activates on a **click** — a synthetic event the router raises only when a press and its release land on the same node, so dragging off to cancel works — and on `Space`/`Enter`, ignoring repeats. The `action` half of §9 is wired: markup names an action and an `Actions` registry resolves it, strict by default so a typo fails at inflation rather than producing a button that silently does nothing. `padding` grew CSS's 1–4 value shorthand and its four longhands on the way, because `padding: 0 12px` is the button's own metric. **`button` is finished, not started**: label, icon, or both — an icon is a `Box` now, which closes the question ADR-0043 left open, and it turned out to need no measure function because an icon is built at a size and that size *is* its intrinsic one. `disabled` refuses every route to the action, drops the button out of the Tab order and matches `:disabled`, which is the one pseudo-class a widget owns rather than the router. Markup names an icon against a registry for the same reason it names an action: an `Icon` owns native memory, and a document reloaded on every keystroke would leak one per reload. **Four golden images** cover the variants on both themes, the five states side by side, and the icon layout — the check that catches a padding on the wrong edge, which no value assertion can. **And the showcase is a widget tree**: bar, sidebar, wrapped prose and a row of buttons, with `setState`, theme switching, `Ctrl+T`, focus that survives a rebuild, and `:hover` that repaints itself. **And `bind` is done, which closes the second half of §9's wiring.** A `Property<T>` is a cell with listeners and nothing else — `get`, `set`, `subscribe` — and `set` does nothing when the value is unchanged, which is what makes two properties mirroring each other settle instead of recursing. `Bindings` is the third registry beside `Actions` and `Icons` and is deliberately the same shape: markup names a path, the registry resolves it, strict by default. **A path is `prefs.frost` and nothing else** — the §17 fork is settled at dotted paths, enforced by the registry, so `bind="!prefs.frost"` fails at inflation with the text quoted rather than producing a control that silently never updates ([ADR-0062](adr/0062-bind-is-a-path-and-nothing-else.md)). The binding lives on the widget and the subscription on its element, so a bound node has no wrapper element and `panel > text` styles it exactly like an unbound one; a change marks the element dirty by the same route `setState` does, so three changes in one frame cost one build. `text bind="user.name"` works from KDL and from Java, with the parity test extended to cover it, and the showcase's sidebar carries a line that follows a property nothing in the tree owns — set from a virtual thread, redrawn without anything reaching into the widgets. **And binding is one-way, which is a change to §9**: a widget is handed the read-only `Observable` half of a property, so markup can read a value and not write it, and what the user did travels back up as an action — `checkbox bind="prefs.frost" change="toggleFrost"`. A control is therefore controlled in the React sense: the tick moves when the application sets the property, not when the pointer lands. §9's "one/two-way" is amended to say one-way, deliberately and on the record ([ADR-0063](adr/0063-data-flows-down-events-flow-up.md)). **And the paint layer can now draw what the design system asks for.** `border-radius`, `border`, `outline` and `opacity` reach `Box`, and a rounded rectangle is built from four cubics through the already-exported `bl_path_cubic_to` rather than from a new Blend2D symbol — so the corner works on every target on the first CI run instead of the one after the export list found out ([ADR-0064](adr/0064-a-rounded-rectangle-is-four-cubics.md)). **`button` complies with its own metrics row** (§3): radius 8, the design system's focus ring — 2px `--gb-focus` at a 2px offset, following the radius, written once for every control rather than per control — and `:disabled` as **45% opacity rather than a colour remap** (§2.1), so a disabled `danger` button still reads as dangerous where eight muted tokens had made every disabled button look alike. Removing the remap exposed that a disabled control still lightened under the pointer; CSS would spell the fix `:not(:disabled):hover` and `:not()` is not in §8's subset, so `PointerRouter` refuses to *set* `:hover` or `:active` on a disabled widget — one choke point, every control, forever. **And `checkbox` ships**: three states with `:indeterminate` as its own pseudo-class, because two cannot describe three and folding mixed into `:checked` makes every rule that meant "the tick is showing" silently wrong; a tick and a dash drawn by the painter rather than by an `Icon`, since a widget is a value and an `Icon` owns native memory; a click target that includes the label; `Space` and deliberately not `Enter`, which belongs to a dialog's default action. Its glyph is the first **part** — `check-indicator` is CSS-selectable and **not** KDL-constructible, a stated exception to the parity invariant rather than an oversight in it, because a part has no existence outside its parent and one `ComputedStyle` cannot carry two backgrounds ([ADR-0065](adr/0065-a-part-is-styleable-and-not-constructible.md)). The value is **controlled** in the sense ADR-0063 settled: a click on a bound checkbox whose handler does nothing moves neither the property nor the tick, and a test asserts exactly that. **And the cascade inherits, which closed a bug and a gap at once.** A checkbox's label rendered black on the dark theme, because `StyleResolver` inherited custom properties and nothing else: the label is a `text` child element no rule names, so it resolved to `ComputedStyle.INITIAL`'s black. `button` had never shown it, because it copies `style.color()` onto its child boxes by hand and bypasses the cascade. `color` and the typography now inherit down the element tree — and `cursor` deliberately does not, because it already inherits through the stack of painted rectangles (ADR-0057), and two mechanisms for one property disagree the first time a box has no element behind it. `WidgetRenderer` resolves styles on the way down and builds boxes on the way up, which is the shape inheritance forces. **§1.4's type scale ships**, and it was the blocker's other half: every typography token is a size, a line height and a weight, and all three inherit. `font-family`, `font-size`, `font-weight` and `line-height` reach `ComputedStyle`; a `Fonts` book caches faces by family+weight and fonts by (face, size), because a widget tree is re-rendered every frame and a heading at 20px would otherwise re-parse Inter sixty times a second. **A weight is a face, not an axis**: Inter ships as a variable file *and* as its SemiBold static instance, because instancing `wght` needs symbols in both HarfBuzz and Blend2D and therefore three new export branches — the machinery that has caught the same local-symbol bug three times — while §1.4 specifies exactly two weights and Principle 3 forbids improvising a third ([ADR-0066](adr/0066-a-weight-is-a-face-and-color-inherits.md)). **`button` is now fully compliant with its §3 row**: `body-strong` was the last of the four things `controls.css` said it could not express. The theme tokens were also wrong and are now §1.4's exactly — `heading` was 16 where the table says 15, `body` was 14 where it says 13, there were no line-height tokens at all, and `docs/ARCHITECTURE.md` §10.1 carried a *different* table with a `label` token at weight 500 that no shipped face can draw; §1.4 won and §10.1 records that it did. **And the controls move.** §1.7's motion language ships: a frame `Clock`, the three duration tokens, the two easing keywords with a bezier solver that cannot overshoot, and CSS `transition` resolved by the cascade like any other property. Animated values live in a **per-node overlay applied at paint and never written back into computed style** — the sentence the whole design hangs off, because a cascade that saw the halfway colour as the node's real one would diff *that* against the target and start again from it, giving a control that approaches its hover colour and never arrives. Retargeting starts from the current animated value, so a pointer leaving a button halfway through a fade returns from where the colour is rather than jumping. The whitelist is a **closed enum** — `opacity`, `background-color`, `border-color`, `color` — and `transition: width 200ms` is a *dropped declaration with a warning naming it* rather than a rule that silently never fires, because animating a width would run Yoga every frame of every transition. Colours interpolate in **OKLCH**, which is measurable rather than decorative: Nord's danger red and success green have a channel spread of 54 at their sRGB midpoint and 109 at their OKLCH one. §1.7's "press applies in 0ms, release fades out" needed no new mechanism — the timing that applies is the one on the style being moved *to*, so a zero duration on `:active` and a fade on the resting rule is the whole of it. **The frame loop stays idle**: `renderer.isAnimating()` is what an application asks another frame on, so a window at rest costs nothing and nothing polls. And the virtual clock is what makes any of it testable — `button-hover-midway.png` is three buttons showing the start, the middle and the end of one transition in a single frame, which is a picture no wall clock can take ([ADR-0067](adr/0067-motion-is-an-overlay-on-a-frame-clock.md)). **And the first composite ships, which closes §7.2.** `radio` and `radio-group` are the third and fourth controls, and the first widget that is a *set* rather than a control — so three things that were trivially true for `button` and `checkbox` stop being true. **Traversal:** a group of six options is **one Tab stop** with the arrow keys roving inside it, which is what `docs/design-system.md` §7.2 asks for and what nothing could express, since `moveFocus` collected every focusable node in document order and a radio is one. `Handles.focusScope()` is the whole opt-in, and both halves are the **router's** by the argument already written on Tab: which node an arrow reaches is a property of the group's shape, and the radio the focus is on cannot see its siblings. Arrows are handled after the focused chain declines the key, so a slider stepping its value keeps its own. Both axes rove, because the group's direction is the stylesheet's and input cannot know which pair the user is looking at. **Where Tab re-enters is derived from `:checked`, not remembered** — the decision the record is worth writing for. The obvious implementation is a stored roving position, and it is wrong in a way that only shows later: it is a second piece of state beside the selection, and the two disagree the first time an application sets the value itself, returning the user to the option they last *looked at* rather than the one that is *on*. No event would fix it, because a property being set does not know a router exists. Derived, **the selection is the roving position**; there is nothing to invalidate, nothing to leak when an element unmounts, and one test — focus leaves, the model changes underneath, Tab comes back to the new selection — that the stored version fails. **The invariant:** "exactly one is on" is a fact about the set, so the group applies it on every build and `selected` is deliberately not a KDL attribute, since a document that could mark one option could mark two. A value no option carries selects nothing rather than guessing the first. **And selection follows focus through the application**, not inside the widget: an arrow raises the change and does not move the tick, so a group whose handler does nothing moves the ring and stays put — ADR-0063 applied to a composite. The `fromKeyboard` half of the new `onFocusChanged` is load-bearing rather than decoration: a mouse focus deliberately does not select, or a press moving focus and the click that follows would each fire the change. `Actions` gains a **valued** binding, the first action told which one — `Consumer<String>` over the `value` the document already wrote, with a plain `Runnable` still resolving against it and a valued action *refused* for a `press=` rather than called with an invented argument. `radio-indicator` is the **second part**, which is where ADR-0065 asked that its argument be made again rather than assumed; it holds, and the circle needed no new drawing code — `border-radius: 8px` on a 16px box is one, through the four cubics ADR-0064 already ships, so no native symbol was added and `Box.Mark.DOT` finally has a caller. Five golden images across both themes, and one of them is what caught that options were stretching to the group's full width: a column's flex children stretch on the cross axis, so the focus ring and the click target ran out across empty space while `.inline` kept hugging its label — the same widget with two hit targets depending on a class, which no value assertion would have shown ([ADR-0073](adr/0073-a-composite-is-one-tab-stop.md)). **And `radio` is finished against both specification documents, not just built.** Reading §1.3, §1.5, §2.1, §2.2 and §3.1 against what had shipped turned up five divergences, four of which `checkbox` shared — §3 gives the two controls **one metrics row**, so a rule true of one and not the other is a spec that has stopped being true. Both now carry §1.5's small-control `border-radius: 4px`, which §2.2's ring follows rather than drawing a square one beside `button`'s 8px; both change a **surface** on hover rather than only a border (§2.1's "one surface step"); the group's gap is §1.3's 8 for related controls rather than the 4 it shipped with, and `.inline` takes 16 because side by side each glyph-plus-label is a unit and at 8 a label sits as close to the next option's glyph as to its own. The fourth was a **bug**: `:active` was set on the single deepest element a press landed on, so no control had a working pressed state at all — see below. The fifth was §3.1's check/dot **scale**, the last unimplemented row in that table, which needed the mark to stop being a mark. Two more golden images, one of them a frame 80 ms into a moving selection. **And `--gb-density` ships, at four controls rather than at thirteen** — the cost is per control, so it is three edits now and ten later. Every control sizes itself from `--gb-control-height`; `density-compact.css` is a three-token `:root` block in the theme layer, because that layer is defined by what it holds rather than by what it is called and a fifth would differ from the fourth in name alone. `Density.REGULAR` ships **no stylesheet**: a default is the absence of an override, and a `density-regular.css` restating 32 would be one number in two files. Padding, gap and radius stay literal and are asserted to, because §1.3's density row names heights and nothing else. Compact is **below §1.3's own 32×32 hit-target floor** and that is the trade rather than an oversight — bounded by the glyph staying 16px, so it costs margin around the target and not a smaller target ([ADR-0074](adr/0074-density-is-a-token-swap-and-regular-is-no-stylesheet.md)). **And `toggle` ships, which is the first widget with a *gesture*.** Everything before it responded to a click, a key or a focus change — all single events. A drag is a sequence, and a widget is a value rebuilt every frame with nowhere to keep one, so the `Toggle` that sees the release is a different object from the one that saw the press. **The router reports the origin**, as `PointerEvent.dragX()`, by the argument already written on Tab and on arrow keys — the router owns what the widget cannot see — and because the interval a drag offset is defined over is exactly the implicit capture ADR-0058 already spans. It is **`NaN` and not zero** with no button held, because zero is a real answer (a press that did not move) and `Math.abs(NaN) >= 8` is `false`, so an event with no gesture reads as "not a drag" through the arithmetic rather than through a guard. The rule is one comparison against half of §3's travel: past 8px the value is the **direction dragged**, under it the value flips — so dragging right on a switch already on asks for **on**, which is what a naive "toggle on release" gets wrong. It is also the only control that acts on a release rather than a click, because a switch has no cancel gesture: dragging off it *is* the interaction. `toggle-track` and `toggle-thumb` are the fifth and sixth parts, the thumb by ADR-0073's argument that the unit of independent movement is a node — a `transform` applies down its subtree, so a thumb drawn onto the track would slide the track with it. Where it travels to is the stylesheet's, not Java's. **And the colour question took two wrong answers before the right one, both of which looked like a geometry bug**: the thumb appeared to be breaking out of the pill, and measured off the image it never was — it is exactly concentric and 2px inside all the way round. What the eye read was the thumb merging with the window *across* those 2px. `nord0` was identical to `--gb-bg`; `nord3` was merely near it. Every dark value in Nord is near `--gb-bg`, so on a **light** accent pill there is no dark thumb that works, and the fix is not a thumb colour at all: the dark theme's on pill is **`nord10` rather than `--gb-accent`**, and the thumb is the same near-white in both states. That is the one place a control departs from the shared accent ramp, and the geometry earns it — a checkbox can use a light accent because nothing sits inside its fill, and a toggle cannot because something does. **All of it was caught by looking at a golden image and none of it by a test**, which is now three occasions; a colour that equals another colour is a passing assertion, and a disc that is provably inside its container can still look like it is not ([ADR-0075](adr/0075-a-gestures-origin-is-the-routers.md)). **And every metric in §3 is now actually fixed.** Reported as "the knob is outside the pill when I resize the window", and it was not a toggle bug: Yoga runs with CSS's defaults, so **every node had `flex-shrink: 1`** and a `width: 36px` was a *preferred* width a cramped row could take back. §8 lists `flex-grow/shrink/basis` and only grow was implemented, so there was no way to say otherwise. Measured at 40px of room: a switch's pill 36 → **16** while its 16px thumb did not move, a checkbox's glyph 16 → **10**, a radio's the same and drawn as an *ellipse* since `border-radius` follows the box, and in a short column a control's hit target 32 → **13** — §1.3's 32×32 floor gone. The reported symptom was the only one of the four visible at a glance. `flex-shrink` is implemented now with **no native symbol and no new binding** — `YGNodeStyleSetFlexShrink` was already exported and bound, so the gap was in the CSS engine alone — and the controls declare `flex-shrink: 0` once over a type list, because the rule is "a control's metrics are fixed" and a copy per control is how that stops being true. **The label deliberately still shrinks**: text is the one thing in a control that should give, and a `text` that refused would push the glyph out of the window rather than ellipsing. Six golden scenes were **sized by the bug** — 300×132 for content needing 136, which fitted only because the options were being squashed. The test frames are deliberately absurd, because a regression here is a function of window size and a test at a plausible size is the one that cannot fail ([ADR-0076](adr/0076-a-glyph-does-not-negotiate.md)). **And `slider` ships, with `fader` as its vertical class** — the sixth control, and the first whose value is a **number rather than a state**. Every control before it has a value a stylesheet can name; `toggle-track:checked toggle-thumb { transform: translate(16px) }` is literally how a switch's thumb moves, and that stops working the moment the value is 37.4. So the thumb is placed by **flex ratio** — fill, thumb, rest, with the grow factors carrying the value — and `transform` is not merely awkward here but *unable*: CSS percentages inside `translate` are a proportion of the moving box, so `translate(50%)` moves the thumb by half a thumb rather than to the middle of the track. The ratio yields the **filled portion for free**, as a box the cascade can reach. The second half is `PointerEvent.local()`, the direct sibling of ADR-0075's `dragX()`: where an event landed **inside the widget currently handling it**, re-pointed per handler because dispatch bubbles — a press on the thumb targets the thumb while the slider wants the position along *itself*. The control **snaps and clamps so no application has to**, and each of the three rules is a choice: steps count from `min` (so a 1..10 slider stepping by 2 can reach 1), an arrow offers the next *reachable* value rather than the current plus a step (nothing snaps a value on the way in, because that would be the control overruling the model), and the **ends are always reachable** even when the range is not a whole number of steps. It is also the first control that relies on ADR-0073 putting scope traversal after the focused chain — and it consumes an arrow even when the value did not move, because a slider at its maximum still owns `Right` and letting it through would move focus off the control being adjusted ([ADR-0079](adr/0079-a-continuous-value-is-placed-by-ratio.md)). **And `slider` is finished against §3 rather than merely shipped.** Its three optional halves — "optional tick marks and value label", and `fader`'s "optional dB scale mapping" — look like three small additions and are three different things breaking. **A value label makes "the control is the track" false**: `[ track ──── ] 40` is one control and two boxes, and the value lives along the shorter one, so a pointer mapped along the control reads 88% at the far end of the track — drawn perfectly, reported nowhere. `Handles.localPart()` is the answer, naming a **CSS type** because that is the vocabulary a part already has (ADR-0065) and resolved by the **router** because a widget cannot see its own elements — `dragX()`'s argument for the third time. The fallback is on the *rectangle* and not the element, which is the case that actually happens: a part exists from the first build and has no region until the first paint, so the element-level check finds it and hands back a zero-sized box whose every fraction is 0 — for a slider, "the user asked for the minimum". **The anatomy was renamed rather than extended**: `slider-track` is now the full-height box the value is measured along and the 4px channel is `slider-groove`, because two boxes were doing one job under one name until a third thing joined the control. **Every existing golden is byte-identical**, which is what says that was a refactor. **The marks needed two things that rule each other out** — clear the thumb, and do not move the groove (a scale that pushed it up would put two sliders in one settings list at different heights for no visible reason) — so `slider-ticks` is `height: 0` and each mark is moved clear by a `transform`, which costs no layout (ADR-0068). Each mark sits in a synthesized **0×0 cell** it overflows out of, because a mark's own 2px has no business being in the spacing arithmetic: spread five 2px marks directly and every centre is a pixel off the thumb centre it names. The cell is zero on **both** axes so a fader can flip the row to a column in the stylesheet alone. Marks are counted along the **travel**, not per `step` (twenty-one marks on a 0–100 slider stepping by 5 is a wall) and not at even values (on a decibel travel that is four marks huddled at the top). **`format` is a pattern and not a function**, because §11 compares two records for equality and two lambdas are never equal; it is validated at construction, so `%d` against a double fails at inflation rather than out of a paint, and formatted in `Locale.ROOT`, because the default would draw `0,5` on a `de_DE` machine and the golden that failed would be unreproducible anywhere else. **`Scale` is a sealed interface of records** — `Linear` and `Decibels(floorDb)` — for the same parity reason, and it places a linear gain at a position linear in dB: half gain is 6 dB down, which is 90% of the way up a fader and half way up a linear slider, and *that* is the feature. The bottom of the travel is silence exactly, a 0.001-of-full-scale discontinuity at one end, because the thing a fader must be able to do is go silent. `SliderGeometryTest` is a new kind of test here and the change is what needed it: the claims rest on **geometric relations between two parts** that no stylesheet states and no value assertion reaches, and two of its six assertions failed on the first run — Yoga adds padding to a box with an explicit `height: 0`, and a slider in a *row* collapses to its content width, so the test's own scene was wrong ([ADR-0080](adr/0080-a-value-is-measured-along-a-part.md)). **And `progress` and `spinner` ship, which are the first two widgets whose motion is not a transition.** Everything that has moved so far moved between two styles the cascade resolved; §3.1 asks these two for a "sweep loop 1.2s linear" and a "rotation 900ms linear loop", and §8's subset has no `@keyframes` and is not going to grow one. §1.7 names `AnimationController` for exactly this and **it was not built**: a loop that never ends is `(now % period) / period`, with nothing to start, stop, dispose or leak. That is ADR-0073's argument for the third time — a second copy of a fact the tree already holds disagrees with it — and here the stored version has a symptom the derived one cannot have: two spinners mounted a frame apart would turn at the same speed and never at the same angle, which looks wrong without looking broken. The controller's real subjects have a **lifecycle** — toast reflow, and the `opening → open → closing → removed` sequence every overlay runs — and none of those widgets exist, so it is M3's to build for M3's problem. Two small seams: `Paints.Context.nowMillis()`, read **once** per frame so two spinners see one number, and `Paints.isAnimating()`, because §1.7's idle loop would otherwise paint a spinner once and go to sleep in front of it — a property of the *description*, so a bar given a value stops asking. The sweep is a `transform` (animating a width would run Yoga every frame of a loop that never ends) and it **turns at the ends rather than running off them**, because the usual drawing needs `overflow: hidden` and nothing here clips a box: a bar that ran past its track would draw over its neighbours, and the wrap clipping exists to hide would be a visible jump once a loop. The spinner's ring is a `Box.Mark` and its arc is **three cubics through the already-exported `bl_path_cubic_to`** — no symbol added to the export list, ADR-0064's rule holding for the fifth time — and it is three quarters of a circle because a spinning circle is a circle ([ADR-0081](adr/0081-a-perpetual-loop-has-no-state.md)). **And `badge` ships, which is the first entry in §3's table that is not a control** — no focus, no value, no keyboard map, no states — and the first widget the design system *lets use colour*. §1.2 admits the aurora hues "only with semantic meaning", so every widget so far has obeyed the never half; a status chip is the first one whose whole job is the only half. Which walks it straight into the other half of §1.2: **every text/surface pair meets WCAG 4.5:1, validated in CI against both themes** — a sentence that had nothing behind it, because no contrast check existed anywhere in the repository. It does now, and it found something on the first run. A filled chip cannot take `--gb-text`: white on `--gb-warning` is **1.35:1**, on `--gb-success` 1.77 and on `--gb-info` 2.34, so three of the four hues need the *opposite* end of the palette from the one the dark theme is built on — `--nord0` text on a theme whose every other text token is `--nord6`. **The foreground is a property of the fill and not of the theme**, and because §1.2's palette is theme-invariant the pairing is identical in both files. `--gb-danger` needs something that is not in the palette at all: it is 3.55:1 under `--nord6` and 3.05 under `--nord0`, legible against neither, so the badge's fill is `--nord11` **derived** darker until it clears the floor — the one place a chip's colour is not a palette entry, and the reason [ADR-0087](adr/0087-a-semantic-fill-brings-its-own-foreground.md) exists. §3's table gained a `badge` row before a single number reached `controls.css` (Principle 3), and every one of them is derived rather than picked: 20 is on §1.3's ramp and is the height `toggle-track` already uses, so `border-radius: 10px` is §1.5's `full` spelled the way that part already spells it. `ContrastTest` resolves every pair through the **real cascade** rather than parsing the CSS, so a rule that stops matching and a token that stops resolving both fail it. **And `knob` ships, which is the tenth control and the first whose drag is a *rate*.** It looked like a slider bent into a circle -- same `min`/`max`/`step`, same keyboard map, same `bind` and `change` -- and almost none of the machinery transferred. **A slider's value is a position**: the pointer is somewhere along a track, the fraction it sits at *is* the answer, read fresh on every event with no history at all, which is why nothing keeps state and why the router only ever had to report *where* a gesture started (ADR-0079). **A knob has no track.** §3 gives it "value drag 200px per full range", so the value is *where it started plus how far you have dragged* -- and "where it started" is exactly what nothing could answer, because a widget is an immutable value rebuilt from the model and by the second frame of the drag the value at the press has been overwritten by the value the drag itself asked for. So the router remembers a **third** thing about a gesture and it is not a point: `Handles.gestureAnchor()` is asked once on the press, deepest-first along the chain so a press on a *part* is anchored by the control that will handle it, and handed back on every event as `PointerEvent.anchor()`. `NaN` outside a gesture, which is `dragX()`'s convention and load-bearing -- a widget reading "no gesture" as an anchor of zero would snap a knob to its minimum on every hover. That is ADR-0075's argument one step further, and it is general: a splitter, a scrollbar thumb and a text-selection drag all want it, so `GestureAnchorTest` is written against a bare widget in `:core`. **The fine modifier is the gesture's, not the event's**, and the reason is a bug that would never have looked like one: reading the live modifier rescales travel already covered, so pressing Shift 100px into a drag takes the value from half a range below where it started to a twentieth of one *without the pointer moving* -- drawn perfectly, reported nowhere, and it reads as the knob slipping. **`SDL_GetModState` joins the export list**, the first new symbol since ADR-0086, because pointer events carried no modifiers anywhere -- not in `PointerEvent`, not in the SPI, not from SDL, whose mouse events have no `mod` field where its keyboard events do. Latching them from the last key event needs no symbol and is wrong in a way that lasts: a window that loses focus while Shift is held never sees the release and sits silently in fine mode. **`Box.Mark` gained `start` and `sweep`**, making `ARC` the one mark whose geometry is not fixed by its kind -- because it is the one that has to show a number -- and **no native symbol was added for the drawing**, because `Arc.addTo` was already general and already fed by ADR-0064's cubics; the rule holds for the sixth time. **Detents are magnetic and `step` is a grid**, which is why both exist: a knob with a centre detent is not a knob with a coarse step. **And the first drawing was wrong in a way only the golden could say.** The dial was `knob`'s own `background` and both rings were stroked on the same box, so the track ran *across* the body at about 1.2:1 and the 270° of travel a user is meant to read was invisible -- every value assertion passed. `KnobDial` exists because of it, and the knob went into `controls-on-surface-*` rather than being exempted from it. A second one the goldens did not catch and a test did: a gentle touchpad scroll did **nothing** on a stepped knob, because a touchpad reports fractions of a line, a stepped knob snaps everything it reports, and every wheel event computes from the current value rather than accumulating -- so a third of a step rounded straight back, every time. A stepped knob now moves at least one step for any scroll at all ([ADR-0089](adr/0089-a-knobs-gesture-is-a-rate.md)). **Then it was put in front of someone and two things were wrong that no assertion could have said.** It read as a *gauge*: §3 asks for an "arc indicator" and between them the two documents say what the value is and never say which way the thing is **pointing**, so nothing on the dial turned and nothing about it suggested you could turn it. And the ring did nothing — a slider's track is clickable, a knob's ring is the same 270° of travel drawn round a circle, and it was inert. So `Box.Mark` gained a `POINTER` kind, a radial line at the value's angle, drawn as a **mark on** `knob-dial` rather than as a part of its own — the first time that has been the right answer since `CheckMark` went the other way, because a part is a node when two things must be styled or *moved* apart and the pointer is neither. **Clicking the ring positions the value; clicking the dial grabs it.** The boundary between them is not a constant: the control cannot know where the dial ends, because the inset is the stylesheet's, so `knob` names `knob-dial` as its `localPart()` and "outside the dial" is derived from the geometry that was actually painted (ADR-0080 answering a question it was not written for). The jump fires on **`CLICKED` and not `PRESSED`**, which is the whole of what makes it compose with the drag: a press is the first event of both gestures and cannot know which one it is, the router synthesizes a click only when press and release landed on the same node, and the rest is `Toggle`'s 8px slop. Jumping on the press would also have fought the anchor, which the router reads *before* dispatching — a drag after a jump would continue from the value the jump replaced ([ADR-0090](adr/0090-a-ring-is-a-track-and-a-dial-is-a-grab.md)). **And `segmented` ships, which closes §3's catalog for everything that does not wait on a popup — and it is the first control whose *specification* could not be built as written.** It was billed as the cheap one: §3 says outright that it shares `radio-group`'s model and invariant exactly and "is `radio-group` with a different drawing", and the model transferred without a line of thought. The drawing did not. §3's row asks for "radius 8 outer, 0 between; 1px divider", which is the joined-buttons look — and a **per-corner** radius, where ARCHITECTURE §8 resolves "one radius, not per-side" on purpose. There is no clipping either, so the usual escape of square fills inside a rounded clipping parent is not there: a square-cornered fill inside the bar paints *over* its curve, and the selected end of the control reads as a corner that lost its radius. So **the bar carries the 8 and the segment is inset inside it at §1.5's 4**, with both numbers derived rather than picked — the 2px inset is what fits a 28-high segment in a 32-high bar, the same arithmetic `toggle`'s padding comes from — and the divider goes with the joined drawing it belonged to, because segments inset on every side are already separated and a rule drawing one would draw it through the gap the inset made. **§3.1's row could not be built either, and for a better reason.** It asks for a "selection indicator `translate`+width between segments": `width` is not on §1.7's whitelist and never will be, and the `translate` would have to name the distance from the segment being left to the one being arrived at — a fact about two boxes' laid-out geometry. A stylesheet cannot write it, because segments are as wide as their labels; and a widget cannot compute it, because ADR-0080 already established where geometry *is* available, which is the router after a paint. So **the fill is the indicator**, on `fast`, which is what `list` selection already does — and the travelling version waits for `tabs`, whose row §3.1 says it borrows the effect from, and which will need a widget to be told where its own children landed last frame. That is a real feature with real costs and it belongs to the control that actually requires it (ADR-0081's argument, for the second time). Both `design-system.md` rows were **amended** rather than left describing something that does not exist. What is new in Java is one line: `focusScope()` is `HORIZONTAL` where `radio-group`'s is `BOTH`, and that single difference is the whole of why these are two widgets rather than `radio-group.segmented` — a group has no axis because its direction is its stylesheet's, a bar has one because it is a row and no class turns it into a column, so `Up`/`Down` are not its keys to take. ADR-0078 wrote that rule for menus and this is the first control outside one to use it. A segment is `option` — the node §3 writes for this control *and* for `select` — and it is a widget rather than a part: a document writes it, it takes the focus, and it means something on its own. Two smaller things fell out. `option:checked:hover` is the first **two pseudo-classes on one compound** anywhere in the repository; the selector engine always supported it and nothing had needed it, and here it is what keeps a selected segment selected-coloured under the pointer — `checkbox` and `toggle` spend a descendant selector on the identical problem because their fill is on a part. And `flex-grow: 1` on a segment is the same question `radio-group` answered with `align-items: flex-start`, answered the other way: a group's options are separate controls that happen to be listed together, while **a bar is one object and its segments divide it** ([ADR-0097](adr/0097-a-selection-that-travels-needs-a-geometry.md)). **And two things landed that M2's ladder does not name.** The first is that **an annotated member may be private again.** ADR-0096 listed "annotated members cannot be `private`" as a cost and argued the fields belonged package-private anyway; the argument runs the wrong way round, because the toolkit was deciding a model's encapsulation as a side effect of how it reads it, and an `@Action` only the markup calls has no business being part of a model's API. A private member now gets a `VarHandle` or a `MethodHandle`, looked up **once** in the generated class's static initializer through `privateLookupIn` — which needs no `opens` and no `setAccessible`, because the generated class is in the target's own package and a module always opens its packages to itself. This is **not** the `MethodHandles.Lookup` alternative ADR-0096 rejected: that one resolved a name at run time, and this writes the descriptor the processor already verified, so a typo is still a compile error naming the field and the handle is *access* rather than discovery. An accessible member still gets nothing — a handle it does not need is a line of generated code a reader has to understand for nothing — so a mixed model gets a mixed file, which is honest. The processor's test suite now **runs** its output rather than only compiling it, because "it compiles" stopped being the interesting half of the claim, and `ShowcaseModel`'s six properties and five markup-only handlers are private ([ADR-0098](adr/0098-a-private-member-is-reached-by-a-handle.md)). **Then the indicator was made to travel, which ADR-0097 had deferred and was wrong to.** That record argued a `translate` "would have to name the distance from the segment being left to the one being arrived at — a fact about two boxes' laid-out geometry", and every clause of it is true except the premise buried in the middle: *segments are as wide as their labels*. That was a **choice**, not a fact. Make every segment exactly 1/n of the bar and the distance to segment *k* is *k* times one segment — a proportion, not a length, and a percentage in a `transform` is resolved by the painter after Yoga has run (ADR-0068). Nothing has to measure anything. What *was* missing turned out to be somewhere else entirely: a value a widget computes in `render` arrives **after** the frame has observed the node's style and started its transitions, which is why every Java-computed geometry in the toolkit — a knob's arc, a slider's fill ratio — is documented as not animating. So `Styled.restyle` exists: **§8's `inline` cascade layer, typed**, applied after the cascade, after the style cache, and *before* the animation looks. Those three orderings are the whole mechanism, and each is a way it could have been wrong — frozen, snapping, or invisible to the subtree that inherits it. The anatomy underneath is `segmented → segmented-track → [segmented-indicator, option…]`, and **the track exists because two percentage bases disagree**: Yoga resolves an in-flow child's percentage width against its parent's *content* box and an absolute child's against its *padding* box, so a pill sized against a bar with 2px of padding is 4px too wide and drifts a little further with every cell. A track with no padding makes them one box, which is `slider` growing a track for the same kind of reason (ADR-0080). Three things fell out of it. **`position` and `inset` reached the cascade** — §8 has listed them and `YogaNode` has bound them since the beginning, and nothing had needed a box that sits *over* its siblings rather than beside them. **`flex-basis` was implemented and taken back out**: `flex-basis: 0` gives equal cells and makes Yoga compute the track's content size as *zero*, so an unconstrained bar collapses to its padding — explicit percentages are the form that works in both directions, and a property with no consumer had no business staying. And **a segment's hover became a wash**: an opaque fill would paint over the pill, because segments are drawn after it, and clicking a new segment would paint the destination fill instantly and beat the animation to it — so the `--gb-overlay-*` tokens `button.ghost` uses do both states on both backgrounds, which took four tokens out of each theme. The cost is stated in §3's row rather than discovered: **a segmented control now has no width of its own** and fills its parent when nothing gives it one, because its cells are proportions ([ADR-0099](adr/0099-an-indicator-travels-on-a-grid.md)). Still to come: `select` and custom image cursors |
| M3 — Shell | **started** | **The in-window overlay layer ships, and `hud` is its first occupant.** Every window's element tree is rooted at a `window-root` whose children are the application's root — in flow, growing to fill the window — and whatever is floating over it, each an absolute box pinned to a `Corner` with two of its four insets *undefined* rather than zero, which is the difference between a plate in a corner and a scrim across the window. Three things follow from that one shape and each is the point: an overlay takes no space from the content, it is painted after it because a box tree has no z-order beyond document order, and adding one **cannot re-parent the application** — the root node is there from the first frame whether or not anything is floating, because a layer that appeared with the first toast would throw away every element's state to show it. The list is a `Property` the launcher owns and the root *watches* through the `binding()` every widget already has (§9's `bind`, pointed at the toolkit's own state), since an element tree's root widget cannot be swapped. `host.overlay(new Hud(), Corner.BOTTOM_END)` is the whole API and the handle it returns is the way out ([ADR-0100](adr/0100-a-window-has-a-layer-above-its-application.md)). **`hud` is §7's first widget** and the first in the catalog that is about the toolkit rather than the application: `60 fps` and `paint 2.1 ms`, read off a 60-frame ring `Window.paint` now writes unconditionally — two `nanoTime` calls a frame, where before every timing was behind `LOG.isTraceEnabled()` and watching a rate meant measuring a loop that was also writing a line per frame. The numbers travel down `Paints.Context` beside the frame clock, which is what lets a bare `hud` node in a document show live figures *and* lets a golden image show figures somebody chose. **It never asks for a frame**: a rate display that requested one would report the frames it had itself caused, and would falsify §1.7's "the frame loop is fully idle when no animation is active" for every window with one in the corner — so it reports the frames that were already happening, freezes with an idle loop, and draws dashes rather than zeroes when there is no loop at all, because a zero is a measurement ([ADR-0101](adr/0101-a-diagnostic-must-not-be-the-thing-it-measures.md)). The showcase toggles one on `Ctrl+F`, off by default, which is also what keeps a machine-dependent number out of §14's image corpus. **And the backend popup window is built** — the other half of §7's "in the in-window overlay layer *or* backend popup windows as appropriate". `Backend.createPopup(owner, spec)` opens a real platform window parented to another and positioned in **its** coordinates, which is the one thing an in-window overlay cannot do and exactly what a dropdown taller than the space below its button needs. A popup **is** a window — it acquires a frame, presents, paces and closes by the same code, and its events arrive through the same pump under their own id — so `Sdl3Window` became `sealed … permits Sdl3Popup` rather than growing a boolean, and popups are in `windows()` because shutdown enumerates windows. **It returns an `Optional` and empty is a normal answer**: popup support belongs to the video driver, not to the request. All four desktop drivers declare it — cocoa included, which was worth checking — and SDL's `dummy`, which every headless test here runs under, does not; so the refusal is a branch CI runs on every platform and the fallback is the in-window layer, clipped to the window. Two things the tests found rather than assumed. `SDL_WINDOW_TOOLTIP` alone does **not** stop a popup taking focus — `NOT_FOCUSABLE` is a separate flag, and §7's "shows on keyboard focus, never focusable itself" is false without it; and `0x80000000` turned out to be the first constant in the toolkit with the top bit set, which the layout probe read into a signed `int` and refused, so a constant row's value is now read unsigned (a *size* that is negative still means the table is being read wrongly). And **a resize is a request**: on X11 the window manager grants it when it likes, `size()` honestly reports the old one until then, and `HeadlessPopup` defers its resize the same way so that the fake is not the one place a caller who measures too early passes ([ADR-0102](adr/0102-a-popup-is-a-window-the-platform-may-refuse.md)). **Still not started:** tray, dialogs, scroll, forms, CSD, charts — and the widget layer over the popup SPI, which is what `menu`, `tooltip`, `popover` and M2's leftover `select` are now waiting on: painting a widget subtree into a second window's frame, routing input to it, and light-dismiss |
| M4 — GPU | not started | `canvas3d`, GPU composition |
| M5 — Hardening | not started | Text editing depth, AccessKit bridge, IME preedit, docs, 0.1 release |

## Module layout

| Module | Artifact | Contents |
|---|---|---|
| `:natives` | `goldberry-natives-{platform}-{arch}` | Hand-written FFM bindings, owning wrappers, and the CMake superbuild that produces `libgoldberry` |
| `:core` | `goldberry-core` | The engines and the contracts — the widget/element/render trees, style, layout, text, icons, paint, the backend SPI, and the two backends `headless` and `sdl3` ([ADR-0041](adr/0041-three-platforms-four-artifacts-two-backends.md)). **No widgets**: `text`, `row`, `column`, `panel` and `spacer` lived here until they had a catalog to belong to ([ADR-0092](adr/0092-a-primitive-is-a-widget-like-any-other.md)) |
| `:widgets` | `goldberry-widgets` | The widget catalog — controls, containers, menus, charts — plus the showcase screens that serve as the visual regression corpus. **One module, a package per control** — `docs/core-widgets.md`'s groups (`…widgets.controls` and `…widgets.overlay`, with `form`/`panel`/`nav`/`collection` as they are built) and one package inside each for every widget and its parts. Half a reversal of ADR-0014, and the second level is what makes ADR-0065's rule a boundary the compiler enforces rather than a convention: a `slider-thumb` is now invisible outside `…controls.slider`, where before "package-private" meant "visible to the whole catalog" ([ADR-0091](adr/0091-one-module-a-package-per-control.md)) |
| `:processor` | *not published* | The registry generator: turns `@Bind`/`@Action` into the explicit `Bindings`/`Actions` calls §9 requires, at compile time. Build-time only, like `:assets` — it runs inside javac, never reaches a runtime classpath and has no `module-info` ([ADR-0096](adr/0096-a-registry-is-generated-not-reflected.md)) |
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

- **Nothing paints into a popup yet.** `createPopup` opens one and it presents
  frames like any window, but the launcher owns *one* window, one element tree and
  one render tree ([ADR-0093](adr/0093-an-application-is-a-root-widget.md)) — and a
  menu needs a second render tree over a **subtree** of the same element tree, so
  that the cascade, focus and state a popup's contents rely on are the ones they
  already have. That, input routing to a second window, and light-dismiss are the
  three pieces between here and `select`. —
  [ADR-0102](adr/0102-a-popup-is-a-window-the-platform-may-refuse.md)
- **Nothing hit-tests an overlay by rule.** The pointer router tests against the
  painted frame ([ADR-0054](adr/0054-hit-testing-runs-against-the-painted-frame.md))
  and an overlay is in that frame, so a button inside one is reachable today by
  the accident of paint order rather than by anything anyone wrote down. A modal
  `dialog` needs the rule stated — the topmost overlay takes the pointer first,
  and a modal one takes it exclusively — and that belongs with the widget that
  needs it rather than with the layer. —
  [ADR-0100](adr/0100-a-window-has-a-layer-above-its-application.md)
- **An overlay cannot be raised from inside the tree.** `Host.overlay` is the
  application's door, which is the whole API a `hud` needs and half of what a
  `toast` will: the thing raising a toast is usually a handler deep in the tree,
  and what it wants is Flutter's `Overlay.of(context)`. Not built, deliberately —
  there is one consumer and an interface designed against one caller is designed
  twice ([ADR-0019](adr/0019-the-backend-spis-first-cut.md)) — and it would be
  implemented in terms of what is here rather than instead of it. —
  [ADR-0100](adr/0100-a-window-has-a-layer-above-its-application.md)
- **Overlays do not animate in or out, and `stack` is still owed.** §1.7's overlay
  curve wants a toast to arrive rather than appear, which is a transition on the
  widget and not on the layer. And `docs/core-widgets.md` §1's `stack` — "z-order
  layering; children positioned by alignment or absolute insets" — is unbuilt; it
  is the *layout* widget where this is a *window* facility, and neither builds the
  other. —
  [ADR-0100](adr/0100-a-window-has-a-layer-above-its-application.md)
- **Nothing reports a dropped frame.** The ring behind `hud` records frames that
  were painted, so a frame the platform refused *after* it was painted is in the
  mean and a frame the loop never reached is not. "3 late" needs the pacer's view
  as well as the painter's, and the pacer belongs to the `sdl3` backend. —
  [ADR-0101](adr/0101-a-diagnostic-must-not-be-the-thing-it-measures.md)
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
- ~~**Seven shipped `button` colour pairs are below §1.2's 4.5:1 floor.**~~
  **Fixed, and the worst of them was a rule applied where it does not hold.**
  §1.2 had always said "every text/surface pair meets **WCAG 4.5:1** […]
  validated in CI against both themes"; nothing validated anything until `badge`
  forced the question, and the first run of `ContrastTest` found
  `--gb-button-danger-text` on `--gb-button-danger-bg` at **3.55:1** — `--nord6`
  on `--nord11`, unchanged since the first control shipped. Two things in the
  numbers were the shape of the fix rather than its size. **Every ramp's darkest
  step already passed** (`button.danger:active` is 5.11:1 on light), so nothing
  needed a new colour system — the ramps needed *sliding*, and the value that was
  `:active` is roughly where rest belongs. And **the worst pair was a hover state
  that was worse than the rest state one step from it**: `button.danger:hover` at
  **2.95:1** on dark, below the 3.55 it moved from. The dark theme lightens on
  hover, correctly, for a *surface* moving one step toward the light — and a
  danger button is not a surface, it is a saturated fill carrying `--nord6`, so
  lightening moved it **toward its own text**. Stated as a rule it already
  described three of the four filled variants: **a fill that carries text moves
  away from it**. So `button.danger` on dark now darkens on hover, against that
  theme's usual direction and alone in the toolkit in doing so. `--gb-danger-fill`
  and `--gb-accent-fill` replace the aliases to `--nord11` and `--nord10`, and the
  danger ramp is now **identical on both themes**, because the hue is and the text
  on it is. The one piece of collateral was worth catching: `--gb-checkbox-bg-checked-hover`
  and its radio and toggle counterparts **aliased the button's ramp**, on the
  argument that a checked control and a primary button share the accent — true
  until a button's fill started being chosen for its *label*. A checked glyph
  carries a mark, which §1.2 asks 3:1 of, so `--gb-accent-bg-hover`/`-active` are
  split out holding the values the button's ramp used to, and **every checkbox,
  radio, toggle, slider, progress and spinner golden is byte-identical** — two
  button images are the only ones that moved, which is what says the split landed
  where it was aimed. `KNOWN_FAILURES` is now empty and *stays*, asserted equal to
  the measured failures and asserted empty by name: nothing is exempt, and
  re-exempting a pair fails a test that says what happened —
  [ADR-0088](adr/0088-a-fill-that-carries-text-moves-away-from-it.md),
  [ADR-0087](adr/0087-a-semantic-fill-brings-its-own-foreground.md),
  [ADR-0082](adr/0082-a-preflight-check-that-cannot-fail-is-not-a-check.md)
- **A shortcut is built from enums, and `Modifiers` is a mask.** An accelerator
  had one way in — `Shortcut.of("Ctrl+S")` — parsed at run time, so `"Crtl+S"`
  threw whenever the line happened to run. `Modifiers` had the same problem from
  the other side: four positional booleans, 23 call sites writing them out, and
  nothing to catch a wrong order. There is a `Mod` enum now with a real bitmask,
  composed as `Mod.CTRL.and(Mod.SHIFT).and(Key.Z)`. **`Mod.CTRL | Key.A` is not
  reachable**: `|` is defined for the integral types and `boolean` and Java does
  not allow overloading it, and the spelling that *would* compile —
  `Mod.CTRL.bit() | Mod.SHIFT.bit()` into a method taking an `int` — is a mask
  with nothing checking it, where `Key.A.ordinal() | Mod.CTRL.bit()` would compile
  and mean nothing. So the mask is real and private to the arithmetic: `bit()` is
  for the SDL boundary and for tests, and `and` can only ever produce `Modifiers`
  or a `Shortcut`. `Modifiers` is one `int` with `has`/`only`/`set` on top, the
  four boolean accessors kept so no call site changed, and the four-boolean
  constructor demoted to a secondary one — it reads fine where all four are
  literals and is a trap where they are computed —
  [ADR-0095](adr/0095-a-shortcut-is-built-from-enums.md)
- **Every widget is provably a value, and now there is a test that says so.**
  ADR-0004 rests on it and nothing checked it. `ImmutabilityTest` asserts the
  parts records do *not* give for free: that every widget is a record with no
  non-final field, that a container copies the children list it is handed rather
  than keeping the caller's, that the list it hands back cannot be written to,
  that `Attributes` copies its class set, and that every chainable step returns a
  new widget rather than mutating the receiver — so handing one widget to two
  panes and styling one cannot restyle the other. Ten checks, all passing, which
  means the guarantee was already true and is now enforced. The one component
  that is mutable by design is called out rather than papered over: a binding is
  an `Observable` and a handler is a lambda, and what matters is that a widget
  cannot *write* through them (ADR-0063).
- **Registries are generated, not reflected.** Wiring a model to markup was
  fifteen lines of pure copying — one `.bind(path, property)` per property, one
  per handler, plus the `Double.parseDouble` a valued action needs — and the
  failure mode was the worst kind: a property that exists and is never registered
  inflates to a control that renders perfectly and never moves, with nothing
  pointing at it. The obvious fix is a runtime reflective scan, and §9 forbids
  exactly that ("no reflective `#handler` magic") — rightly, since it would need
  the application's package `opens`, cost start-up, and leave the same silent
  control. So `@Bind`, `@Action` and `@Registry` are read by an **annotation
  processor** that writes the calls a person would have written: ordinary Java you
  can open, step into and get a stack trace out of, with nothing on the runtime
  path at all. **The refusals are the point** — a `private` member the generated
  code cannot see (with the fix in the message), a `@Bind` on something that is
  not a `Property`, two members claiming one path, an `@Action` taking more than
  one argument or one the toolkit cannot parse, and an annotated member on a class
  that is not `@Registry`, which is the mistake with no other symptom at all.
  Eight processor tests cover those; the showcase proves the generation itself
  every build. Annotations are `SOURCE`-retained so nothing at run time can be
  tempted to read them —
  [ADR-0096](adr/0096-a-registry-is-generated-not-reflected.md)
- **The showcase is five classes and two documents, and `new` survived a
  challenge.** It was one 770-line class doing four unrelated jobs — the
  application lifecycle, the view model, the widget tree and three panes' layout
  — with every screen a private method on one state object that also held the
  model. It is now `Showcase` (the `Application`: lifecycle, stylesheets,
  registries, accelerators), `ShowcaseModel` (properties, the methods that change
  them, and the two registries markup resolves against), and `ui.Screen` /
  `ui.Panes` / `ui.Content`. **`titlebar.kdl` and `sidebar.kdl` carry everything
  declarative**, which is the first time §9's markup path has run in a *window*
  with all three registries live: `bind=`, `change=`, `press=` and `icon=` all
  resolve against what the model and the application register, and all three are
  strict, so a typo fails at inflation with a line and column. `ui.Content` stays
  in Java and the reason is the instructive one — its Undo and Reset buttons are
  disabled when the click count is zero, and §8's markup has no expressions; a
  document that could evaluate `clicks == 0` would be code in a data file with no
  stack trace. **`ShowcaseDocumentsTest` asserts the shape rather than trusting
  the window**: an empty `sidebar.kdl` inflates to an empty column and paints a
  blank panel, and the headless three-frame run would pass — so it checks that
  every control is there *and* that the bindings reach the model's own
  properties, which a shape assertion misses (a `bind=` resolving to nothing
  still renders a control that never moves).
  **On `Column.of()` against `new Column()`**: `new` stays, and the deciding
  argument is that a public record's canonical constructor **cannot be hidden**
  — the JLS requires it to be at least as accessible as the record — so `of()`
  could only ever be additive, two permanent public doors with no compiler help
  keeping them in step. The noise turned out to be depth rather than the keyword,
  and decomposition fixed it. Performance was **measured** rather than assumed:
  20M allocations, `new` 45.2 ms against `of` 45.1 ms, identical within noise,
  because `-XX:+PrintInlining` shows the factory inlined (`Box::of (10 bytes)
  inline (hot)`) — the first attempt at that benchmark said 87 against 46 and was
  wrong, with a `String.equals` inside the loop. What *did* get named is the
  ambiguous **overload**: `Slider` had two five-argument constructors differing
  only in whether the fourth parameter was a `double` or an `Observable`, and
  `Knob`, `Toggle` and `Progress` had the same shape — now `Slider.of`, `Knob.of`,
  `Toggle.of`, `Progress.of`, following the `of` = bound convention the catalog
  already used —
  [ADR-0094](adr/0094-name-the-overload-not-the-allocation.md)
- **An application is a root widget, and the showcase's `main` is one line.**
  It was 190 lines, and none of them were about the showcase: open a window, open
  a font book, build an element tree, a render tree and a router, hold three
  one-element arrays to remember the renderer and the theme and the density
  across frames, write the paint callback — flush, restyle if the theme moved,
  update, compute damage, choose partial or full, hand the damage back, capture
  the hit-test snapshot, ask for another frame if anything animates — and take it
  all down in an order that matters. Two of those lines are subtly wrong if
  reordered: a render object holds a Yoga measure callback closing over a
  paragraph closing over a font, so closing the fonts first reads unmapped
  memory, and the trailing `Goldberry.shutdown()` is the difference between a
  clean Wayland disconnect and a compositor unwinding a client that never said
  goodbye (ADR-0085). None of it is a decision an application makes differently,
  so all of it is `Goldberry.launch`'s now: an application implements
  `Application` — one required method, `root()` — and gets back a `Host` with
  `repaint`, `restyle`, `title`, `shortcut`, `fonts` and a *named* escape hatch
  to the window. **`restyle()` is separate from `repaint()`** and is the one
  piece of state the launcher keeps for the application: re-reading
  `stylesheets()` every frame would rebuild the renderer every frame, and never
  re-reading it would make a theme switch impossible, so the application says
  when. Alongside it, **every widget is chainable** — `Attributed` gives `id`,
  `styled` and `keyed`, `Bindable` gives `bound`, both self-typed so
  `new Badge("3").styled("danger")` is still a `Badge` — and a widget supplies
  the one line only it can, `withAttributes`. Containers take children as
  varargs, so `List.of` is gone from the showcase entirely. And an application's
  **CSS and markup are resources now**: `Stylesheet.resource` and
  `KdlParser.resource` read files beside a class the way the toolkit reads its
  own, which is also how the badge row became the first thing in a *window* to
  come from KDL rather than from Java (§9 had test coverage and no window
  coverage) —
  [ADR-0093](adr/0093-an-application-is-a-root-widget.md)
- **JPMS encapsulates resources, and the first headless run found out.**
  `exports` governs types; a file inside a package of a named module is invisible
  to other modules unless the package is `opens`. So the toolkit could not read
  the showcase's own `showcase.css`, and the error blamed the file. The message
  now checks whether the owning package is open and names the missing `opens`
  line when it is not. The showcase opens its package **to `:core` only** — an
  unqualified open would hand its private types to everything on the module path
  as well — and this is a line every application will have to write, which is a
  papercut in "implement one interface and go" that nothing can remove. —
  [ADR-0093](adr/0093-an-application-is-a-root-widget.md)
- **`:core` ships no widgets, and its own tests stopped needing any.** `text`,
  `row`, `column`, `panel` and `spacer` were nested records inside a `Widgets`
  class in `:core`, for a reason that had expired: the widget tree, the cascade
  and the painter all had to be provable before there was a catalog to prove them
  with, and five primitives were the smallest set that made the parity invariant
  testable. Once `:widgets` reached thirty types with a package per control, they
  were the only widgets in a module that is not a widget toolkit — and
  `core-widgets.md` had specified their packages since v0.1 while the code had
  them in a different module inside one holder class. They are ordinary top-level
  records now, in the packages the document gives them. `Attributes` **stayed**,
  promoted to a top-level type: it is not a widget but part of the widget
  *contract*, and an application widget wanting an id should not have to depend on
  the catalog to hold three fields. The interesting half was the tests. Two of the
  five that moved could not: `StyleCacheTest` and `BindingTest` reach into
  `Element`'s package-private internals, which is right for a test of the element
  tree and impossible from another module — so they stayed and use **local test
  widgets**, the pattern `DragOriginTest` already established, and nothing in
  `StyleCacheTest` any longer looks like a fact about `panel`. `BindingTest` split
  along a seam that turned out to be real: reading `bind=` off markup is the
  catalog's, and what an element does with a binding once it holds one is
  `:core`'s. The same 1,641 tests run; 25 of them changed module —
  [ADR-0092](adr/0092-a-primitive-is-a-widget-like-any-other.md)
- **The catalog's specified surface roughly tripled, and none of it is built.**
  `docs/core-widgets.md` gained twenty-one widgets and four options in one pass —
  `link`, `affix`, `segmented`, `date-picker`, `time-picker`, `color-picker`,
  `code-input`, autocomplete on both `text-input` and `select`, tree-select,
  `collapse`, `carousel`, `statistic`, `skeleton`, `breadcrumbs`, `steps`,
  `wizard`, `message`, `tour`, `tree`, `calendar`, `timeline`, and `button`'s
  `outlined` / `square` / `circle` / `float` options — each with a
  `design-system.md` §3 metrics row and, where it moves, a §3.1 row. §5 requires
  a spec **and** a metrics row **and** gallery coverage before code, in that
  order: these have passed two gates of three, and the third is what "built"
  means. **One of them is built now** — `segmented`, which was in this list and
  is out of it — and the way it went is the argument for writing them down
  first, read from the other end: two of its five specified metrics and both of
  its specified transitions turned out to be undrawable in §8's subset, and
  that was found by implementing it rather than by writing it. The other twenty
  are still only written down. The point of writing them down
  first is that the arguments are cheap now and expensive later — `message`
  against `toast`, `segmented` against `radio-group`, `code-input` against a
  styled `text-input` are all decisions that would otherwise be made by whoever
  happened to need one.
- **A generated registry can fail at class-init time now, and only for private
  members.** A `VarHandle` lookup that cannot find its field throws
  `ExceptionInInitializerError` where a direct field reference would have thrown
  `NoSuchFieldError` at link time — the same class of failure with a different
  exception, and both are impossible within one compilation, which is how a
  registry and its model are always built. Recorded because it is the one thing
  ADR-0098 moved later rather than earlier.
- **A `static` `@Action` is still unsupported, and now for a second reason.** The
  accessible path generates `target::method`, which does not compile for a static
  method, and the private path writes `findVirtual`. Nothing refuses one
  explicitly; it was broken before ADR-0098 and remains so, because no model has
  ever wanted one.
- **`Styled.restyle` is an escape hatch, and the honest risk is what goes into
  it.** What a widget writes there is unthemeable and unoverridable — right for a
  number nobody else can compute, wrong for anything else. It has one caller in
  the toolkit and one rule ("only what a stylesheet could not have written"); a
  second caller that is *not* a count is the signal to look at it again.
- **A segmented control fills its parent when nothing gives it a width**, which
  is new and is a real loss of convenience: in a toolbar beside other widgets it
  takes the whole row until an author writes `width`. It buys the travelling
  indicator, and there is no third option under flexbox — content-sized cells
  cannot be travelled between, and a zero basis collapses the bar entirely.
- **A segment's label overflows its cell when it is longer than 1/n of the bar**,
  because the cells are equal and nothing clips. Reached by a different road than
  the indeterminate progress sweep documents, and stopped by the same missing
  feature.
- **`flex-basis` is still the only layout property §8 names and nothing
  resolves.** It was implemented for this change, found unnecessary, and removed
  again rather than left as a property with no consumer.
- **Per-corner radii do not exist, and `segmented` is the second control that
  wanted one.** ARCHITECTURE §8 resolves "`border-radius` … (one radius, not
  per-side)" deliberately, and `button.square` was the first thing to ask — §3
  gives it radius 0 "where buttons butt against each other", which is the joined
  drawing seen from the other side. `segmented` is the second, and it went round
  the outside: the bar keeps the radius and the segment is inset. `SegmentedTest`
  pins both numbers so a third asking is a decision that gets revisited rather
  than one that quietly outlives its reason (ADR-0097).
- **The travelling selection indicator is deferred with `tabs`, and it needs
  something the toolkit does not have.** §3.1 gives `segmented` and `tabs` the
  same effect and the same controller; what neither can be written against today
  is *where the next item was laid out*. Geometry exists after a paint and only
  the router can see it (ADR-0080), so a widget that wanted to move something by
  the width of its sibling would need a read-back path and would stop being a
  pure function of its model. Real costs, and they should be paid by `tabs`,
  whose specification actually requires the effect.
- **A segment's focus ring lands exactly on the bar's edge.** §2.2's ring is 2px
  at a 2px offset and the bar's inset is 2, so the two coincide — legible in
  `segmented-focus.png`, and an accident of two numbers derived separately rather
  than a thing anyone chose. If either moves, look at the image.
- **An icon-only segment has no accessible name, and neither does an icon-only
  button.** §3 requires `name=` for both and the attribute does not exist
  anywhere; §13's semantics are M5's. `Option` refuses a segment with neither a
  label nor an icon, which is the half that can be enforced today, and the other
  half is a gap the whole catalog shares rather than one this control invented.
- **`option` lives in `…controls.segmented` and `select` will want it.** §3 gives
  both controls the same child node, so the widget is shared by specification and
  not yet by code. Moving it now would be guessing at what a `select`'s option
  needs — a model, possibly a tree node, a popup to render in — so it stays with
  its only caller until there are two (ADR-0092's rule about a reason that has
  not expired).
- **`tree` moved from deferred to specified**, which changes what M5 owes.
  ARCHITECTURE §17 defers "tables/trees"; `table` still is, because it waits on
  virtualization, but `tree` reuses `list`'s model and item-factory and does not
  — and `select tree=#true` needs it, so the two arrived together.
- **`Kind.WHEEL` now has exactly one consumer, and no scroll view to test it
  against.** The wheel route has been live and covered since ADR-0061 — a
  fabricated `SDL_MouseWheelEvent` pushed onto SDL's own queue, through the real
  `translate` and the real sink — and until `knob` nothing in the toolkit
  *handled* one. What that means is that the whole bubble path for a wheel is
  still unexercised: a knob inside a scroll view should turn without scrolling
  the list, and a knob at its maximum should let the scroll through, and neither
  can be tested until `scroll` exists in M3. `Knob.wheel` consumes
  unconditionally, which is the safe half of that pair and the wrong half if the
  other one turns out to matter. —
  [ADR-0089](adr/0089-a-knobs-gesture-is-a-rate.md)
- **Every pointer event now costs an `SDL_GetModState`.** Polled per event rather
  than carried on it, because SDL's mouse events have no `mod` field. On a 120 Hz
  trackpad that is a few thousand calls a second into a statically linked
  function that reads a global. Not measured — named here so it can be if a
  profile ever points at it. —
  [ADR-0089](adr/0089-a-knobs-gesture-is-a-rate.md)
- **The circular drag is not built, and §3 offers it.** "Rotary: vertical-drag
  primary (**circular-drag optional**)". The vertical drag ships; the circular one
  needs an answer for the pointer crossing the 90° gap at the bottom, and every
  answer is either a jump or a wrap that depends on which way round the user went
  — which needs the accumulated angle, a *second* piece of gesture state, for a
  gesture that is nobody's first choice. —
  [ADR-0089](adr/0089-a-knobs-gesture-is-a-rate.md)
- **Non-text contrast is not checked at all.** `ContrastTest` measures text
  against the fill under it. §1.2's other half — 3:1 for anything that is not
  text — reaches a checked checkbox's mark, a slider's thumb against its groove,
  a spinner's ring, a border against the surface it separates, and the focus ring
  against whatever is behind it. None of them is measured, and ADR-0088's
  argument that the accent ramp did not need to move rests on exactly that
  unenforced number. The arithmetic is already written; what is missing is
  deciding what counts as the "background" of a mark drawn *onto* its own box. —
  [ADR-0088](adr/0088-a-fill-that-carries-text-moves-away-from-it.md)
- **`button.ghost` has no contrast ratio, and is therefore not checked.** Its
  fill is `transparent` and its hover is a `#ffffff14` wash, so what a user
  reads depends on the surface underneath — there is no single pair to measure.
  It is left out of `ContrastTest` rather than measured against black, which is
  what ignoring alpha would silently do and would score it as passing. The same
  is true of `--gb-selection`. A backdrop-aware check would need the painted
  frame rather than the cascade, which is a different kind of test. —
  [ADR-0087](adr/0087-a-semantic-fill-brings-its-own-foreground.md)
- **Nothing validates an application's own theme.** §10 lets an application swap
  the alias tokens, and `ContrastTest` runs over the two themes the toolkit
  ships. A third-party theme that pairs `--gb-badge-warning-bg` with an
  unreadable `--gb-badge-warning-text` is a legibility bug the toolkit will not
  notice — the arithmetic is nine lines and is not exposed as anything an
  application can call. —
  [ADR-0087](adr/0087-a-semantic-fill-brings-its-own-foreground.md)
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
  problem, named here because ADR-0076 is what makes it visible. **`badge` is
  the second consumer, and it wants the other half**: §8's subset has no
  `min-width` at all, so a one-digit chip is a stadium rather than the circle a
  badge usually is. `badge-digits.png` is the record of it. —
  [ADR-0076](adr/0076-a-glyph-does-not-negotiate.md),
  [ADR-0087](adr/0087-a-semantic-fill-brings-its-own-foreground.md)

- **`flex-basis` is the last of §8's `flex-grow/shrink/basis` still unimplemented**,
  and `:core`'s five primitives all still shrink — right for containers, and
  unexamined for `spacer`, which presumably wants to keep a fixed size. —
  [ADR-0076](adr/0076-a-glyph-does-not-negotiate.md)

- ~~**A slider's groove was invisible on a surface.**~~ **Fixed, and it is the
  fourth instance of one defect.** `--gb-slider-track-bg` was `nord1` on the dark
  theme, which **is** `--gb-surface` — so the unfilled groove vanished on any
  panel, which is where the showcase's options live. A slider hides this better
  than anything before it: the fill and the thumb still show, so the control looks
  like a control and merely appears to have no track. It is `--gb-border` now,
  because a 4px groove *is* an edge. What is different this time is that
  **`controls-on-surface-{dark,light}` already existed** — ADR-0073 added it for
  exactly this — and had not been extended to the new control, so the axis was
  covered and the control was not. `everySurfacelessControlIsCovered` now asserts
  every entry in `Controls.controlTypes()` is in that scene, with `button` exempt
  and saying why, and the scene is one helper the golden and the guard share.
  Verified by deleting the slider from the scene and watching it fail by name. —
  [ADR-0079](adr/0079-a-continuous-value-is-placed-by-ratio.md),
  [ADR-0073](adr/0073-a-composite-is-one-tab-stop.md)

- **A bare `text` with no ancestor setting `color` renders black**, which is
  ADR-0066's deliberate `INITIAL` and a trap all the same: the showcase's new gain
  label was unreadable on the dark theme. A control gets away with saying nothing
  because `controls.css` sets `color` on `checkbox`, `radio`, `toggle` and
  `slider` themselves; a primitive does not. The showcase now sets
  `color: var(--gb-text)` on its root, which is what an application should do —
  but nothing warns one that has not. —
  [ADR-0066](adr/0066-a-weight-is-a-face-and-color-inherits.md)

- ~~**A slider has no tick marks and no value label.**~~ **Both ship, and the
  label needed exactly the mechanism this entry predicted.** A widget can name the
  **part** its pointer position is measured against — `Handles.localPart()`, a CSS
  type resolved by the router — because a label at the end of the row takes its
  width off the track and a value mapped along the *control* is short by that
  width at every position, drawn correctly and reported nowhere. The marks hang
  out of a zero-height row, moved clear of the thumb by a `transform` so that
  adding a scale does not move the groove. —
  [ADR-0080](adr/0080-a-value-is-measured-along-a-part.md),
  [ADR-0079](adr/0079-a-continuous-value-is-placed-by-ratio.md)

- ~~**`fader`'s dB scale is not implemented.**~~ **It ships, as a value rather
  than a function.** `Scale` is a sealed interface with two inverse methods and
  two records — the obvious `DoubleUnaryOperator` spelling is the wrong one,
  because §11's parity invariant compares two control records for equality and two
  lambdas doing the same arithmetic never are. `knob`'s taper is what it was built
  general for. What it does *not* have is a second curve: §3 names dB and nothing
  else, and inventing a `log` or an `exp` for symmetry would be inventing a scale
  the design system does not have (Principle 3). —
  [ADR-0080](adr/0080-a-value-is-measured-along-a-part.md)

- **A slider maps the pointer over the track's full width**, so at the extremes
  the thumb's centre is up to 8px from the finger. Mapping over the *travel* needs
  the thumb's width, which is the stylesheet's and not the widget's. The mapping is
  monotonic and reaches both ends exactly; closing the gap means a widget being
  told a resolved metric, which is a bigger door than this is worth. The **tick
  marks do not have this problem**: their inset is half a thumb, written in the
  stylesheet beside the thumb's own width, so a mark and the thumb agree exactly
  while the finger is the thing that is up to 8px out. —
  [ADR-0080](adr/0080-a-value-is-measured-along-a-part.md),
  [ADR-0079](adr/0079-a-continuous-value-is-placed-by-ratio.md)

- **A slider's value label is left-aligned in its box**, because §8's subset has
  no `text-align` — `docs/ARCHITECTURE.md` §8.1 lists it among the properties
  `Box` cannot express, so it resolves into nothing and has no test that could
  mean anything. A right-aligned readout is what the column of numbers beside a
  row of faders wants. It arrives with whatever else needs `Box` to place text
  inside a box rather than at its origin. —
  [ADR-0080](adr/0080-a-value-is-measured-along-a-part.md)

- **A toggle's thumb does not follow the pointer during the drag — and the
  design system says it should not.** Left open as a defect after ADR-0075 and
  closed by reading rather than by building: §1.7's first principle names the
  controls that track 1:1 — "drags (**slider, knob, fader, splitter, scroll**)
  track the pointer 1:1" — and `toggle` is not among them, while §3.1's `toggle`
  row asks for the opposite, "thumb `translate` **base**". A switch here is a
  control with two positions that animates between them, and tracking the finger
  would be a third behaviour neither document asks for. It would also cost the
  mechanism the entry named: transient per-element state for a value that is
  neither the model's nor the stylesheet's, which nothing else in the catalog
  wants. Reopened only if the design system changes its mind, in writing. —
  [ADR-0075](adr/0075-a-gestures-origin-is-the-routers.md),
  `docs/design-system.md` §1.7, §3.1

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
  are specifications without subjects — and two of the controller's three subjects
  turned out not to need one.** `opening → open → closing → removed` applies to
  menus, popovers, tooltips, dialogs and toasts, none of which exist. The
  controller was to drive indeterminate progress, the spinner and toast reflow;
  the first two ship as **functions of the frame clock with no state at all**,
  because a loop that never ends has nothing to remember and a controller would be
  a per-element copy of the time that puts two spinners permanently out of phase.
  What is left for it is the work with a real lifecycle — a start, an end, and an
  interruption to reverse from — which is toast reflow and the overlay sequence,
  and both are M3. —
  [ADR-0081](adr/0081-a-perpetual-loop-has-no-state.md),
  [ADR-0067](adr/0067-motion-is-an-overlay-on-a-frame-clock.md)

- **Nothing clips, so an indeterminate bar turns where it should run off the
  edge.** §3.1's sweep is conventionally drawn running off one end and back in at
  the other, which needs `overflow: hidden` — absent, along with the scroll view
  and the ellipsis that the same missing feature blocks. What ships reverses at
  the ends instead, which needs no clipping and has no wrap to hide. The
  reduced-motion **opacity pulse** §3.1 asks for is missing for the same class of
  reason: a pulse is a loop between two opacities and §8 has no `@keyframes`, so a
  reduced-motion bar holds still rather than breathing. —
  [ADR-0081](adr/0081-a-perpetual-loop-has-no-state.md)
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
- **What does the release container actually compile into its Wayland driver?**
  Two dependencies decide it and `linux.yml` installs neither. `egl` is one of the
  five specs in SDL's single `CheckWayland` `pkg_check_modules` — lose any one and
  the entire Wayland driver is dropped *silently*, and the container has no
  `mesa-libEGL-devel`. `libdecor-0` decides whether a Wayland window that does get
  built has a titlebar and a resize edge, and whether `libdecor-devel` even exists
  in AlmaLinux 8's repositories is unverified. The manylinux leg runs CMake
  directly with no JDK, so `checkToolchain` never gets to ask either question, and
  the drift guard deliberately holds that workflow only to the packages SDL
  refuses to configure without. Answering it means reading
  `SDL_VIDEO_DRIVER_WAYLAND` and `HAVE_LIBDECOR_H` out of a container build's
  `SDL_build_config.h` — not another look at the table. —
  [ADR-0082](adr/0082-a-preflight-check-that-cannot-fail-is-not-a-check.md),
  [ADR-0083](adr/0083-on-gnome-wayland-libdecor-is-not-a-fallback.md)
- ~~**Nothing warns at run time that a window came up undecorated.**~~
  **Answered: `WaylandDecorations` warns, once, with the command that fixes it.**
  Not by asking SDL, which cannot answer — `libdecor_new` succeeds even when every
  plugin failed, so SDL marks the surface `WAYLAND_SHELL_SURFACE_TYPE_LIBDECOR`
  and exposes nothing to say the frame is empty. It is inferred from which plugin
  files are installed, which works because the GTK plugin is *guaranteed* to fail
  in a JVM. The verdict is three-valued and stays silent when it cannot locate a
  plugin directory: a warning that is sometimes wrong is worse than none. —
  [ADR-0084](adr/0084-the-gtk-plugin-cannot-decorate-a-jvms-window.md)
- **No CI leg exercises Wayland.** `example.yml` and `showcase.yml` both run under
  `xvfb-run`, which is X11, where the window manager decorates the window and
  libdecor is never reached — which is why two consecutive decoration bugs shipped
  without a single red tick. A Wayland leg needs a headless compositor in CI
  (`weston --backend=headless` or `sway --headless`), which is a job nobody has
  written yet. —
  [ADR-0084](adr/0084-the-gtk-plugin-cannot-decorate-a-jvms-window.md)
- **Native decorations on Wayland need a launcher that embeds the VM.** The GTK
  plugin is the only thing that draws decorations matching the desktop, and its
  one requirement is `getpid() == gettid()`. The stock `java` launcher runs `main`
  on a thread it creates and so fails it; a launcher whose own `main` calls
  `JNI_CreateJavaVM` and then the Java `main` runs Java on the primordial thread,
  and the plugin loads there — demonstrated with a throwaway C launcher against
  the real showcase. `jpackage` does not help; it goes through the same
  `ContinueInNewThread`. Shipping one is a distribution change (a native binary
  per platform, VM argument handling, and a story for `./gradlew run` and
  `java -jar`), so it is recorded as the answer and not yet taken. Two things
  bound how much to invest in it: upstream is building an out-of-process GTK
  plugin (libdecor MR 176) that dissolves the thread restriction entirely when it
  ships, and the ecosystem's own answer on GNOME/Wayland is that every non-GTK
  toolkit — Qt, Firefox, Chromium — draws its own decorations in-process, which is
  the `SdlWindowFlag.BORDERLESS` design Goldberry has reserved but not built. —
  [ADR-0084](adr/0084-the-gtk-plugin-cannot-decorate-a-jvms-window.md)
- ~~**A Goldberry window on GNOME/Wayland has no titlebar out of the box.**~~
  **Answered: X11 is the Linux default now.** On a Wayland session the backend asks
  SDL for `x11,wayland`, unconditionally — under XWayland the window manager
  decorates the window itself, which is the only configuration today that produces
  a titlebar matching the desktop. Wayland stays behind X11 rather than being
  dropped, so a session without XWayland still gets a window, and the
  [ADR-0084](adr/0084-the-gtk-plugin-cannot-decorate-a-jvms-window.md) warning
  still fires there. `-Dgoldberry.backend.videoDriver=wayland` asks for Wayland
  anyway. The cost is
  [ADR-0027](adr/0027-prefer-wayland-fall-back-to-x11.md)'s resize quality and
  fractional scaling, given up for as long as decorations are unobtainable on the
  better axis. —
  [ADR-0086](adr/0086-x11-is-the-linux-default-for-now.md)
- **A window on GNOME/Wayland needs two packages from two different phases.**
  `libdecor-0-dev` at build time, or SDL compiles no libdecor support at all
  ([ADR-0083](adr/0083-on-gnome-wayland-libdecor-is-not-a-fallback.md)), and
  `libdecor-0-plugin-1-cairo` at run time, because the GTK plugin that libdecor
  pulls in by default refuses to start off the process's initial thread and a JVM
  is never on it ([ADR-0084](adr/0084-the-gtk-plugin-cannot-decorate-a-jvms-window.md)).
  Installing either alone leaves the window bare. Whether Goldberry should carry
  its own decorations instead — `SdlWindowFlag.BORDERLESS` already describes the
  design — is the standing question behind both records.
- ~~**A window on GNOME/Wayland had no titlebar and could not be resized.**~~
  **Answered: SDL was built without libdecor.** Wayland has no decoration protocol
  of its own, GNOME's compositor declines to draw them server-side, and every use
  of the client-side path in SDL sits behind `#ifdef HAVE_LIBDECOR_H`. Without
  `libdecor-0-dev` SDL builds a complete Wayland driver that opens an undecorated
  toplevel — and since a Wayland resize is client-initiated from the decoration's
  own edge, the same missing header removes resizing too. The Java side was never
  involved: `WindowSpec.of` asks for decorated and resizable and
  `Sdl3Backend.createWindow` passes exactly that. It only became visible when
  ADR-0082 added `egl` and the Wayland driver started being built at all. —
  [ADR-0083](adr/0083-on-gnome-wayland-libdecor-is-not-a-fallback.md)
- ~~**`checkToolchain` passed and the build died two minutes later.**~~
  **Answered: the table it checked had drifted from what SDL demands.** It probed
  `pkg-config --exists xss`, a module no distribution ships — SDL's own spec is
  `xscrnsaver` — so the row returned "absent" whether the package was installed or
  not, and it was marked optional besides, while SDL's `CheckX11` treats
  XScrnSaver as a `FATAL_ERROR`. XTest, the next hard stop in line, was not in the
  table at all. Both CI workflows already knew all of this, in comments, written
  by whoever hit it there twice. The table is now `LinuxDependencies` in
  build-logic with a three-valued `Necessity`, and `LinuxDependenciesTest` asserts
  it against the packages the workflows install — the invariant that broke. —
  [ADR-0082](adr/0082-a-preflight-check-that-cannot-fail-is-not-a-check.md)
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
