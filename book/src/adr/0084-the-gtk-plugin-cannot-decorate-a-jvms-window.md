# ADR-0084: The GTK plugin cannot decorate a JVM's window

- **Status:** Accepted
- **Date:** 2026-08-18
- **Relates to:** `docs/ARCHITECTURE.md` §3; [ADR-0003](0003-sdl3-as-the-only-desktop-backend.md), [ADR-0020](0020-one-ui-thread-and-virtual-threads-behind-it.md), [ADR-0083](0083-on-gnome-wayland-libdecor-is-not-a-fallback.md)
- **Amends:** [ADR-0083](0083-on-gnome-wayland-libdecor-is-not-a-fallback.md) (its fix is necessary and not sufficient)

## Context

[ADR-0083](0083-on-gnome-wayland-libdecor-is-not-a-fallback.md) concluded that a
GNOME/Wayland window has no titlebar because SDL was built without libdecor, and
added `libdecor-0-dev` to the toolchain check. That was correct and incomplete.
With libdecor compiled in and `libdecor-0-0` installed, the window still had no
titlebar and still could not be resized, and the run printed:

```
Failed to load plugin 'libdecor-gtk.so': failed to init
No plugins found, falling back on no decorations
```

libdecor draws nothing itself. It loads a plugin, and Debian and Ubuntu ship two:
`libdecor-gtk.so`, pulled in as a dependency of the library, and
`libdecor-cairo.so`, a separate package almost nobody installs.

`libdecor-gtk.so`'s constructor opens with this, at offset `0xbb45`, before it
touches GTK, D-Bus or Wayland:

```text
call   getpid
call   gettid
cmp    %eax,%ebx
jne    bcc5        ; -> xor %r14d,%r14d ; ret   (NULL, no message)
```

`getpid() == gettid()` is the Linux test for "am I the process's initial thread",
which is what GTK requires. **The stock `java` launcher does not satisfy it.** It
runs `main` on a thread it creates, so that the primordial thread's stack size
does not limit Java. The jump target lands *past* the plugin's own `fprintf`,
which is why it fails without a word and why `SDL_LOGGING=*=verbose` shows
nothing.

This record originally said *a JVM* never satisfies it. That was wrong, and the
distinction turns out to be the whole answer to "how do I get native decorations
on Wayland". It is a property of the **launcher**, not of the VM. A launcher that
embeds the VM — its own `main` calling `JNI_CreateJavaVM` and then the Java
`main` — runs Java on the primordial thread, and there the GTK plugin loads and
draws decorations that match the desktop. Measured with one variable changed, same
libdecor 0.2.5, same GNOME session, same GTK-only plugin directory:

| launcher | `main` runs on | libdecor |
|---|---|---|
| stock `java` | a created thread | `failed to init`, then `No plugins found` |
| embedded `JNI_CreateJavaVM` | the primordial thread | silent — GTK plugin loaded |

`jpackage`'s launcher does not help: it goes through `JLI_Launch`, which calls
`ContinueInNewThread` like the stock one.

**This is new behaviour, and that matters more than it looks.** The check was
added by commit `74839e51` on 2025-01-21 and first shipped in **libdecor 0.2.3**
(2025-05-13); every release up to 0.2.2 (2024-01-15) has no `gettid` in the GTK
plugin at all. On a distribution carrying an older libdecor, a JVM on
GNOME/Wayland gets real GTK decorations that match the desktop exactly, with no
warning and no fallback — which is why this can be remembered as having worked,
on the same compositor, with the same code.

It was not working. It was crashing intermittently for other people, which is why
the check exists. libdecor issue #72 is titled *"guvcview (with SDL backend, which
uses libdecor) crashes in GTK CSS code on startup on Wayland"*; the backtraces
land in GTK's CSS refcounting on `g_assert_not_reached()`, GTK's maintainers
declined it as a libdecor problem, and libdecor's fix was to stop the GTK plugin
running where GTK cannot. Lutris and Bottles are linked from the same issue with
variants of it.

**So downgrading libdecor is not a workaround for the decorations, it is a
reintroduced memory-corruption bug.** Recorded here explicitly because it is the
obvious next idea for anyone who saw the old behaviour and wants it back.

