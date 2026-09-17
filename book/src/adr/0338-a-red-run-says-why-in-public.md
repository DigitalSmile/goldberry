# 338. A red run says why, in public

Date: 2026-09-17

## Status

Accepted. Repairs CI after a month red; amends ADR-0334's per-OS jobs, testing.md §3's
coverage gates and §4's nightly lane. Amended the same day with the four causes the
runners' own logs named, read through the GitHub MCP server once it was reachable.

## Context

No CI workflow had passed since 2026-08-16. `linux.yml`, `macos.yml` and
`windows.yml` all failed at the `java` job, the nightly at "Build libgoldberry",
and after ADR-0334 `snapshot.yml` failed at the same places through `publish.yml`.

Nobody could see why from outside. A job log cannot be read without signing in, so
every failure said "Process completed with exit code 1" and nothing else. The
showcase workflow's comments show the cost: its Windows image "has failed twice ...
so the only diagnosis available was inference". A check run's **annotations**, on
the other hand, are served by the public API.

Rebuilding the `java` job from a fresh clone, with no build cache and no native
library, turned up five separate causes:

1. **`TestFont.get()` (`:widgets`) and `TestFonts.get()` (`:html`) stored the font
   book before the probe parse that throws.** The first caller skipped and every
   later one got a book that could not open a face. About 280 widget tests failed
   instead of skipping. Locally a library was always built, so this never showed.
2. **Tests that reach libgoldberry without asking first:** `MaximizedStateTest`
   (a headless window still paints into Blend2D), `MarkdownHtmlTest`, whose
   `@BeforeAll` touched nothing native, and one `ShowcaseDocumentsTest` case that
   parses Markdown. `MarkdownPropertyTest` is jqwik, and jqwik reports an abort
   from `@BeforeContainer` as an error rather than a skip.
3. **The coverage floors (2026-08-30) were measured with the library loaded.**
   Under `-Pgoldberry.skipNative` every test that shapes, paints or parses skips,
   and `:core`/`:widgets`/`:html` fall to 56/39/57% of lines. The `java` job
   could never pass them, and nothing else ran them.
4. **`GoldberryTest` still asked for a semver `x.y.z`** after ADR-0333 made the
   version `2026.1-SNAPSHOT`. This broke the `java` job and every verify job.
5. **`nightly.yml` installed no system packages** before `:natives:cmakeBuild`.
   Since ADR-0325, `checkToolchain` refuses to continue without D-Bus, IBus and
   udev, and the job never had X11 or Wayland headers either.

## Decision

**Failures are written as annotations.** A new `build.ci` package in build-logic:

- `WorkflowCommand` renders `::error title=…::message` with the runner's escaping
  rules (`%`, CR, LF; plus `:` and `,` in a property) and caps the body.
- `TestFailureAnnotations`, a `TestListener` that `goldberry.java-conventions`
  adds to every `Test` task, writes one annotation per failed test: the task path,
  the class, the test name, then the cause chain and the top frames.
- `BuildFailureAnnotation`, a flow action registered once per build, writes
  "What went wrong" for failures no test reports: jlink, native-image, CMake.

All of them run only where `GITHUB_ACTIONS=true`. The flow action is registered
from the conventions plugin, guarded by a flag on `gradle`, and not by a plugin on
the root project. Applying build-logic at the root puts its classpath under every
module, and `:core`'s versioned `me.champeau.jmh` request then fails to resolve.

**Test fixtures skip only after the probe succeeds**, and every test that
reaches the library asks first: `RendererRequirement` in `:core` and `:example`,
`MarkdownRequirement` in `:html`. For jqwik, `MarkdownAvailable` is a
`SkipExecutionHook`, which is jqwik's own way to skip.

**Coverage floors run only when the library is part of the build**
(`onlyIf { !skipNative }`), and the linux-x64 verify leg runs them against the
manylinux library it just verified. One leg is enough, because coverage does not
vary by CPU. The nightly coverage job stays a report.

**`nightly.yml` installs the same apt list as `showcase.yml`**, and
`LinuxDependenciesTest` now holds both files to the dependency table.

### The four the runners named

Read from the job logs of run `35188823461` (Snapshot), `35188822932` (Showcase) and
`35076016742` (Nightly), on 2026-09-17, once the GitHub MCP server made them readable.
None of them can run on the machine this is written on.

