# ADR-0083: On GNOME/Wayland, libdecor is not a fallback

- **Status:** Accepted
- **Date:** 2026-08-18
- **Relates to:** `docs/ARCHITECTURE.md` §3, §15; [ADR-0003](0003-sdl3-as-the-only-desktop-backend.md), [ADR-0012](0012-native-ci-runners-with-a-pinned-glibc.md), [ADR-0082](0082-a-preflight-check-that-cannot-fail-is-not-a-check.md)
- **Follows:** [ADR-0082](0082-a-preflight-check-that-cannot-fail-is-not-a-check.md) (the dependency it adds is one that check could not have caught, for a new reason)

## Context

A window opened on real hardware — Ubuntu, GNOME, a Wayland session — had no
titlebar, no minimize or close button, and could not be resized. Nothing in the
build log or the run log mentioned it. The Java side was innocent:
`WindowSpec.of` is documented as "a resizable, server-side-decorated window",
`Sdl3Backend.createWindow` adds `SDL_WINDOW_RESIZABLE` and withholds
`SDL_WINDOW_BORDERLESS` exactly as it should, and SDL accepted both.

The cause was in the generated `SDL_build_config.h`:

```c
/* #undef HAVE_LIBDECOR_H */
#define SDL_VIDEO_DRIVER_WAYLAND 1
```

Wayland has no decoration protocol of its own. A toplevel is decorated one of two
ways: the compositor draws them, negotiated through
`zxdg_decoration_manager_v1`, or the client draws them itself. **GNOME's Mutter
declines to draw them** — a long-standing and deliberate position — so on GNOME
the second way is the only way, and SDL's implementation of the second way *is*
libdecor. Every use of it in `SDL_waylandwindow.c` sits behind
`#ifdef HAVE_LIBDECOR_H`, and that macro is set only if `libdecor-0.pc` was
present when SDL was configured.

Without it SDL builds a complete, working Wayland driver that opens an
undecorated toplevel — which also explains the second symptom, because on Wayland
a resize is a client-initiated `xdg_toplevel.resize` and the thing that decides a
pointer is on a resize edge is the decoration. No decoration, no edge, no resize.
One missing header, both symptoms.

Two things made this land now rather than earlier.

**It was uncovered by [ADR-0082](0082-a-preflight-check-that-cannot-fail-is-not-a-check.md).**
That record added `egl` to the dependency table, which is what turned
`SDL_VIDEO_DRIVER_WAYLAND` on. Before it, this machine's builds had no Wayland
driver at all, SDL fell through to X11 under XWayland, and Mutter decorated the
X11 window normally. Fixing the Wayland backend is what made the missing
decorations visible. The bug was always there; nobody could see it.

**It is a third failure mode, which ADR-0082's table had no way to express.**
That record split dependencies into `HARD_STOP` (SDL refuses to configure) and
`NEEDED` (SDL drops a whole backend silently). libdecor is neither: the backend
is built, initializes, opens a window, pumps events and paints. What is missing
is a *capability of a window that otherwise works*. Nothing logs it, nothing
warns, and `SDL_GetWindowFlags` still reports the window as bordered — SDL asked
for a bordered window and does not know it did not get one.

## Decision

**Add `libdecor-0` to `LinuxDependencies` as `NEEDED`, so a build without it
fails in `checkToolchain` with the package name, and install `libdecor-0-dev` in
the workflows that build through Gradle.**

`NEEDED` rather than a new necessity of its own. The category means "SDL will not
tell you, so we will", and that is exactly right here; what differs is the size
of what is lost, not who reports it. A fourth value would split the table on a
distinction that changes no behaviour.

Rejecting the alternative explicitly: **Goldberry does not draw its own
decorations, and this record does not start.** `SdlWindowFlag.BORDERLESS` is
documented as "client-side decorations are drawn by Goldberry on top of this",
which describes a design that does not exist yet and is not what a default window
uses. Nothing here commits to building it.

## Alternatives considered

- **Prefer X11 on GNOME**, by narrowing `PREFERRED_LINUX_DRIVERS` from
  `wayland,x11`. Rejected: it trades a missing titlebar for XWayland's blurry
  fractional scaling, which is precisely what `SDL_WINDOW_HIGH_PIXEL_DENSITY` and
  the whole fractional-DPI path exist to avoid. It also fixes GNOME by punishing
  every compositor that does decorate properly.
- **Draw the decorations in Goldberry**, using `BORDERLESS` as the doc comment
  imagines. A genuine long-term option — it is the only way to get a titlebar
  that matches the toolkit's own design system, and it is what GTK and Qt both
  do. Rejected *now* because it is a feature, not a fix: it means window-move and
  eight-way-resize hit testing, a shadow, a maximize/snap protocol, and a
  titlebar widget, on a milestone ladder that has not reached window chrome. A
  package that already does it correctly is available today.
- **Ship a bundled libdecor in the superbuild**, alongside Blend2D and the rest.
  Rejected: SDL `dlopen`s libdecor by soname at run time rather than linking it,
  so vendoring it would mean shipping and installing a shared library for SDL to
  find — a distribution problem, not a build one. The headers are all the build
  needs.
- **Detect it at run time and warn**, by exporting the compiled-in value of
  `SDL_HAVE_LIBDECOR` through the shim and logging when the Wayland driver starts
  without it. Not rejected — deferred. It is the only thing that helps someone
  running a *published* jar, who never runs `checkToolchain` at all, and it is
  listed as an open question rather than half-built here.

## Consequences

- A machine without `libdecor-0-dev` now fails `checkToolchain` in a second,
  naming the package, instead of building a window nobody can close.
- **Installing the package is not enough on its own.** CMake caches
  `HAVE_LIBDECOR_H` in `CMakeCache.txt`, and Gradle sees no changed input, so a
  plain rebuild after `apt install` silently keeps the old answer. The configure
  directory has to be discarded. This is a sharp edge and this record does not
  remove it — see the open question below.
- **The published Linux artifacts are not covered.** `linux.yml` builds in a
  manylinux AlmaLinux 8 container, which runs CMake directly with no JDK and so
  never runs `checkToolchain`; whether `libdecor-devel` even exists in its
  repositories is unverified. The drift guard in `LinuxDependenciesTest` only
  holds that workflow to `HARD_STOP` rows, so it will not catch this — by design,
  since a guard that fails on a package that cannot be installed is a guard that
  gets deleted. Tracked in `book/src/status.md` together with ADR-0082's
  unanswered question about `mesa-libEGL-devel`, because they are the same
  question: what the release container actually compiles into its Wayland driver.
- The table now describes three genuinely different failure modes with two
  necessity values, and `libdecor-0` is the row where the naming strains. Worth
  re-reading if a fourth case turns up.
