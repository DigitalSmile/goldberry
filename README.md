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

**The first native build downloads about 330 MB** — Blend2D, AsmJit, Yoga,
HarfBuzz and SDL3, cloned by the superbuild — which typically takes a few
minutes. Compiling them afterwards is comparatively quick, around a minute on a
recent laptop. Git reports its progress as it goes, so a configure step that
looks idle for a long time is a slow connection rather than a stuck build
(ADR-0029).

The clones land in `natives/.deps/<target>`, deliberately outside `build/` so
that `./gradlew clean` does not throw them away. To discard them on purpose:

```sh
./gradlew :natives:cleanNativeDeps
```

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

## Hello, window

```java
var window = Window.open("Hello", 960, 640);
window.onPaint(frame -> frame.fill(0xFF2E3440));
Goldberry.run();
```

That is the whole API for a window: no backend to name, no event loop to build,
no `switch` over platform events (ADR-0022). Painting takes **logical**
coordinates, so the same code is correct at 100%, 125% and 150%.

Work that is not instant goes off the UI thread and comes back on it:

```java
Goldberry.async(() -> loadTheThing())
         .thenAccept(thing -> window.title(thing.name()));   // on the UI thread
```

## Run the showcase

`:example` is an ordinary subproject that runs on the module path, which is what
catches an unexported package or a wrong `--enable-native-access` (ADR-0023).

```sh
./gradlew :natives:cmakeBuild     # once, to build libgoldberry
./gradlew run
```

A window opens. `-Pgoldberry.example.frames=3` paints three frames and exits,
which is what CI runs under Xvfb.

**On macOS**, AppKit has to be driven from the process's first thread, so any
Goldberry application needs `-XstartOnFirstThread`. `./gradlew run` passes it for
you; an application of your own has to pass it itself, exactly as it would for
LWJGL or SWT. Without it `SDL_Init` fails with "No available video device", which
says nothing about threads — see [ADR-0030](book/src/adr/0030-macos-needs-the-first-thread.md).

## Logging

Goldberry logs through **SLF4J** and binds no implementation. Add one and the
toolkit's diagnostics appear:

```groovy
runtimeOnly 'ch.qos.logback:logback-classic:1.5.18'
```

Add nothing and you get silence — including from SLF4J itself, which otherwise
prints a "no providers were found" warning to stderr (ADR-0023).

At `TRACE`, Goldberry reports a start-up timeline and per-frame timings — which is
how to find out where a slow start or a slow frame went (ADR-0028):

```text
start-up timeline (866.6ms to here):
     533.8ms    +533.8ms  runtime starting
     559.6ms     +25.8ms  libgoldberry mapped (1.9ms)
     722.6ms    +162.9ms  SDL video subsystem up (99.2ms)
     866.6ms    +117.1ms  first frame presented
```

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
