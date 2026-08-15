# Status

Tracked against the milestone ladder in `docs/ARCHITECTURE.md` §16.

| Milestone | State | |
|---|---|---|
| Foundation | **done** | Multi-module Gradle (Groovy DSL), version catalog, convention plugins, JPMS module graph, JDK 25 toolchain, JUnit 6, licence disclosure, decision log |
| M0 — Skeleton | **all but Windows** | **The superbuild links on four of six targets.** Blend2D, AsmJit, SDL3, Yoga, HarfBuzz and libxkbcommon statically combine into one `libgoldberry` exporting exactly the symbols on the export list and nothing else — both Linux targets in CI's manylinux containers, both macOS targets on an Apple Silicon runner. The layout probe passes against the real library, and Yoga's measure callback crosses in both directions including the `YGSize` struct-by-value return ([ADR-0017](adr/0017-proving-the-struct-by-value-upcall.md)), so the hand-written binding mechanism is proven end to end. SDL3's lifecycle, error and version calls are bound and tested against the real library ([ADR-0018](adr/0018-sdl-conventions-stop-at-the-boundary.md)). The backend SPI, the `headless` backend and the `sdl3` backend are in `:core`, with fractional DPI correct by construction ([ADR-0019](adr/0019-the-backend-spis-first-cut.md)) and background work on virtual threads that completes on the UI thread ([ADR-0020](adr/0020-one-ui-thread-and-virtual-threads-behind-it.md)). **The showcase opens a window and presents frames** ([ADR-0021](adr/0021-the-example-is-a-separate-build.md)), through a `Window` front door that names no backend and builds no event loop ([ADR-0022](adr/0022-window-is-the-front-door.md)). Still to come: the two Windows targets, and Yoga's node API |
| M1 — Vertical slice | not started | Styled wrapped paragraph, resized at 60 fps; paragraph cache + upcall benchmarks |
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

`:example` is a fifth subproject and is not published: it is the showcase, and it
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
- ~~**`YGSize` struct-by-value upcall returns.**~~ **Answered.** A Java upcall
  returning `YGSize` by value is called from C and arrives intact; the return
  segment is allocated once per callback rather than per call, and an exception
  thrown by a measure function is held and rethrown in Java instead of taking the
  process with it. Proven on linux-x64; the check runs on every target in CI, so
  the other five are answered by the next run rather than by argument. What
  remains is binding Yoga's node API so the callback is driven by a real layout
  pass. — [ADR-0017](adr/0017-proving-the-struct-by-value-upcall.md)
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
- **Blend2D and AsmJit have no release tags**, so both are pinned by branch and
  the build is not reproducible. They must become commit SHAs before publishing.
- **The layout registry has two entries.** The canary, the primitive widths, and
  now `YGSize`. Its value keeps arriving as `SDL_Event` and the rest are bound —
  and `YGSize` is a reminder of the limit: identical on all six targets, so its
  row proves nothing the round trip in
  [ADR-0017](adr/0017-proving-the-struct-by-value-upcall.md) does not. —
  [ADR-0010](adr/0010-hand-written-ffm-bindings.md)
- **The export machinery was never exercised on an upstream symbol until now.**
  `--exclude-libs,ALL` forced static-archive symbols local, and a version script
  cannot promote a symbol already marked hidden, so `SDL_Init` linked in without
  being exported. Fixed by removing the flag — the version script's `local: *`
  was always sufficient. The equivalent question on the MSVC `.def` and Mach-O
  `-exported_symbols_list` branches is answered by the next CI run, not by
  argument. — [ADR-0018](adr/0018-sdl-conventions-stop-at-the-boundary.md)
- **CMake arguments live in two places** — `natives/build.gradle` for local builds
  and the CI workflows — because the manylinux container has no JDK. They must be
  kept in step. — [ADR-0012](adr/0012-native-ci-runners-with-a-pinned-glibc.md)
- **No licence text is vendored yet.** Every file in `licenses/` is a placeholder.
  `./gradlew checkLicenses -Pgoldberry.releaseCheck=true` fails until they are
  copied verbatim from the pinned upstream revisions. —
  [ADR-0015](adr/0015-licensing-and-third-party-disclosure.md)
