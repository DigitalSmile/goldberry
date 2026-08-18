# ADR-0082: A preflight check that cannot fail is not a check

- **Status:** Accepted
- **Date:** 2026-08-18
- **Relates to:** `docs/ARCHITECTURE.md` §15; [ADR-0008](0008-superbuild-before-the-vertical-slice.md), [ADR-0012](0012-native-ci-runners-with-a-pinned-glibc.md), [ADR-0040](0040-find-the-native-tools-by-absolute-path.md), [ADR-0055](0055-sdl-owns-keyboard-translation.md)

## Context

`:natives:checkToolchain` exists so that a missing Linux development package is
reported in one second, in terms of `apt`, instead of two minutes into a CMake
configure in terms of a CMake option. It did not do that. A local build failed
with:

```
CMake Error at .../sdl3-src/cmake/macros.cmake:433 (message):
  Couldn't find dependency package for XSCRNSAVER.  Please install the needed
  packages or configure with -DSDL_X11_XSCRNSAVER=OFF
```

after `checkToolchain` had printed `Native toolchain OK` and not so much as
warned.

Three separate things were wrong, and each one alone would have been enough.

**The module name did not exist.** The table probed `pkg-config --exists xss`.
No distribution ships an `xss.pc`; the module is `xscrnsaver`, which is also what
SDL asks for (`cmake/sdlchecks.cmake`, `set(Xss_PKG_CONFIG_SPEC xscrnsaver)`).
Debian's `libxss-dev` and RHEL's `libXScrnSaver-devel` both install
`xscrnsaver.pc` and neither installs `xss.pc`. The probe therefore returned
"absent" on every machine ever, whether the package was installed or not — a row
that cannot distinguish the two states is not measuring anything.

**The row was marked optional.** SDL's X11 driver does not degrade. Every
`SDL_X11_*` sub-feature is `dep_option(... ON ...)` and every one ends in
`SDL_missing_dependency`, which is a `FATAL_ERROR`. There is no build without
XScrnSaver; there is only no build. Because the row was optional, even a probe
that worked would have printed a warning and let the build proceed to fail.

**XTest was not in the table at all.** It is the very next hard stop after
XScrnSaver, in that order, so installing one package moves the error down one
line and no further.

The uncomfortable part is that none of this was unknown. Both CI workflows
already install `libxss-dev`/`libXScrnSaver-devel` and `libxtst-dev`/
`libXtst-devel`, and both carry comments explaining that SDL treats them as hard
dependencies — comments written by whoever hit this in CI, twice, once per
package. `linux.yml` says so in as many words: *"They come in that order, so
adding the first only moved the failure one line down — which is what happened."*
The knowledge existed in three places and reached the check in none of them. The
table and CI are two statements of the same fact, and nothing made them agree.

## Decision

**Move the dependency table out of `natives/build.gradle` into
`LinuxDependencies` in build-logic, and unit-test it against what CI installs.**

The table gains a `Necessity` — `HARD_STOP`, `NEEDED` or `OPTIONAL` — in place of
a boolean, because the three cases are genuinely different and the difference is
what the failure message needs to say:

| Necessity | What SDL does | What the check does |
|---|---|---|
| `HARD_STOP` | Stops the configure with `SDL_missing_dependency` | Fails, and says the configure will stop |
| `NEEDED` | Drops a backend **silently** and configures successfully | Fails |
| `OPTIONAL` | Builds without a feature Goldberry does not use | Warns |

`NEEDED` covers SDL's Wayland check, which is one `pkg_check_modules` over five
specs: lose any one and the Wayland driver is not compiled in, the configure
succeeds, and the first symptom is a user on Wayland with no window. That is
worth failing on here *because* SDL will not fail on it. It also added a row that
was missing for the same reason `xtst` was: `egl`, the one spec in that list
nothing else pulls in.

`LinuxDependenciesTest` then asserts the invariant that actually broke:

- every required package is installed by the workflows that run `checkToolchain`
  through Gradle (`example.yml`, `showcase.yml`), or CI would fail its own
  preflight;
- every `HARD_STOP` package is installed by `linux.yml`, which runs CMake
  directly inside the manylinux container with no JDK — so `checkToolchain` never
  runs there and this list is the only thing between it and SDL's `FATAL_ERROR`;
- no row uses `xss`, named as a regression rather than left to the sweep.

This is the same move ADR-0040 made for `ToolResolver`, for the same reason: the
logic is real, the bug is one nobody reproduces by reading, and a Groovy literal
in a build script has nowhere to put a test.

## Alternatives considered

- **Configure with `-DSDL_X11_XSCRNSAVER=OFF`**, as the CMake error suggests.
  Rejected, and both CI workflows had already rejected it in comments: XScrnSaver
  is how SDL keeps a screensaver off a window that is playing something, and
  XTest is how it warps the pointer on X11 — which §7.3's drag-to-resize and
  slider behaviour want. Switching them off removes a capability silently, on one
  platform only, and makes a developer's local library differ in behaviour from
  the published one. A build that stops and names a package is better than a
  library that is quietly less capable.
- **Fix the two rows in place and leave the table in Groovy.** Rejected: it
  repairs the instance and not the mechanism. The table drifted from CI once
  already, in a direction nobody could see, and the next dependency SDL turns
  into a hard stop will drift it again.
- **Derive the table from SDL's CMake at configure time**, parsing `dep_option`
  and `SDL_missing_dependency` out of the vendored sources. Rejected: it needs
  the sources cloned, which is the 330 MB step this check runs *before*, and it
  still could not produce the distribution package names — which is the half of
  the mapping that makes the message actionable.
- **Have CI generate its install list from the table.** Attractive, and a real
  option later. Rejected for now because the workflows install more than the
  superbuild needs — `xvfb`, `wayland-protocols`, the pip toolchain — so the
  generated part would be a fragment spliced into a hand-written command, and a
  test asserting the two agree buys most of the benefit for none of the
  machinery.

## Consequences

- A missing header now fails in under a second with the exact command:

  ```
  Missing development headers the superbuild needs:
    xscrnsaver -- SDL3 screensaver inhibition (SDL stops the configure without it)
    xtst -- SDL3 pointer warping on X11 (SDL stops the configure without it)
    egl -- SDL3 Wayland backend

  sudo apt install libegl1-mesa-dev libxss-dev libxtst-dev
  ```

- **Some machines that used to configure will now be refused.** `egl` was never
  checked, so a machine without `libegl1-mesa-dev` previously produced a library
  with no Wayland backend and no indication of it. Those builds now stop. This is
  the intended cost: the alternative is shipping a Linux toolkit that silently
  does not run on Wayland.
- Adding a dependency now means editing Java in a second build and possibly a
  workflow, rather than one line in a build script. The test says which workflow.
- The drift guard reads `.github/workflows` from a unit test, which couples
  build-logic's tests to the repository layout. `build-logic/build.gradle` passes
  `-Dgoldberry.repoRoot`; the test falls back to walking up from the working
  directory so an IDE run still works, and throws rather than skipping when it
  finds nothing — a guard that skips when it cannot find what it guards is a
  green tick over an unchecked invariant.
- **`linux.yml` is only guarded for hard stops, not for `NEEDED`.** It installs
  no `mesa-libEGL-devel` and no `xkeyboard-config`, so whether the published
  manylinux artifacts carry SDL's Wayland driver is an open question this record
  does not answer — it needs a look at the container, not at this table. It is
  listed under the open questions in `book/src/status.md`.
