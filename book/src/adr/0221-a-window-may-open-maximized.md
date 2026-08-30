# 221. A window may open maximized

Date: 2026-08-30

## Status

Accepted.

## Context

`Application` could say how big its window should be and nothing else about how
it opens. That is enough for almost everything, and it is not enough for the
showcase: a gallery is a layout whose whole subject is *how much fits on a
screen*, and a wall of cards that shows three columns on a 1440px display shows
two on a 900px one. Opening at 960×640 on a 4K monitor makes the argument the
gallery exists to make about a third as well as it could.

The obvious workaround is to ask for a very large size. It is wrong in three
separate ways, and each of them is the kind that only shows up on somebody else's
machine:

- **A size is not a state.** A 3840×2160 window on a laptop is a window larger
  than the screen, positioned by the compositor wherever it can, with its bottom
  edge and its resize grip off the display.
- **The desktop owns the work area.** A maximized window snaps to the space left
  over after panels, docks and menu bars; an application asking for "the display,
  minus what I guess a panel is" is guessing at something the desktop already
  knows.
- **It cannot be undone.** The titlebar's maximize button toggles a *state*. A
  window that is merely enormous has no state to leave, so pressing it makes the
  window bigger and then smaller than it was — and there is no size to restore
  to, because the enormous one was the only size the application ever named.

SDL has exactly the right thing: `SDL_WINDOW_MAXIMIZED`, a creation flag that
sits *beside* the size rather than instead of it. The size stays what the window
restores to.

## Decision

**`Application.maximized()` — a `default false` predicate beside `size()`.** The
launcher passes it to a `WindowSpec`, the SDL backend turns it into
`SDL_WINDOW_MAXIMIZED`, and the headless backend ignores it, having no desktop to
be maximized against.

**`size()` keeps its meaning, and gains one.** It is still the window's opening
size and it is now also the size a maximized window is restored to. Those are the
same number for the same reason: it is the size the application thinks its window
should be when nothing else has an opinion.

**Maximized-and-not-resizable is refused, in the `WindowSpec` constructor.** SDL
silently drops the flag on a fixed-size window, which leaves the application with
a small window it asked to have filled and the user with no maximize button to
fix it with. Both readings of a warning would be wrong, so it throws.

**`--size=` un-maximizes.** The flag exists so a screenshot or a golden run can
pin a window's geometry, and a window that took the size and ignored the geometry
would be the one combination nobody means.

**The default stays `false`,** and every example but the showcase leaves it
there. An application that takes the whole screen without being asked is one the
user has to undo before they can see anything else; a gallery is the case for
saying otherwise, and it says so in one method with a paragraph under it.

## Alternatives considered

- **A `WindowState` enum — `NORMAL`, `MAXIMIZED`, `FULLSCREEN`, `MINIMIZED`.**
  The shape this grows into if it grows. Three of the four are not creation
  states at all: fullscreen is a mode with a display and a video mode attached,
  and minimized-at-startup is a thing no toolkit should make easy. A boolean that
  answers the one question asked is smaller than an enum with three members
  nobody may use.
- **Runtime `maximize()` / `restore()` on `Window`.** Genuinely useful and a
  different feature: it needs `SDL_EVENT_WINDOW_MAXIMIZED` plumbed through so the
  application can find out the *user* did it, and a `isMaximized()` that is
  honest between the request and the event. Nothing has asked for it; this ADR
  does not close the door on it.
- **Sizing to the work area in the launcher** — `workArea()` is already on
  `BackendWindow`. It computes what the desktop would have done, gets it wrong on
  a multi-monitor setup where the window has not been placed yet, and still
  leaves a window with no maximized state to leave.
- **Leaving it to the desktop's window rules.** Real on Linux, absent on Windows,
  and not something an application can ship.

## Consequences

- **`WindowSpec` gains a fifth component.** It is a record and every construction
  site in the repository is `WindowSpec.of(...)` plus withers, so the change is
  the record and its two callers.
- **`SdlWindowFlag` gains `MAXIMIZED`,** which means `goldberry_shim.c` gains a
  `GB_CONSTANT` for it — the layout verification refuses a flag declared in Java
  that nothing checks against the real header. That refusal caught this exact
  omission on the first run.
- **The headless backend ignores it**, which is right and is worth stating: every
  golden image in the repository is drawn at a size the test chose, so no test
  can see this flag. `ShowcaseShellTest` asserts the *spec* instead.
- **What is not built**: reading the state back, changing it at runtime, and
  being told when the user changed it. A window opens maximized; after that
  nobody involved knows whether it still is.
