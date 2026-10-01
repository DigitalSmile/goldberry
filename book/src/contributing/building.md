# Building from source

<p class="gb-lede">One command builds and tests every Java module and the native library for this machine, and one property leaves the native library out.</p>

## The JDK

The build needs a **JDK 25** toolchain. Gradle provisions one if it cannot find one, so the only hard requirement is a JDK that can run the Gradle wrapper.

```sh
./gradlew build
```

That compiles and tests every Java module. It also builds `libgoldberry` for this machine when the library is missing or out of date.

## The native superbuild

`libgoldberry` is one shared library per platform. A CMake superbuild statically links Blend2D, AsmJit, Yoga, HarfBuzz, SDL3, md4c and libwebp into it. Building it needs CMake 3.28 or newer, Ninja and a C/C++ toolchain.

Check for them first:

```sh
./gradlew :natives:checkToolchain
```

The task prints the absolute path and version of each tool it will use, and names the packages to install if anything is absent. On Linux it also names the desktop development headers SDL needs, in terms of `apt` or `dnf`. The reasoning behind that list is [ADR-0082](../adr/0082-a-preflight-check-that-cannot-fail-is-not-a-check.md).

The tools are searched for on the `PATH` first and then in the usual install directories. A Homebrew, MacPorts, `CMake.app` or `pip install --user` toolchain is found even from a Gradle daemon that an IDE started with a bare `PATH`. [ADR-0040](../adr/0040-find-the-native-tools-by-absolute-path.md) explains why that matters. To point at a tool somewhere else:

```sh
./gradlew build -Pgoldberry.cmake=/path/to/cmake    # also -Pgoldberry.ninja
```

> [!CAUTION]
> The first native build downloads about 330 MB. The superbuild clones its upstreams, and HarfBuzz and SDL3 are most of it. Git reports its progress as it goes, so a configure step that looks idle is a slow connection rather than a stuck build. [ADR-0038](../adr/0038-the-superbuild-download-is-not-a-hang.md) records the time this cost before the progress meter was turned on.

The clones land in `natives/.deps/<target>`, outside `build/`, so `./gradlew clean` does not throw them away. To discard them on purpose:

```sh
./gradlew :natives:cleanNativeDeps
```

Once the sources are present the build is about a minute of work. Every upstream is pinned in `gradle/libs.versions.toml`, and the superbuild reads that file itself, so a pin that moves reconfigures the build. That is [ADR-0035](../adr/0035-the-catalog-is-the-only-place-a-ref-lives.md).

## Java only

For a build with no native toolchain:

```sh
./gradlew build -Pgoldberry.skipNative=true
```

Tests that need real native code skip when no library is loadable, so this stays green. It verifies less: nothing that shapes text or paints a pixel runs. CI's Java jobs build this way, and the per-platform jobs run the rest against the library they built.

## The properties

Everything the build reads, in one place:

| Property | What it does |
|---|---|
| `-Pgoldberry.skipNative=true` | Builds no native library. Everything that needs a rasterizer skips |
| `-Dgoldberry.native.library=<path>` | Runs the tests against a library built somewhere else, and builds none |
| `-Dgoldberry.native.required=true` | Turns those skips into failures. CI's native legs pass it |
| `-Pgoldberry.allowDegradedPlatform=true` | Builds the library without the desktop-integration headers, on a machine that has none. The library it produces reports no capabilities |
| `-Pgoldberry.depsDir=<path>` | Where the superbuild's upstream checkouts live, instead of `natives/.deps/<target>` |
| `-Pgoldberry.cmake=<path>`, `-Pgoldberry.ninja=<path>` | A tool somewhere the search does not look. Taken at its word |
| `-Pgoldberry.nativeImage=true` | Weaves `@Bind` and `@Action` into the compiled classes, which is what a native image runs |
| `-Pgoldberry.lenient` | Turns Error Prone off. For triage only, never for a green build |
| `-Pgoldberry.nullaway=warn` | Demotes NullAway from error to warning, for a sweep |
| `-Pgoldberry.golden.update=true` | What `blessGoldens` passes: rewrites the reference images from what the code draws |
| `-Pgoldberry.example.frames=N`, `-Pgoldberry.example.size=WxH` | Runs the showcase for N frames, at a size, and exits |
| `-Pgoldberry.backend.videoDriver=dummy` | Runs the showcase against SDL's dummy driver, with no compositor |
| `-Pgoldberry.gpu.videoDriver=offscreen`, `-Pgoldberry.gpu.required=true` | The GPU tests' driver, and whether a missing device is a failure |

### A library built somewhere else

Released artifacts are built on native runners per platform, so a locally built library is for development only. A `.so` from a developer machine links against the host's glibc and has a higher floor than the published one. Only the container build in CI ships, and [ADR-0012](../adr/0012-native-ci-runners-with-a-pinned-glibc.md) says why.

