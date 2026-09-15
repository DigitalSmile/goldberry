# 322. The desktop says light or dark, or says nothing

Date: 2026-09-15

## Status

Accepted. Closes `docs/gaps.md` G26.

## Context

An application that ships two themes has one question it cannot answer for itself:
**which one is the user's desktop set to**, and **when does that change**. The
second half is the one that matters: on every platform with a sunset schedule it
changes once a day, while the application is running, so asking at start-up answers
the first question and none of the later ones.

Every way of asking is a platform call. `SDL_GetSystemTheme` and
`SDL_EVENT_SYSTEM_THEME_CHANGED` cover all three platforms in one; the alternatives
are `AppleInterfaceStyle` through `NSUserDefaults`, the `AppsUseLightTheme` registry
value, and the XDG settings portal over D-Bus. Goldberry already owns the window,
the event loop and the SDL layer, and
[ADR-0174](0174-what-both-halves-need-is-its-own-module.md) says an application may not
hold a `MemorySegment` at all — so an application reaching for any of the three is
the thing `:natives` is sealed to prevent.

## Decision

**Two methods on `Host`, and the second one is the point.**

```java
Optional<SystemTheme> systemTheme();                        // LIGHT | DARK
void onSystemThemeChanged(Consumer<SystemTheme> listener);
```

`SystemTheme` is `:core`'s own word for it, in a new
`io.github.digitalsmile.goldberry.render.desktop` package — the SDL enum stays
inside `:natives`, like every other platform vocabulary.

### The `Optional` is the design, not a nicety

SDL answers `SDL_SYSTEM_THEME_UNKNOWN` on a desktop that has no such setting, and
an application needs to tell **"the desktop says light"** from **"the desktop does
not say"**: the first is a theme and the second is a default. `SystemTheme`
therefore has *two* constants and the third answer is an empty `Optional`, which no
caller can ignore by accident — a third enum constant is exactly the thing a
`switch` forgets.

Three different situations produce empty, and they are deliberately one answer: a
desktop with no such setting, a video driver that cannot ask, and a `libgoldberry`
built before the export existed. What a caller does about them is identical.

### The path down

- `SDL_GetSystemTheme` is on the export list, bound as an **optional** symbol —
  `SdlVideo.systemTheme()` answers `UNKNOWN` rather than failing to link, which is
  the same treatment the display-mode pair gets and for the same reason: a library
  built before the symbol existed must keep opening windows.
- `SdlSystemTheme`'s three ordinals and `SDL_EVENT_SYSTEM_THEME_CHANGED`'s number
  are in the layout probe's registry, so the C preprocessor checks them
  ([ADR-0010](0010-hand-written-ffm-bindings.md)). A wrong event number does nothing at all
  and a wrong ordinal starts the application in the wrong theme; neither reports an
  error anywhere, which is what that probe exists for.
- `Backend.systemTheme()` is a **default** method returning empty, so the headless
  backend and every test double are correct without being touched. The headless one
  overrides it, with a setter that also posts the event — a desktop in a test.

### The event carries a window, and the setting does not

SDL delivers the change with no window id, because it concerns the session. It is
translated into **one `SystemThemeChanged` per open window**, which is exactly what
`QUIT` already does with `CloseRequested`, and for the same reason: a `Host` is per
window, so that is where an application is listening. A two-window application gets
two events with the same theme in them, and each window's listeners hear it once.

`Window` gains `systemTheme()` and `onSystemThemeChanged(…)` too, because a bare
window with no `Application` over it is a supported shape and this is a
window-level fact in the same sense a scale change is. No repaint is scheduled for
it, for `onMove`'s reason: nothing inside the window changed, and what to *do* about
a new setting is the application's decision — the frame comes from whatever it
changes.

### No `Subscription` comes back

Unlike the router's listeners, `onSystemThemeChanged` hands back no handle. The
caller is the application, its listener lives as long as the window it registered
against, and that is the lifetime of the application itself. A widget that wants to
follow the desktop should be told by the application, through whatever it already
rebuilds from — a widget subscribing to a window-lifetime listener is the leak the
absence of a handle makes obvious.

The launcher notifies over a copy of its list, so a listener that reacts by adding
another one — a settings screen that appears *because* the theme changed — is not a
`ConcurrentModificationException`. A listener registered during a notification hears
the *next* change.

## Consequences

**The toolkit still does not choose a theme.** Goldberry ships `nord-light` and
`nord-dark`; which one an application uses, whether it follows the desktop at all,
and whether it offers a three-way choice of its own are the application's. This is
one input to that decision and nothing more — an application that ships a single
theme ignores it and nothing changes.

Nothing is cached on the Java side. SDL keeps the answer and updates it from the
same platform notification that produces the event, so a copy here would be a second
thing to keep right.

The event is now the fourth kind that is not really about a window, after `QUIT`,
the dialog answers and the frame heartbeat. `BackendEvent.window()` still holds for
every case, which is what keeps the runtime's exhaustive `switch` honest.

## Alternatives considered

**A `SystemTheme.UNKNOWN` constant.** Smaller signature, and it moves the mistake
into every `switch` that forgets the third arm. The gap asked for the `Optional` in
as many words, and the reason it gave is the reason it is here.

**Resolve it in the toolkit** — ship a `Theme.SYSTEM` that picks a stylesheet. It
would decide, for every application, that following the desktop is the right default
and that "unknown" means light. Both are the application's calls, and one of them is
the difference between a document tool and a drawing tool.

**A poll instead of an event.** Reading the setting once a frame would be a platform
call per frame for a value that changes once a day, and would still have no answer
for "when".

**An `EventWatch`**, the way the interactive-resize path works (ADR-0060). The theme
change does not arrive inside a blocking platform call, so there is nothing for a
watch to rescue — the ordinary queue is where it already is.
