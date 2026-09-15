# 325. A build says what it can ask the desktop

Date: 2026-09-15

## Status

Accepted. Closes `docs/gaps.md` G32.

## Context

[ADR-0322](0322-the-desktop-says-light-or-dark-or-says-nothing.md) bound
`SDL_GetSystemTheme` and gave applications `Host.systemTheme()`. On a GNOME/Wayland
desktop set to dark, it answered empty.

The desktop was not the problem. Measured on the machine that reported it:

```
$ gsettings get org.gnome.desktop.interface color-scheme
'prefer-dark'

$ gdbus call --session --dest org.freedesktop.portal.Desktop \
      --object-path /org/freedesktop/portal/desktop \
      --method org.freedesktop.portal.Settings.Read org.freedesktop.appearance color-scheme
(<<uint32 1>>,)                       # 1 = prefer dark

# and, calling the toolkit's own library directly:
after SDL_Init: SDL_GetSystemTheme() = 0        # 0 = UNKNOWN
```

The desktop answers. The portal answers. SDL does not, and the reason is in the
library rather than in the session. On Linux, SDL's theme detection is **entirely**
the D-Bus portal — `src/core/linux/SDL_system_theme.c` reads
`org.freedesktop.appearance color-scheme` from `org.freedesktop.portal.Settings`
and subscribes to its `SettingChanged` signal, and there is no second path. That
file is behind `SDL_USE_LIBDBUS`, which is `#define`d from `HAVE_DBUS_DBUS_H`,
which SDL's CMake sets from a **build-time** probe:

```cmake
dep_option(SDL_DBUS "Enable D-Bus support" ON "${UNIX_SYS}" OFF)
...
if(SDL_DBUS)
  pkg_search_module(DBUS dbus-1 dbus)          # headers, not the library
```

SDL `dlopen`s `libdbus-1.so` at *run* time, but only if the *headers* were present
when it was compiled. On the machines that built Goldberry's SDL they were not:

```
$ grep DBUS .../build_config/SDL_build_config.h
/* #undef HAVE_DBUS_DBUS_H */
```

so `SDL_GetSystemTheme()` was a compile-time constant `UNKNOWN` in every copy of
`libgoldberry.so` built that way, on every Linux desktop, however it was set.

**It was not only the theme, and it was not only one machine.** The same probe
gates the portal file dialog and the screensaver inhibit; a second gates the input
method on X11; a third, device hotplug. And the absence was structural rather than
accidental: `LinuxDependencies` — the table `checkToolchain` reads — listed
`dbus-1` as `OPTIONAL` under the purpose *"SDL3 desktop integration"*, which was an
honest description until ADR-0322 bound the call; `linux.yml`, which builds the
**published** artifact inside a manylinux container, installed neither
`dbus-devel`, `systemd-devel` nor `ibus-devel`; and `LinuxDependenciesTest`'s CI
drift guard checked that workflow for `HARD_STOP` rows only, because hard stops are
what CI had previously been burned by.

So four things were true at once: a `-dev` package was missing, no local check
minded, CI did not install it, and the guard that exists to catch exactly this
disagreement did not look. Nothing failed. Every build was green, and a client
shipped a settings screen that told its user their desktop had no light-or-dark
setting.

## Decision

**A build that cannot ask the desktop something stops; one that is told to build
anyway says so, in the binary, for ever after.**

Three parts, and the third is the one that generalises.

### 1. The headers are a declared dependency

`dbus-1` becomes `NEEDED` in `LinuxDependencies` — the necessity that already
means *"SDL drops this silently, so fail here because SDL will not"*, the same
reading `libdecor-0` and the Wayland spec get. `ibus-1.0` joins the table as a new
row. Each desktop-integration row also names the `Capability` constants a library
loses without it, which is what ties the build table to what an application can
read back at run time.

`linux.yml` installs `dbus-devel`, `systemd-devel` and `ibus-devel` — all three
verified present in the manylinux_2_28 container — and `example.yml` and
`showcase.yml` gain `libibus-1.0-dev`. `LinuxDependenciesTest` now asserts that the
workflow building the published artifact installs the package behind **every**
capability, not just the hard stops.

### 2. The superbuild stops rather than degrading