This was confirmed by reducing it to one variable. The same binary, in the same
session, against the same libdecor:

| Caller | `pid`/`tid` | Result |
|---|---|---|
| the process's initial thread | 51011 / 51011 | `libdecor_new -> 0x5c16005d62d0`, decorated |
| a `pthread` | 51018 / 51019 | `Failed to load plugin 'libdecor-gtk.so': failed to init` |

SDL 3.4 knows about the restriction. `Wayland_LoadLibdecor` deliberately
initializes libdecor on a secondary thread "so that it will not use its GTK
plugin, but instead will fall back to the Cairo or dummy plugin" — but only when
`SDL_CanUseGtk()` is false, and that function checks the `SDL_ENABLE_GTK` hint and
setuid/setgid, never the thread. So SDL believes GTK is usable, takes the direct
path, and the plugin fails anyway. **Cairo is the fallback SDL itself names.**

That libdecor is the *only* path here was measured rather than assumed, by
enumerating the compositor's globals on a GNOME 4x session:

```text
compositor globals relevant to decorations:
  xdg_wm_base (v7)
```

No `zxdg_decoration_manager_v1`. `should_use_libdecor` returns false only when
that global is present, so on GNOME there is no server-side path to fall back to —
unlike KDE and wlroots, which advertise it and never reach libdecor at all.

Nothing in CI could have caught this: `example.yml` and `showcase.yml` both run
under `xvfb-run`, which is X11, where the window manager draws the decorations and
libdecor is never reached.

## Decision

**Detect the condition and warn, loudly and once, naming the package. Do not throw
and do not change the video driver.**

Throwing was considered and rejected: the window opens, paints and receives input
correctly, and a fullscreen or kiosk application does not care about a titlebar.
Refusing to start would turn a cosmetic problem into an outage. Automatically
falling back to X11 was rejected for the reason
[ADR-0083](0083-on-gnome-wayland-libdecor-is-not-a-fallback.md) gave for not
preferring X11 in the first place — XWayland's fractional scaling is what
`SDL_WINDOW_HIGH_PIXEL_DENSITY` exists to avoid — and because a driver that
changes underneath the application is worse than a message telling it what to do.

**The verdict is inferred, because SDL cannot be asked.** `libdecor_new` returns a
valid context even when every plugin failed — it falls back to drawing nothing —
so SDL sees success, marks the surface `WAYLAND_SHELL_SURFACE_TYPE_LIBDECOR` and
exposes no property saying the frame is empty. What *is* knowable is the input to
that decision: which plugin files are installed. Since the GTK plugin is
guaranteed to fail in this process, "the GTK plugin is the only one" is equivalent
to "there will be no decorations".

`WaylandDecorations` is therefore three-valued rather than boolean, and the third
value is the load-bearing one:

- `UNDECORATED` — a plugin directory was found and nothing in it can decorate here.
- `DECORATED` — some non-GTK plugin is present, **or** the GTK plugin is present
  and this is the initial thread, where it will load.
- `UNKNOWN` — not Wayland, no plugin directory could be located, or the thread
  could not be determined and the answer depended on it. **Says nothing.**

**The thread is measured, not assumed.** `/proc/thread-self` is a symlink to
`<pid>/task/<tid>`, so one `readlink` yields both numbers and no native call is
needed. Assuming it instead — which this record did in its first draft — makes the
warning fire under an embedded launcher, against a window that has a titlebar the
user is looking at. That is the worst kind of wrong for a diagnostic, and it was
caught only by running the embedded launcher rather than reasoning about it.

A message that is sometimes wrong is worse than no message, because the next
person to see it will not believe it. Where libdecor keeps its plugins is a
distribution's choice; guessing wrong must produce silence, not a warning about a
problem that is not there.

## Alternatives considered

- **Throw a `BackendException` with the same text.** The house style
  ([ADR-0082](0082-a-preflight-check-that-cannot-fail-is-not-a-check.md)) favours
  failing loudly, and an undecorated non-resizable window is arguably broken.
  Rejected: unlike a build-time check, this runs on an end user's machine, where
  refusing to open a working window is a bigger failure than the one being
  reported.