6. **Windows `:natives:test`: seven structural tests, one path.** `ExportedSurfaceTest`
   and `HolderShapeTest` each found the module's classes with
   `Path.of(codeSource.getLocation().getPath())`. On Windows that location is
   `file:/D:/a/goldberry/.../classes/java/test/`, `getPath()` keeps the leading slash,
   and `Path.of` refuses `/D:/...` with `Illegal char <:> at index 3`. Both now go
   through `CompiledClasses`, one helper in the test tree, which converts the URI —
   the form that knows about drive letters — and is tested on its own.
7. **Windows `:build-logic:test`: CRLF.** `GraalVmReleaseTest` cut `showcase.yml` at
   its first blank line, `indexOf("\n\n")`; a Windows runner checks out with
   `core.autocrlf=true`, no such sequence exists, and the test died of a
   `StringIndexOutOfBoundsException` rather than a message. `Repository.read`
   normalises what it hands out to LF, tested, and a `.gitattributes` keeps every
   working tree at LF — the drift guards are about content, and endings are not
   content.
8. **The Windows showcase built libgoldberry with MinGW.** `:natives:cmakeBuild`
   hands CMake the Ninja generator and no compiler, and the first `cc` on a
   `windows-2022` runner's PATH is `C:\mingw64\bin\cc.exe`: `cl` is only on the PATH
   inside a Developer Command Prompt. GNU 14.2.0 configured without a word, linked
   `libgoldberry.dll` — GNU naming, and a `libstdc++-6.dll` dependency — and
   `showcaseImage` failed looking for the `goldberry.dll` that MSVC writes and
   `goldberry-natives` ships. `windows.yml` never met this because it drives CMake
   itself with the Visual Studio generator. Now `WindowsToolchain` in build-logic
   names the compiler: `checkToolchain` resolves `cl` the way it resolves `cmake` and
   refuses without it, naming the compiler CMake would have taken instead;
   `cmakeConfigure` passes the path it found as `CMAKE_C_COMPILER` and
   `CMAKE_CXX_COMPILER`; and `showcase.yml` runs `ilammy/msvc-dev-cmd@v1` before the
   build, held there by a test.
9. **The macOS trace aborted inside CoreGraphics.** The jlink image ran to three
   frames on the same runner, under Cocoa. `nativeImageMetadata` ran the same showcase
   under `videoDriver=dummy` and died with
   `Assertion failed: (CGAtomicGet(&is_initialized)), function CGSConnectionByID` and
   no Java frame. SDL's macOS tray is Cocoa's status bar, and `SDL_CreateTray` calls
   `[NSStatusBar systemStatusBar]` *before* `[NSApplication sharedApplication]`;
   under the Cocoa driver `SDL_Init` had already created the application, under
   `dummy` nothing had, and the status bar's first call into the window server is
   the assertion. Two changes, because one would not do: `TrayAvailability` in the
   sdl3 backend declines a tray on macOS under `dummy` so the process is never
   aborted for asking; and the macOS trace runs under `cocoa`
   (`-Pgoldberry.trace.videoDriver`, `TraceVideoDriver` in build-logic), because a
   trace recorded with the guard in force would hold no tray call and the image built
   from it would meet its first one on a user's desktop. The runner has a window
   server, as the jlink run proved.

## Consequences

- Reproduced locally in a fresh clone, with no build cache and no library: the
  `java` job's three steps pass. Then, against a local libgoldberry:
  `:natives:test` and `:core/:widgets/:html:test` pass with all three coverage
  floors.
- **Diagnosed from the logs, fixed blind:** Windows `:natives:test`, Windows
  `:build-logic:test`, the showcase's Windows image and the macOS trace (6–9 above).
  Each fix is unit-tested where it has logic, but no machine here runs the platform
  it is for, so the next Snapshot and Showcase runs are the verification. What is
  still an inference is the MSVC + Ninja configure of the whole superbuild, which
  `windows.yml` has only ever done through the Visual Studio generator.
- The Showcase's two publish jobs failed only for want of the macOS and Windows
  artifacts, and need nothing of their own.
- A PR's `java` job no longer enforces the coverage floors; the verify job does.
  A drop in coverage now turns the verify job red, not the `java` job.
- The runner keeps ten error annotations per step and fifty per job. A step with
  hundreds of failing tests reports only the first ten, which is enough to name
  the cause.
