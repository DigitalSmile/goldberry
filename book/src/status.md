# Status

Tracked against the milestone ladder in `docs/ARCHITECTURE.md` §16.

| Milestone | State | |
|---|---|---|
| Foundation | **done** | Multi-module Gradle (Groovy DSL), version catalog, convention plugins, JPMS module graph, JDK 25 toolchain, JUnit 6, licence disclosure, decision log |
| M0 — Skeleton | **all but Windows** | **The superbuild links on four of six targets.** Blend2D, AsmJit, SDL3, Yoga, HarfBuzz and libxkbcommon statically combine into one `libgoldberry` exporting exactly the symbols on the export list and nothing else — both Linux targets in CI's manylinux containers, both macOS targets on an Apple Silicon runner. The layout probe passes against the real library, and Yoga's measure callback crosses in both directions including the `YGSize` struct-by-value return ([ADR-0017](adr/0017-proving-the-struct-by-value-upcall.md)), so the hand-written binding mechanism is proven end to end. **Yoga's node API is bound**, and the callback is now driven by real layout passes rather than by a C probe written for the purpose ([ADR-0029](adr/0029-yogas-node-api-and-who-owns-a-node.md)). SDL3's lifecycle, error and version calls are bound and tested against the real library ([ADR-0018](adr/0018-sdl-conventions-stop-at-the-boundary.md)). The backend SPI, the `headless` backend and the `sdl3` backend are in `:core`, with fractional DPI correct by construction ([ADR-0019](adr/0019-the-backend-spis-first-cut.md)) and background work on virtual threads that completes on the UI thread ([ADR-0020](adr/0020-one-ui-thread-and-virtual-threads-behind-it.md)). **The showcase opens a window and presents frames** ([ADR-0021](adr/0021-the-example-is-a-separate-build.md)), through a `Window` front door that names no backend and builds no event loop ([ADR-0022](adr/0022-window-is-the-front-door.md)). Still to come: the two Windows targets |
| M1 — Vertical slice | **started** | **Blend2D rasterizes the frame, HarfBuzz shapes the text.** `Frame` no longer writes pixels by hand: it wraps the platform's own buffer in a `BLImage` without copying it, scales the context by the display factor so coordinates stay logical and fractional edges antialias rather than snap, and blends with alpha that now means something ([ADR-0031](adr/0031-blend2d-and-the-borrowed-buffer.md)). The showcase paints through it. Shaping takes UTF-16 straight from a Java `String`, so the cluster indices point back into the caller's own text ([ADR-0032](adr/0032-shaping-is-utf16-in-glyphs-out.md)). **Text draws.** Blend2D's font chain is bound and a `GlyphRun` reaches the rasterizer: `Font` in `:core` owns a HarfBuzz font and a Blend2D one over the same bytes, shapes in design units and puts the size on the Blend2D font alone, so the font matrix is the only thing that converts ([ADR-0034](adr/0034-one-size-and-the-design-unit-crossing.md)). The showcase draws two lines of Inter, and the tests assert *where* the ink landed — the inked span matches the measured width, which fails by a factor of 128 if either side of that crossing is wrong. **And text takes part in layout.** A `Paragraph` shapes once and wraps with arithmetic over that one `GlyphRun`, so its measure function answers Yoga from inside a layout pass without shaping again ([ADR-0036](adr/0036-the-paragraph-is-shaped-once-and-wrapped-many-times.md)). A `Box` with text is a measured leaf: the showcase's body wraps to whatever width the sidebar leaves it, and its siblings are positioned against the height that comes back. Two numbers are written down in that layout — the bar's height and the padding — and everything else comes from content. **The cache and the benchmarks are done** ([ADR-0037](adr/0037-what-the-text-path-costs.md)): `./gradlew benchmark` measures the text path, and the numbers say the upcall crossing is ~0.3 µs, a memoised wrap 0.02 µs, and shaping 56 µs — so `ParagraphCache` caches shaping and nothing else. **What remains is the 60 fps claim itself.** A 960×640 frame with a wrapped paragraph runs at a 7.86 ms median and a 14.18 ms p95, which fits the budget with a factor of two in hand on the median and none at the tail — measured on one machine, and that machine is a VirtualBox VM on X11. The milestone asks for a Mac, a ThinkPad and a Scarlet compositor. **Yoga and Blend2D now meet**: `BoxPainter` lays a flexbox tree out and fills the result, setting Yoga's point scale factor from the display scale so computed edges land on physical pixels — the first code for which the fractional-DPI claim is a mechanism rather than an intention. Inter, JetBrains Mono, OpenMoji and Lucide's 1544 icons are fetched at build time, pinned by checksum, and packaged into `goldberry-core` ([ADR-0033](adr/0033-assets-are-fetched-and-compiled-not-committed.md)) |
| M2 — Widgets & style | not started | CSS engine, KDL inflater + hot reload, core controls, Nord light/dark, golden-image CI |
| M3 — Shell | not started | Menus, popups, tray, dialogs, scroll, forms, CSD, charts, widget showcase |
| M4 — GPU | not started | `canvas3d`, GPU composition, dmabuf on Scarlet |
| M5 — Hardening | not started | Text editing depth, AccessKit bridge, IME preedit, docs, 0.1 release |