The CMake superbuild probes for the same pkg-config modules SDL probes for
(`dbus-1`/`dbus`, `ibus-1.0`, `libudev`), sets `SDL_DBUS`, `SDL_IBUS` and
`SDL_LIBUDEV` explicitly rather than inheriting their defaults, and raises a
`FATAL_ERROR` naming the package — on both package managers — when D-Bus is
absent. It then **cross-checks its own prediction against SDL's answer**, by
reading the `SDL_build_config.h` SDL just generated: headers present and
`HAVE_DBUS_DBUS_H` still undefined is a build that would report a capability it
does not have, which is the one outcome this record rules out.

`-DGOLDBERRY_REQUIRE_PLATFORM_INTEGRATION=OFF`, or
`-Pgoldberry.allowDegradedPlatform=true` through Gradle, builds without them on
purpose — one flag reaching both the toolchain check and CMake, so the two halves
cannot disagree about what was asked for.

### 3. The library reports what it is

```java
Set<Capability> Goldberry.capabilities();
// SYSTEM_THEME, INPUT_METHOD, DEVICE_HOTPLUG, FILE_DIALOG, SCREENSAVER_INHIBIT
```

The superbuild passes what it found to the shim as compile definitions;
`goldberry_platform_capabilities()` returns them as a word; `NativeCapability`
decodes it and `:core`'s `Capability` is what an application sees — two enums,
because `natives.*` does not leave `:natives`
([ADR-0174](0174-what-both-halves-need-is-its-own-module.md)), and a `switch` the
compiler checks between them. The five bit values are rows in the layout probe's
registry, like every other constant the bindings hard-code
([ADR-0010](0010-hand-written-ffm-bindings.md)).

The bits describe the **library**, not the session it is loaded into. A build that
can ask reports `SYSTEM_THEME` even on a desktop that has no such setting, because
*"could not ask"* and *"asked and was told nothing"* are different facts and only
the first one is fixable. The second is what an empty `Host.systemTheme()` means,
and the two together are what let an application say something true to its user.

And the sdl3 backend warns once at start-up when a capability it ships an API for
is missing — naming the package — because a log line on the machine where it is
wrong is worth more than a paragraph in a document.

## Consequences

**The ABI version goes to 10.** A new export changes the shape of the surface, so
an old `libgoldberry` beside new Java fails at load time with the message that
already exists for it. The pair is built together everywhere it matters.

**A contributor without `libdbus-1-dev` now has to install it, or pass a flag.**
That is the intended cost and it is small: one package, named in the failure, on
both package managers. The alternative is the state this record exists to end,
where that contributor builds a lesser library and cannot tell.

**`INPUT_METHOD` is a Linux half-truth, deliberately.** On Wayland SDL drives
`zwp_text_input_v3` from the compositor and needs neither IBus nor Fcitx, so a
build without the bit composes text perfectly well there and not at all under X11.
A build-time bit cannot express "depends on the session", so it reports what it is
— whether an X11 session *would* have an input method — and the backend only warns
when the driver actually is X11. The javadoc says so in as many words.

**`FILE_DIALOG` is about the portal specifically.** SDL has a second path on Linux
— it shells out to `zenity` — so a library without the bit may still open a dialog
on a machine that happens to have that binary. The bit reports the half the build
decides.

**Empty means two things and that is accepted.** `Goldberry.capabilities()` is
empty both for a library that can do none of this and for a run with no native
library at all — a Java-only test, the headless backend. Both mean *do not expect
these to work here*, which is what a caller acts on.

**This does not fix libdecor.** The same audit found `linux.yml` installs no
`libdecor-devel` or `xkeyboard-config` either, and neither package exists in the
manylinux_2_28 repositories — so the published Linux artifact may have a Wayland
window with no titlebar, for the same class of reason and with a harder answer.
That is a separate entry, not this one, and the capability mechanism above is what
it should be reported through when it is written.

## Alternatives considered

**Read the portal from `:core`.** It is what SDL does, and doing it ourselves
would mean Goldberry talking D-Bus to the desktop — platform integration living
above the backend SPI, and a second answer to a question `Host.systemTheme()`
already answers. Worse than none.

**A run-time probe instead of a build-time constant.** Asking the session whether
D-Bus is reachable answers a different question: a library compiled without the
support cannot use a D-Bus that is right there. What an application needs to know
is what its library can do, and that is decided once, at build time.

**Warn instead of stopping.** A warning in a configure that prints several hundred
lines is a warning nobody reads — this one was effectively printed for months by
SDL itself, in the form of a `#undef` in a generated header. The default is a stop
precisely because the failure mode is silence.

**One `Capability.DESKTOP_INTEGRATION`.** Fewer constants, and it would collapse
three independently-gated features into one bit that is false when any of them is,
telling an application less than it needs to say anything useful.
