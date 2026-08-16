# ADR-0055: SDL owns keyboard translation, so libxkbcommon goes

- **Status:** Accepted
- **Date:** 2026-08-16
- **Relates to:** `docs/ARCHITECTURE.md` §3, §7.1, §15; [ADR-0008](0008-superbuild-before-the-vertical-slice.md), [ADR-0010](0010-hand-written-ffm-bindings.md), [ADR-0015](0015-licensing-and-third-party-disclosure.md), [ADR-0035](0035-the-catalog-is-the-only-place-a-ref-lives.md), [ADR-0040](0040-find-the-native-tools-by-absolute-path.md)
- **Amends:** [ADR-0008](0008-superbuild-before-the-vertical-slice.md) (drops one of the five pinned upstreams)

## Context

§7.1 specified that on Linux "the translation is libxkbcommon (`xkb_state` +
`xkb_compose` for dead keys)", and the superbuild has built and pinned
libxkbcommon since [ADR-0008](0008-superbuild-before-the-vertical-slice.md).
When keyboard input was actually implemented, it was built on SDL's
`SDL_EVENT_TEXT_INPUT` instead — which already carries the layout, dead keys,
compose sequences and IME conversion, applied by the platform.

That raised the question of whether libxkbcommon was needed at all. It is not,
and it turned out it had never been used.

## What was measured

Against the built `libgoldberry.so`:

| Check | Result |
|---|---|
| xkb symbols in `goldberry.symbols` | 0 — the section header had nothing under it |
| Java code binding xkb | none |
| Exported xkb symbols | 0 |
| *Undefined* xkb symbols (dynamic linkage) | 0 |
| `DT_NEEDED` for libxkbcommon | absent |
| String `libxkbcommon.so.0` present | **yes** |

The superbuild did link it —
`target_link_libraries(goldberry PRIVATE .../libxkbcommon.a)` — but a static
archive contributes only the objects that resolve an undefined symbol, and
nothing referenced one. All 1.7 MB was discarded at link time. The surviving
string is SDL's own `dlopen` name: SDL loads the **system** libxkbcommon when it
needs a keymap, and always has.

So the library was being cloned, configured, built and linked on every Linux
build, and thrown away by the linker every time.

## Decision

**Remove libxkbcommon from the superbuild, and let SDL own keyboard
translation.**

SDL already does it on all three platforms — libxkbcommon on Linux, the
platform's own translation on Windows and macOS — and delivers the result as
committed text. Binding it a second time would duplicate work SDL does
correctly, in one place, for one platform.

§7.1's split survives intact and is the reason this is comfortable: `KeyEvent`
and `TextEvent` are still separate, because one character can take several keys.
What changes is only *who* performs the translation, not that it is performed.

**Meson goes with it.** libxkbcommon was the only meson-built dependency, and so
the only reason `checkToolchain` demanded Meson ≥ 1.4. That floor was not
theoretical: Ubuntu 24.04 ships 1.3.2, it broke CI, and it cost a toolchain
upgrade to work around before this was understood. The superbuild's tools are now
CMake and Ninja.

**The headers stay, for SDL.** `libxkbcommon-dev` and `xkb-data` are still
checked and still installed in CI — SDL's Wayland backend needs the headers to
build and the keymap data to run. What changed is the reason: they are SDL's
dependency, not Goldberry's, and the toolchain check now says so.

## Alternatives considered

- **Link it properly and bind it, as §7.1 originally described.** It is a real
  design — owning the translation would give tighter control over IME preedit
  later. Rejected: it duplicates what SDL does on one of three platforms, and
  §7.1's stated goal is the key/text split, which SDL's events already provide.
  If preedit ever needs more than SDL exposes, SDL's own IME API is the next
  place to look, not a parallel xkb stack.
- **Keep building it in case it is wanted later.** Rejected: it was dead weight
  with a live cost — a git clone, a meson build, and a version floor on a tool
  nothing else needed.
- **Configure SDL to link its dependencies statically** so the artifact carries
  its own libxkbcommon. Rejected here as a separate question: it applies equally
  to libwayland, libGL and a dozen others SDL `dlopen`s, and deciding it for one
  of them would be arbitrary.

## Consequences

- **The shipped Linux artifact depends on the system libxkbcommon at run time.**
  It already did — the static copy was never linked — so nothing changes for
  anyone. It is now stated rather than accidentally true.
- **The superbuild has four pinned upstreams, not five.** `libs.versions.toml`
  loses its `xkbcommon` entry, and `GOLDBERRY_XKBCOMMON_REF` and
  `GOLDBERRY_MESON` are gone from the CMake surface
  ([ADR-0035](0035-the-catalog-is-the-only-place-a-ref-lives.md) still holds for
  the rest).
- **The licence disclosure drops an entry.** libxkbcommon is no longer
  redistributed in object form, so it leaves `THIRD-PARTY-NOTICES.md`, `NOTICE`
  and `licenses/` ([ADR-0015](0015-licensing-and-third-party-disclosure.md)).
  `checkLicenses` verifies the two sides still agree.
- **Verified by rebuilding from scratch with meson absent from `PATH`.** The
  native build, all 950 tests and the packaged showcase all pass, and the
  resulting `libgoldberry.so` contains zero xkb symbols — which it also did
  before, which was the whole point.
- **§7.1 and the CMake comment described a design that was never built.** Both
  are corrected. The lesson is narrower than "check your dependencies": a static
  archive that nothing references links silently and successfully, and the only
  way to notice is to look at the symbols.