## Module layout

| Module | Artifact | Contents |
|---|---|---|
| `:natives` | `goldberry-natives-{platform}-{arch}` | Hand-written FFM bindings, owning wrappers, and the CMake superbuild that produces `libgoldberry` |
| `:core` | `goldberry-core` | Widgets, style, layout, text, paint, the backend SPI, and the backends themselves — `headless` today, `sdl3` and `scarlet` to come |
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
there is no cross-compilation toolchain. Four runner types produce six artifacts,
with two of them cross-targeting inside their own native toolchain.

| Target | Built on | Output |
|---|---|---|
| `linux-x64` | `ubuntu-24.04` + `manylinux_2_28_x86_64` | `libgoldberry.so` |
| `linux-aarch64` | `ubuntu-24.04-arm` + `manylinux_2_28_aarch64` | `libgoldberry.so` |
| `windows-x64` | `windows-2022`, MSVC `-A x64` | `goldberry.dll` |
| `windows-aarch64` | `windows-2022`, MSVC `-A ARM64` | `goldberry.dll` |
| `macos-aarch64` | `macos-14` | `libgoldberry.dylib` |
| `macos-x64` | `macos-14`, `CMAKE_OSX_ARCHITECTURES=x86_64` | `libgoldberry.dylib` |

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

- **The state and rebuild API.** The three-tree model is settled; the
  stateful-widget lifecycle, rebuild scheduling, and dirty-marking are not.
  Blocks M2. — [ADR-0004](adr/0004-three-tree-retained-declarative-model.md)
- **KDL 2.0 Java parser.** KDL is the stable contract, but whether a suitable
  Java parser exists at the required maturity is unverified. Blocks M2. —
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
- **Only the Linux targets have ever been built.** Both link, x64 locally and in
  the manylinux container and aarch64 in the container, so the ELF version-script
  branch of the export machinery and the container's X11/Wayland header list are
  no longer guesses. The MSVC (`/INCLUDE:`, `.def`) and Mach-O (`-u,_symbol`,
  `-exported_symbols_list`) branches remain unexercised — the Windows and macOS
  workflows have never run. —
  [ADR-0012](adr/0012-native-ci-runners-with-a-pinned-glibc.md)
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

- **Live resize stalls on Windows and macOS.** Both run a modal loop during a
  resize gesture, so SDL does not return from event pumping until the drag ends
  and frames stop with it. Wayland and X11 are prompt. The fix is
  `SDL_AddEventWatch`, drawing from inside SDL's own callback, which is a
  different shape of frame loop and waits for a renderer worth driving from it. —
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
  magnitude, and most of it is waiting on the compositor rather than copying.
  That is the number to have before optimising anything: double-buffering the
  paint target cannot win back more than the ~12% of a frame that painting
  costs, and buying it would mean giving up the borrowed buffer
  ([ADR-0019](adr/0019-the-backend-spis-first-cut.md)) and reintroducing a
  full-frame copy. The real levers, in order, are damage tracking — blocked on
  the widget tree knowing what changed — and understanding what `present` is
  actually blocked on. Blend2D's `thread_count` is a third, and only matters if
  paint ever becomes the bottleneck. —
  [ADR-0031](adr/0031-blend2d-and-the-borrowed-buffer.md)

