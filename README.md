<p align="center">
  <img src=".github/assets/goldberry-banner-1600x500.webp"
       alt="Goldberry — Modern Java UI toolkit" width="100%">
</p>

[![Maven Central](https://img.shields.io/maven-central/v/dev.goldberry/goldberry?label=Maven%20Central)](https://central.sonatype.com/artifact/dev.goldberry/goldberry)
[![Snapshot](https://github.com/digitalsmile/goldberry/actions/workflows/snapshot.yml/badge.svg)](https://github.com/digitalsmile/goldberry/actions/workflows/snapshot.yml)
[![Showcase](https://github.com/digitalsmile/goldberry/actions/workflows/showcase.yml/badge.svg)](https://github.com/digitalsmile/goldberry/actions/workflows/showcase.yml)
[![Release](https://github.com/digitalsmile/goldberry/actions/workflows/release.yml/badge.svg)](https://github.com/digitalsmile/goldberry/actions/workflows/release.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

**A fast and modern UI toolkit for Java.**

Website: **[goldberry.dev](https://goldberry.dev)** · Documentation: **[goldberry.dev/docs](https://goldberry.dev/docs/)**

Goldberry is a declarative desktop UI toolkit written in pure Java over a small
set of native C libraries bound through the Foreign Function & Memory API. No
JNI, no bundled web engine, no platform widget wrapping.

- **Starts in milliseconds.** CPU rasterization, GraalVM native-image as a
  first-class target, and the GPU only when `goldberry-gpu` is on the module
  path.
- **Declarative.** Immutable widgets with a pure `build()`, written as Java
  records or as KDL markup. Markup and stylesheets hot-reload at runtime.
- **Real layout and real styling.** Flexbox via Yoga, and a genuine CSS subset
  with variables, cascade and transitions.
- **Cross-platform.** Linux (Wayland/X11), Windows and macOS are peer platforms
  behind one SPI.

> **Releases are on Maven Central.** The current release is `2026.2`, and every
> push to `master` publishes a `-SNAPSHOT` of the next version. There is no
> screen-reader support on any platform. [Status](book/src/status.md) says what
> is built, and [TODO](book/src/TODO.md) what is not.

## Quick start

Requires **JDK 25**. Add the BOM for the version, the umbrella artifact for the
toolkit, and one natives jar per platform you run on —
[Installing](book/src/getting-started/installing.md) has the Maven form, the
optional modules and the snapshots of `master`:

```groovy
dependencies {
    implementation platform('dev.goldberry:goldberry-bom:2026.2')
    implementation 'dev.goldberry:goldberry'
    runtimeOnly 'dev.goldberry:goldberry-natives::linux-x64'   // linux-aarch64, macos-aarch64, windows-x64
}
```

A window is three lines:

```java
var window = Window.open("Hello", 960, 640);
window.onPaint(frame -> frame.fill(0xFF2E3440));
Goldberry.run();
```

[Your first Java application](book/src/getting-started/first-java-application.md)
goes on from there to a widget tree, a KDL document and a stylesheet.

## Testing an application

A test renders a widget tree to pixels with no display, drives it through the
real input router, and compares the picture with a golden image:

```java
@Test
void applyingSavesTheSettings() {
    try (var session = Offscreen.of(800, 600)
            .stylesheets(Controls.stylesheets(Theme.NORD_DARK))
            .session(new SettingsScreen(settings, actions))) {
        session.focus("name");
        session.type("Deploy Orc");
        session.click(session.byRole(Role.BUTTON, "Apply").orElseThrow());
        session.advance(Duration.ofMillis(300));   // the virtual clock, past the animation
        assertEquals(1, settings.saves());
        assertEquals(List.of(), session.overruns());
    }
}
```

Documents inflate against the real models, so a `press=` or a `bind=` that
names nothing fails the test. A virtual clock makes every frame the same on
every machine, and one set of golden images serves all platforms with a
tolerance that absorbs antialiasing. [Testing an
application](book/src/guide/testing.md) has the whole of it.

## Build from source

```sh
./gradlew build                                # every module, and libgoldberry for this machine
./gradlew build -Pgoldberry.skipNative=true    # Java only; tests that need the library skip
./gradlew run                                  # the showcase: a screen per chapter of the guide
```

The native build needs CMake 3.28 or newer, Ninja and a C/C++ toolchain;
`./gradlew :natives:checkToolchain` names what is missing.
[Building from source](book/src/contributing/building.md) has every build
property, and [Tests and gates](book/src/contributing/testing.md) the CI lanes.

## Documentation

| Where | What |
|---|---|
| [goldberry.dev/docs](https://goldberry.dev/docs/), from [`book/`](book/src/introduction.md) | The guide: overview, getting started, layout, components, performance, developer guide |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | The design, layer by layer |
| [`book/src/adr/`](book/src/adr/README.md) | The decision log: why each significant choice was made |
| [`book/src/status.md`](book/src/status.md) | What is built |
| [`book/src/TODO.md`](book/src/TODO.md) | What is not built, and why |

The book is [mdBook](https://rust-lang.github.io/mdBook/): `mdbook serve book`.

## License

[Apache License 2.0](LICENSE). Third-party software and assets are disclosed in
[THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) and [`licenses/`](licenses/),
and shipped inside every jar under `META-INF/`.