To check a library built somewhere else, a CI artifact or a colleague's build, point the tests at it instead of building one:

```sh
./gradlew :natives:test \
  -Dgoldberry.native.library=/path/to/libgoldberry.so \
  -Dgoldberry.native.required=true
```

Supplying a library is also what tells the build not to build its own. Adding `goldberry.native.required=true` turns "no library, skip quietly" into a failure, which is how CI verifies that the artifact it just built loads. That is [ADR-0016](../adr/0016-verify-the-artifact-and-never-skip-the-check.md).

## Running the showcase

`:example` is an ordinary subproject that runs on the module path, which is what catches an unexported package or a wrong `--enable-native-access`. The record is [ADR-0023](../adr/0023-logging-and-the-example-as-a-subproject.md).

```sh
./gradlew :natives:cmakeBuild     # once, to build libgoldberry
./gradlew run
```

A window opens, maximized, with a menu bar, a bar that reports what start-up cost, and a gallery of seventeen screens. To paint a few frames and exit, which is what CI does under Xvfb:

```sh
./gradlew run -Pgoldberry.example.frames=3
```

To drive it without touching the real compositor:

```sh
./gradlew run -Pgoldberry.example.frames=3 -Pgoldberry.backend.videoDriver=dummy
```

> [!WARNING]
> `SDL_VIDEODRIVER=dummy` in the environment does not reliably reach the application. A Gradle `JavaExec` fork inherits the daemon's environment rather than the shell's. Use the property.

> [!CAUTION]
> GNOME Shell 46 segfaults in its own `wl_client_destroy` path when this client disconnects. An accidental real run on that desktop costs a session. [Status](../status.md) has the details.

## The native image

The showcase also builds as a GraalVM native image, one file with `libgoldberry` inside it. It needs a GraalVM and a C toolchain with the zlib development package:

```sh
./gradlew :example:nativeImage -Pgraalvm.home=/path/to/graalvm
./example/build/native/goldberry-showcase-linux-x64
```

[Native image](../native.md) has the whole recipe, the metadata, and what to do when the image fails at the last step with `cannot find -lz`.

## Platform notes

### macOS

AppKit has to be driven from the process's first thread, so any Goldberry application needs `-XstartOnFirstThread`. `./gradlew run` passes it for you. An application of your own passes it itself, as it would for LWJGL or SWT. Without it `SDL_Init` fails with "No available video device", which says nothing about threads. The record is [ADR-0039](../adr/0039-macos-needs-the-first-thread.md).

Only `macos-aarch64` is built. Intel Macs were dropped from the matrix in [ADR-0041](../adr/0041-three-platforms-four-artifacts-two-backends.md).

### Linux

SDL compiles its X11 and Wayland drivers in only where the development headers are present, and several of the X11 ones are hard stops: the configure fails at the first one it cannot find. `checkToolchain` names every package before CMake runs. The table it reads is `LinuxDependencies` in build-logic, and a test holds it to what CI installs.

SDL's D-Bus, IBus and udev support is different. It compiles out silently, into calls that succeed and answer nothing. The superbuild probes for those headers and stops rather than shipping a library that cannot ask the desktop anything. On a machine that has none:

```sh
./gradlew build -Pgoldberry.allowDegradedPlatform=true
```

The library that produces reports no capabilities, so an application can tell "this desktop has no such setting" from "this build cannot ask". That is [ADR-0325](../adr/0325-a-build-says-what-it-can-ask-the-desktop.md).

### Windows

`libgoldberry` on Windows is built with MSVC. Open an "x64 Native Tools Command Prompt for VS 2022", or run `vcvars64.bat`, and build from that shell, because `cl.exe` is on the `PATH` only there. The build does not leave the compiler to a search: `checkToolchain` resolves `cl.exe` the way it resolves `cmake` and refuses when it is absent. It has to, because a MinGW `cc.exe` on a stock runner configures without complaint and writes a `libgoldberry.dll` that depends on `libstdc++-6.dll`. The library an MSVC build writes is `goldberry.dll`, which is what `goldberry-natives` ships.

Every CI step on Windows runs in bash, so `./gradlew` means the same script on all three runners and `-Pgoldberry.skipNative=true` is not split at the dot by PowerShell.

## The IDE

Import the root project. `:example` is a subproject, so the IDE sees it with the rest and `./gradlew run` is a run configuration like any other. Keep the showcase on the module path: an unexported package and a wrong module name fail there and nowhere else.

A Gradle daemon that an IDE started carries the environment the IDE was launched with, which on macOS is launchd's four directories and no Homebrew. The build no longer cares. Every native tool is resolved to an absolute path once, and both the check and the use run that path. If `checkToolchain` prints a tool you did not expect, `-Pgoldberry.cmake` overrides the search.

> [!NOTE]
> `build-logic` is an included build with tests of its own. `./gradlew :build-logic:test` runs them, and `:natives:check` runs them as part of the ordinary build.
