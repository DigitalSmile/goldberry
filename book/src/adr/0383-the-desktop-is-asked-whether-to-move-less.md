# 383. The desktop is asked whether to move less

Date: 2026-09-17

## Status

Accepted. Closes `book/src/TODO.md`'s "Reduced motion is obeyed but not
detected"; the density half of that entry is answered separately below.

## Context

§13 names four accessibility switches and the toolkit obeys all four. Three are
the application's to set and one is not: **reduce motion is a desktop setting**,
and `renderer.reducedMotion(true)` was the only way in — so every application
had to find the answer for itself, on three platforms, which is exactly the work
a toolkit exists to have done once.

ADR-0322 answered the *other* desktop preference with one SDL call and was glad
not to write three platform integrations for a boolean. There is no
`SDL_GetReducedMotion`, so this is those three integrations — taken because the
alternative is every application writing them.

## Decision

**Ask each platform directly, through FFM, against a library the process already
has; cache the answer; obey it.**

| | asked | of |
|---|---|---|
| Linux | `org.freedesktop.portal.Settings.Read` | the XDG portal, over libdbus |
| Windows | `SystemParametersInfoW(SPI_GETCLIENTAREAANIMATION)` | `user32.dll` |
| macOS | `NSWorkspace.accessibilityDisplayShouldReduceMotion` | `libobjc` |

- **No native build.** Nothing is compiled, nothing is added to the superbuild
  and nothing new ships: each library is `dlopen`ed by name at run time, which is
  what SDL itself does with D-Bus.
- Linux asks the specified key first — `org.freedesktop.appearance`'s
  `reduced-motion`, a `uint32` whose 0 means "no preference" — and falls back to
  GNOME's `org.gnome.desktop.interface`/`enable-animations`, which is the same
  question the other way round.
- `dbus_message_append_args` is variadic and is not used: the iterator API says
  the same thing with fixed signatures. The reply is a variant wrapping a variant
  on every portal implementation tried, so the reader unwraps until it finds a
  value.
- **Three answers, not two.** `UNKNOWN` is what a missing library, a missing
  portal, a missing key, a refused call and an unexpected type all produce, and
  it is not an instruction: a renderer built from it animates normally.
- `Backend.reducedMotion()`, `Window.reducedMotion()` and `Host.reducedMotion()`
  carry it, and **`Launcher` applies it** — unlike the theme, which the toolkit
  deliberately does not act on. A colour scheme is a matter of taste; an
  accessibility preference is not.
- `-Dgoldberry.motion.reduced=reduce|full` overrides it, for a test, a
  screenshot, and a desktop whose answer is wrong.
- The holders live in a package that is **not exported**, which is ADR-0173's
  rule and what `ExportedSurfaceTest` enforces: what leaves `:natives` is an enum
  with three constants in it.

## Consequences

- Asked **once** per process, and a change mid-session is not obeyed until the
  application restarts. Listening means a D-Bus main loop on Linux and an
  observer on each of the others — a much larger thing than one 200ms read, and
  worth its own decision if anybody asks for it.
- The Linux path is verified against a real portal on this machine, in all four
  of its outcomes: a `uint32` preference, a `uint32` zero, a GNOME boolean and a
  namespace nobody serves. The Windows and macOS paths are written from their
  documented APIs and are exercised by CI on those runners; each of them fails
  to `UNKNOWN`, which is the behaviour the toolkit had before this existed.
- **Density is answered, not built.** The TODO entry pairs the two, and they are
  not alike: no desktop has a density setting to read — not GNOME, not Windows,
  not macOS — and §1.3's compact mode is an application's own choice about its
  screens. There is nothing to detect.