- **A build with no network cannot produce a usable `goldberry-core`.** The
  bundled fonts and icons are fetched from upstream releases and cached, so this
  bites once per checkout rather than once per build — but a jar assembled
  without the asset step contains a toolkit that cannot render text. The build
  already needed network for the native superbuild, so no new constraint; it is
  written down because the failure is far from its cause. —
  [ADR-0033](adr/0033-assets-are-fetched-and-compiled-not-committed.md)

- ~~**Nothing draws a glyph or an icon yet.**~~ **Glyphs do; icons still do not.**
  `bl_font_*` and `bl_context_fill_glyph_run_d_rgba32` are bound, and a shaped
  run reaches the screen. The icon half is untouched: the Lucide table holds SVG
  path data and Blend2D's path API — `bl_path_*` and `bl_context_fill_path_*` —
  is not bound. It was scoped out on purpose, because it shares nothing with the
  font chain except the context. —
  [ADR-0034](adr/0034-one-size-and-the-design-unit-crossing.md)

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

- **A `Font` costs two copies of the font file, and there is one per size.**
  `docs/ARCHITECTURE.md` §6 says "one font buffer feeds both `hb_face_t` and
  `BLFontFace`", and today it does not: HarfBuzz copies the bytes and Blend2D is
  pointed at a second copy, because each library owns its own. The guarantee
  behind that sentence still holds — the two are byte-identical, so their metrics
  cannot disagree — but the memory does not. Inter costs about a megabyte and a
  half per `Font`, and the showcase's two sizes cost three megabytes of outlines.
  A shared face cache is the fix; nothing above depends on its absence, so it can
  land whenever the size count starts to matter. —
  [ADR-0034](adr/0034-one-size-and-the-design-unit-crossing.md)

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

- **A fresh upcall stub per text box per frame is the largest cost of text in a
  layout pass.** One paragraph takes a pass from 12.5 µs to 40.4 µs, and 11 µs of
  that is `MeasureCallback.of` — an `Arena` and a `MethodHandle` bound into native
  code — because `BoxPainter` rebuilds the Yoga tree every frame. The crossing
  itself is ~0.3 µs, so the stub costs forty times what calling through it does.
  The retained render tree removes this by keeping the node; this is the first
  measurement that makes ADR-0004 a performance argument as well as a design one. —
  [ADR-0037](adr/0037-what-the-text-path-costs.md),
  [ADR-0004](adr/0004-three-tree-retained-declarative-model.md)

- **Painting now dominates a frame, and half of that reversal is a driver change.**
  Over 119 frames at 960×640 with text: buffer 0.18 ms, **paint 5.10 ms**, present
  1.92 ms, total 7.86 ms median, 14.18 ms at p95, and 3 frames of 119 over the
  16.67 ms budget. ADR-0031 had paint at ~1.3 ms and present at ~10 ms and
  concluded present dominated by an order of magnitude. Text is what moved paint;
  **X11 rather than Wayland is what moved present**, since these frames were
  measured on X11 after the Wayland run crashed the compositor. The like-for-like
  Wayland measurement is still owed, and nothing here made present faster.
  Blend2D's `thread_count` was parked in ADR-0031 as "only matters if paint ever
  becomes the bottleneck"; on these numbers it has. —
  [ADR-0037](adr/0037-what-the-text-path-costs.md),
  [ADR-0031](adr/0031-blend2d-and-the-borrowed-buffer.md)

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

- **Every frame damages the whole window.** `Window.paint` presents
  `DamageRect.all(...)`, so nothing exploits partial repaint yet. At 960×640
  that is affordable; at 4K it will not be. The damage rects already flow
  through the SPI — what is missing is anything that knows which parts changed,
  which is the same thing the state and rebuild API is blocked on. —
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
