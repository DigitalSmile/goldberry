# Goldberry

[![Linux](https://github.com/digitalsmile/goldberry/actions/workflows/linux.yml/badge.svg)](https://github.com/digitalsmile/goldberry/actions/workflows/linux.yml)
[![Windows](https://github.com/digitalsmile/goldberry/actions/workflows/windows.yml/badge.svg)](https://github.com/digitalsmile/goldberry/actions/workflows/windows.yml)
[![macOS](https://github.com/digitalsmile/goldberry/actions/workflows/macos.yml/badge.svg)](https://github.com/digitalsmile/goldberry/actions/workflows/macos.yml)
[![Example](https://github.com/digitalsmile/goldberry/actions/workflows/example.yml/badge.svg)](https://github.com/digitalsmile/goldberry/actions/workflows/example.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

**A fast and modern UI toolkit for Java.**

Goldberry is a declarative desktop UI toolkit written in pure Java
over a small set of native C libraries bound via the Foreign Function & Memory
API. No JNI, no bundled web engine, no platform widget wrapping.

- **Starts in milliseconds.** CPU rasterization, no GPU context for plain UI,
  GraalVM native-image as a first-class target.
- **Declarative.** Immutable widgets with a pure `build()`, expressed as Java
  records or as KDL markup. Markup and stylesheets hot-reload at runtime.
- **Real layout and real styling.** Flexbox via Yoga, and a genuine CSS subset
  with variables, cascade, and transitions — not a proprietary styling DSL.
- **Cross-platform from the first commit.** Linux (Wayland/X11), Windows, macOS 
  are peer backends behind one SPI.

> **Pre-release.** A window opens and presents frames; there are no widgets yet.
> See [Status](book/src/status.md) for what works and what is still open.

## Quick start

Requires a **JDK 25** toolchain. Gradle provisions one if it cannot find one.

```sh
./gradlew build
```

That builds and tests every Java module and, if it is missing or out of date,
the native library `libgoldberry` for this machine. Building the native library
needs CMake ≥ 3.28, Ninja, a C/C++ toolchain, and — on Linux, for libxkbcommon —
Meson. `./gradlew :natives:checkToolchain` verifies all of it up front and names
the packages to install if anything is absent.

For a Java-only build with no native toolchain:

```sh
./gradlew build -Pgoldberry.skipNative=true
```

Tests that need real native code skip when no library is loadable, so this stays
green — it just verifies less.

Released artifacts are built on native runners per platform, so a locally built
library is for development only. To check a library built somewhere else — a CI
artifact, a colleague's build — point the tests at it instead of building one:

```sh
./gradlew :natives:test \
  -Dgoldberry.native.library=/path/to/libgoldberry.so \
  -Dgoldberry.native.required=true
```

Supplying a library is also what tells the build not to build its own. Adding
`goldberry.native.required=true` turns "no library, skip quietly" into a failure,
which is how CI verifies that the artifact it just built actually loads — see
[ADR-0016](book/src/adr/0016-verify-the-artifact-and-never-skip-the-check.md).

## Run the showcase

`example/` is a separate build that consumes Goldberry the way an application
would — through published coordinates, on the module path (ADR-0021).

```sh
./gradlew :natives:cmakeBuild     # once, to build libgoldberry
cd example && ./gradlew run
```

A window opens. `-Pgoldberry.example.frames=3` paints three frames and exits,
which is what CI runs under Xvfb.

## Documentation

| Where | What |
|---|---|
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | The design: what the system is, layer by layer |
| [`book/`](book/src/introduction.md) | Why each significant choice was made, one decision at a time |
| [`book/src/status.md`](book/src/status.md) | Module layout, milestones, and open questions |

The book is [mdBook](https://rust-lang.github.io/mdBook/):

```sh
mdbook serve book
```

## License

Goldberry is licensed under the [Apache License 2.0](LICENSE).

It bundles third-party software and assets under their own licenses, disclosed in
[THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) and [`licenses/`](licenses/), and
shipped inside every jar under `META-INF/`. Notably, the native libraries are
statically linked into `libgoldberry`, and the bundled OpenMoji emoji font is a
modified, share-alike (CC BY-SA 4.0) derivative — see
[ADR-0015](book/src/adr/0015-licensing-and-third-party-disclosure.md).

```sh
./gradlew checkLicenses
```