- **Set `SDL_ENABLE_GTK=0`** so SDL takes its own non-main-thread branch.
  Rejected: it changes nothing here. That branch exists to make the GTK plugin
  fail on purpose; it already fails. Without the Cairo plugin installed there is
  still nothing to fall back to.
- **Ask SDL through a window property.** Rejected because it cannot answer — see
  above. This was checked before inferring rather than after.
- **Detect the thread directly**, since `gettid() != getpid()` is the actual
  cause. Rejected: it is *always* true in a JVM, so it discriminates nothing. The
  plugin listing is the only part of the condition that varies.
- **Bundle a decoration plugin, or draw decorations in Goldberry.** Both remain
  open, and [ADR-0083](0083-on-gnome-wayland-libdecor-is-not-a-fallback.md)
  already records the second as deferred. This record does not start either.
- **Fake the thread check with an `LD_PRELOAD` shim** interposing glibc's
  `gettid` to return `getpid` for the plugin's one call. It would work — for a
  process that uses GTK nowhere else it reproduces exactly the pre-0.2.3
  behaviour, which is what ran happily in a VM for months. Rejected as anything
  Goldberry ships or documents as a remedy: it is a native artifact anyway (so it
  buys nothing over a launcher), it re-enables behaviour upstream deliberately
  disabled, and the "GTK nowhere else" premise is not Goldberry's to guarantee —
  an application is free to embed WebKitGTK or a file chooser portal fallback in
  the same process, which is precisely the two-threads-in-GTK case issue #72 is
  about.
- **Wait for upstream's out-of-process GTK plugin.** libdecor MR 176 (active,
  last updated 2026-07-10) moves all GTK work into a dedicated child process that
  tunnels Wayland traffic, drawing the decorations as a subsurface. In that
  design the host process's thread no longer matters, so the restriction — and
  this whole record's problem — dissolves for every JVM app with no change on our
  side. Not a plan, because it has no date; but it means the launcher below is a
  bridge, not a permanent investment, and arguing against building anything
  elaborate here.
- **Ship a launcher that embeds the VM**, so Goldberry applications run `main` on
  the primordial thread and get the GTK plugin. This is the only route to
  decorations that match the desktop on Wayland, and it is now demonstrated rather
  than theoretical. Not decided here, because it is a distribution change rather
  than a diagnostic: it means a native binary per platform, `JNI_CreateJavaVM`
  argument handling, and an answer to what `./gradlew run` and a plain
  `java -jar` should do — none of which belongs in the fix for a missing warning.
  Recorded as the answer, and left for its own record. The warning names it as the
  third remedy so nobody has to rediscover it.

## Consequences

- The failure now announces itself, next to libdecor's own cryptic line, with the
  command that fixes it. It fires once, at the first window that asked to be
  decorated — a borderless window is unaffected and is not warned about.
- **The fix is a run-time package, not a build-time one.**
  `sudo apt install libdecor-0-plugin-1-cairo` requires no rebuild;
  [ADR-0083](0083-on-gnome-wayland-libdecor-is-not-a-fallback.md)'s
  `libdecor-0-dev` is still needed, at build time, to compile the support in at
  all. Two packages, two phases, and installing either alone leaves the window
  bare.
- **The warning can fire on a compositor that would have decorated the window
  anyway.** `should_use_libdecor` returns false when the compositor offers
  `zxdg_decoration_manager_v1`, so KDE and wlroots decorate server-side and never
  reach libdecor. Goldberry cannot see that protocol from Java, so a KDE machine
  with only the GTK plugin installed gets a warning about a problem it does not
  have. The message is worded to stay true in that case — it says GNOME is the
  compositor that declines — but it is noise there, and that is the price of
  inferring rather than asking.
- The plugin directory is searched by convention (`LIBDECOR_PLUGIN_DIR`, then the
  multiarch, `lib64` and `lib` paths). A distribution that puts it somewhere else
  gets `UNKNOWN` and silence, which is the intended failure mode rather than a
  bug.
- **CI still cannot catch a regression here.** Every CI leg is X11 under Xvfb.
  `WaylandDecorationsTest` covers the decision from a plugin listing, which is the
  part that can be tested without a compositor; that no job exercises the real
  Wayland path is unchanged and remains an open question in
  `book/src/status.md`.
