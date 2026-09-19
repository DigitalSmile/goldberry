# 422. What SDL compiled is the fact, and decorations are a capability

Date: 2026-09-19

## Status

Accepted. Finishes the half of `docs/gaps.md` G32 that
[ADR-0325](0325-a-build-says-what-it-can-ask-the-desktop.md) left, and answers the
standing question behind
[ADR-0083](0083-on-gnome-wayland-libdecor-is-not-a-fallback.md) and
[ADR-0084](0084-the-gtk-plugin-cannot-decorate-a-jvms-window.md) with a report
rather than a fix.

## Context

`book/src/TODO.md` asked "what does the release container actually compile into
its Wayland driver?" and answered its own question halfway:

> Measured in `quay.io/pypa/manylinux_2_28_x86_64`: `dbus-devel`,
> `systemd-devel`, `ibus-devel` and `mesa-libEGL-devel` all install and all
> provide their `.pc` files; `libdecor-devel` and `xkeyboard-config` are **in no
> repository the container has** … Extending both to `SDL_VIDEO_DRIVER_WAYLAND`
> and `HAVE_LIBDECOR_H` is the remaining work, and the honest form of it is
> probably a `Capability.WINDOW_DECORATIONS`, since the answer for the container
> may be "it cannot" rather than "install this".

Two facts decide the shape of this, and they point in opposite directions from the
three integrations ADR-0325 built.

**A prediction of SDL's Wayland check cannot be made faithfully.** ADR-0325's
pattern is a `pkg_search_module` probe that predicts what SDL's CMake will find,
followed by a cross-check against the `SDL_build_config.h` SDL then generates; a
prediction that says "present" where SDL says "absent" is a fatal error, because
that is a library about to claim a capability it does not have. It works because
each of those three is one `pkg_check_modules` over one module set.

`SDL_VIDEO_DRIVER_WAYLAND` is not. SDL's `CheckWayland` is a single
`pkg_check_modules` over **five** specs — `wayland-client`, `wayland-scanner`,
`wayland-egl`, `wayland-cursor`, `egl` — and it additionally needs
`wayland-protocols` and the `wayland-scanner` *binary*. Lose any one and the whole
driver is dropped silently. The existing function's own comment already warns
about the direction of error that matters for a search: *"a probe that asks for
fewer names than SDL does would report absent on a machine SDL builds fine on"*.
Under the all-of semantics Wayland needs, the error inverts and gets worse — a
probe listing four of the five reports **present** on a machine where SDL builds
no driver, and the cross-check then fails a build that was fine.

**Neither package is installable everywhere.** `libdecor-devel` is in no
repository the manylinux release container has. A `REQUIRED` probe would make the
release container unbuildable, which is the entry's own point: for that build the
honest answer is "it cannot".

## Decision

**Two new capabilities, and their source of truth is SDL's generated header
rather than a probe.**

```cmake
foreach(_sdl_capability IN ITEMS SDL_VIDEO_DRIVER_WAYLAND HAVE_LIBDECOR_H)
    if(_sdl_build_config_text MATCHES "\n#define ${_sdl_capability}")
        list(APPEND _definitions GOLDBERRY_PLATFORM_${_sdl_capability})
    else()
        message(WARNING ...)
    endif()
endforeach()
```

`Capability.WAYLAND` and `Capability.WINDOW_DECORATIONS`, bits `0x40` and `0x20`,
reported through `Goldberry.capabilities()` like the other five.

Four things about the shape.

**There is nothing to cross-check, and that is the improvement.** The three
integrations have a prediction *and* an answer, and the gap between them is the
error ADR-0325 exists to catch. These two have only the answer. That is strictly
better where it is available — the reason ADR-0325 kept the prediction is that it
is what produces a message naming the package to install, and here the warning
names those packages directly.

**A warning, not a fatal error.** The release container cannot install either
package, and a build that stops there produces no library at all. So the
consequence of absence is a reported capability, which is the whole mechanism
ADR-0325 built for exactly this case.

**A header SDL did not generate reports both as absent.** The existing
unreadable-config path warns and carries on, which was right when the capability
bits came from the probe. Now that these two come from the file itself, "could not
read it" resolves to "the bit is not set" — the right way round, because a build
that could not read SDL's answer has not earned the claim.

**`WINDOW_DECORATIONS` does not promise a titlebar, and says so.** It is a
build-time fact: without libdecor, SDL compiles no client-side decoration support
and a Wayland window opens bare however the session is configured (ADR-0083).
Whether a titlebar then appears is a *run-time* question with its own defect —
libdecor's default plugin refuses to start off the process's initial thread and a
JVM is never on it (ADR-0084). Built able to ask is the claim; the javadoc on both
enums says which half it is. Conflating them would have produced the worst
possible value: a bit that is set on the exact machine where the bug is.

**`WAYLAND` is Linux-only and unset elsewhere.** A library claiming it on Windows
would be claiming something false about the session it will run in. And like every
other value here it describes the library: a build with the driver still runs on
X11 when that is what the desktop is.

## Consequences

- **The silent case is now audible in two places**: a warning naming
  `mesa-libEGL-devel` at configure time, and a missing bit at run time. EGL's
  headers are the spec whose absence has actually dropped the driver, which is why
  the warning names that one first.
- **The release container will warn on every build**, twice, for as long as
  `libdecor-devel` is unavailable there — and the published `linux-x64` library
  will report `WINDOW_DECORATIONS` absent. That is a true statement about it and
  the first time the artifact has said so. Whether Goldberry should carry its own
  decorations instead (`SdlWindowFlag.BORDERLESS`, reserved by ADR-0084) is
  unchanged by this record; what changes is that an application can now ask.
- **The ABI version goes 12 → 13.** No exported symbol changed shape, so this is
  the looser reading of the shim's own rule — but the constant table gained two
  rows that `:natives`' enum now requires, and a `:natives` jar loaded against an
  older library should fail saying *the library is old* rather than saying a
  constant is missing.
- **`:natives:test` and `:core:test` need a rebuilt library.**
  `./gradlew -Pgoldberry.allowDegradedPlatform=true :natives:cmakeBuild` first, as
  after every ABI bump.
- `PlatformIntegrationBuildTest` gains a second enum, `SdlDecided`, held to three
  things: that the define is read out of SDL's header, that absence warns rather
  than stops, and that the unreadable-header path claims neither capability. The
  existing `bitsMatchTheShim` covers the two new bits without being touched,
  because it is parameterized over the enum.
- **What this does not do is test the negative path.** No machine here can produce
  an SDL configured without Wayland, so the warning branch and the absent bits are
  reasoned about rather than observed — the same limit every other row in G32 has,
  and the reason the message names packages rather than diagnosing.
