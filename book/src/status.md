# Status

Tracked against the milestone ladder in `docs/ARCHITECTURE.md` §16.

| Milestone | State | |
|---|---|---|
| Foundation | **done** | Multi-module Gradle (Groovy DSL), version catalog, convention plugins, JPMS module graph, JDK 25 toolchain, JUnit 6, licence disclosure, decision log |
| M0 — Skeleton | **in progress** | Native build and CI wired; binding foundation, library loader, and layout-verification harness in place. Not yet linked: the superbuild has never completed a build. Still to come: SDL3 bindings, the backend SPI, `headless`, and a blank window at correct fractional DPI |
| M1 — Vertical slice | not started | Styled wrapped paragraph, resized at 60 fps; paragraph cache + upcall benchmarks |
| M2 — Widgets & style | not started | CSS engine, KDL inflater + hot reload, core controls, Nord light/dark, golden-image CI |
| M3 — Shell | not started | Menus, popups, tray, dialogs, scroll, forms, CSD, charts, widget showcase |
| M4 — GPU | not started | `canvas3d`, GPU composition, dmabuf on Scarlet |
| M5 — Hardening | not started | Text editing depth, AccessKit bridge, IME preedit, docs, 0.1 release |

## Module layout

| Module | Artifact | Contents |
|---|---|---|
| `:natives` | `goldberry-natives-{platform}-{arch}` | Hand-written FFM bindings, owning wrappers, and the CMake superbuild that produces `libgoldberry` |
| `:core` | `goldberry-core` | Widgets, style, layout, text, paint, the backend SPI |
| `:widgets` | `goldberry-widgets` | The widget catalog — controls, containers, menus, charts — plus the showcase screens that serve as the visual regression corpus |
| `:gpu` | `goldberry-gpu` | `canvas3d` and the GPU composition path |

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
- **`YGSize` struct-by-value upcall returns.** The fiddliest corner of FFM, and it
  sits on the layout engine's hot path. Should be proven first in M0. —
  [ADR-0010](adr/0010-hand-written-ffm-bindings.md)
- **The superbuild has never linked, and CI has never run.** Upstream refs in
  `gradle/libs.versions.toml` and the CMake target names in the superbuild are
  marked `VERIFY` and unresolved, and the manylinux container will need its
  X11/Wayland development headers tuned on the first run. Blocks all of M0. —
  [ADR-0012](adr/0012-native-ci-runners-with-a-pinned-glibc.md)
- **Blend2D has no release tags**, so it is pinned by branch. That must become a
  commit SHA before anything is published, or the artifacts are irreproducible.
- **CMake arguments live in two places** — `natives/build.gradle` for local builds
  and the CI workflows — because the manylinux container has no JDK. They must be
  kept in step. — [ADR-0012](adr/0012-native-ci-runners-with-a-pinned-glibc.md)
- **No licence text is vendored yet.** Every file in `licenses/` is a placeholder.
  `./gradlew checkLicenses -Pgoldberry.releaseCheck=true` fails until they are
  copied verbatim from the pinned upstream revisions. —
  [ADR-0015](adr/0015-licensing-and-third-party-disclosure.md)
