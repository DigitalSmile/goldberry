# 304. A window has a floor, and the desktop enforces it

Date: 2026-09-13

## Status

Accepted. Adds the one geometry constraint `WindowSpec` was missing, beside
`resizable`, `decorated` and `maximized`
([ADR-0221](0221-a-window-may-open-maximized.md)).

## Context

A Goldberry window could be dragged to any size at all, including sizes at which
nothing it contains means anything: a sidebar and a content pane at 200 logical
pixels wide are not two things, a `split` with two panes each a few characters
across is a layout nobody can read, and the toolkit will draw both without
complaint. Yoga does the arithmetic it is given, and the arithmetic is fine —
the result is simply not a user interface.

Every desktop toolkit has a floor for this, and every desktop window manager
knows how to enforce one. SDL exposes it as `SDL_SetWindowMinimumSize`. Nothing
in Goldberry asked for it.

The question that makes this a decision rather than a one-line binding is **who
enforces it**. There are two places it could go:

1. **The application clamps.** Watch `BackendEvent.Resized`, and when the size
   is below the floor, ask for a bigger one.
2. **The window manager refuses.** Declare the floor once; the pointer stops at
   the edge of the drag.

The first is available today and is what an application would otherwise have to
write. It is also visibly wrong: the resize has already happened by the time the
event arrives, so a frame is laid out and painted at the too-small size, and the
correction arrives as a second resize — the window shrinks and springs back
under a pointer that is still dragging. On a compositor that resizes
continuously, that is every frame of the drag.

## Decision

**The floor is declared, and the platform enforces it.**

- `WindowSpec.minimumSize` — a `LogicalSize`, defaulting to `NO_MINIMUM`
  (`0×0`), beside the other three window properties. `WindowSpec.of` gives no
  minimum.
- `BackendWindow.setMinimumSize` / `minimumSize()` — SPI, defaulting to a no-op
  and `NO_MINIMUM`, so a backend with no window manager to ask is honest rather
  than pretending. `Sdl3Window` hands it to `SDL_SetWindowMinimumSize`;
  `HeadlessWindow` enforces it in `resizeTo`, which is the only way this rule can
  be tested at all.
- `Window.minimumSize(LogicalSize)` — settable at runtime, because a window whose
  content changes shape has a different floor than the one it opened with.
- `Application.minimumSize()` — one line to override, defaulting to no minimum.

**A zero on an axis is "no minimum on that axis"**, per axis, which is SDL's own
reading and the one a window that cares about its width alone wants:
`LogicalSize.of(480, 0)` rather than a guessed height.

### The default is no floor

The toolkit does not know what a window contains. A floor invented for it would
be wrong for a colour-picker palette and wrong again for an editor, and a default
that is wrong in both directions is worse than an absent one — the application
that needed a floor still has to say so, and the one that did not now has to say
so too. `Showcase` declares 640×480, which is what a default would have been an
approximation of.

### Contradictions are refused, except the one the command line makes

`WindowSpec` refuses a minimum larger than the opening size on either axis.
Growing the window to its floor is not the size the application asked for, and
opening below the floor gives a window the user can never drag back to the size
it started at — the two ways out are opposite, so neither is a default. It also
refuses a minimum on a window that is not resizable, for the reason it already
refuses `maximized` on one: a window nobody can resize has no size to be stopped
at, and a declaration that cannot do anything is a reader's trap.

The exception is `--size=`, which exists so a screenshot or a golden run can pin
the window's geometry. `Launcher` **demotes** a floor that does not fit a
command-line size and says so in the log, rather than refusing to start: an
application declaring a 1024-wide minimum must not be able to make
`--size=800×600` fail. A floor is a promise to a *user* about what dragging an
edge may do, and there is no user in a golden run.

### Setting a floor does not resize the window

`Window.minimumSize` constrains what happens next; it does not grow a window that
is already below it. Resizing a window out from under whoever is looking at it is
a different action nobody asked for, and the open-time form — which refuses the
contradiction — is where that case belongs.

## Consequences

**An application gets the desktop behaviour users expect** for one overridden
method, with no resize handler and no clamping.

**`WindowSpec` gained a record component**, so the canonical constructor's arity
changed. There was exactly one direct `new WindowSpec(...)` outside the record —
in its own test — because `of` plus the withers is how it is built everywhere
else, which is the property that made this cheap.

**`libgoldberry` exports one more symbol.** `SDL_SetWindowMinimumSize` is in
`exports/goldberry.symbols`, so the native library must be rebuilt; a Java-only
build against an older library fails at bind time, loudly, which is the failure
mode [ADR-0035](0035-the-catalog-is-the-only-place-a-ref-lives.md) chose on purpose.

**The floor is not read back from the platform.** `minimumSize()` answers what
the backend was told. SDL has `SDL_GetWindowMinimumSize`, and a second native
call to read a number this process just wrote is a round trip for nothing — but
it does mean that a window manager which quietly ignores the constraint will be
reported as having one. That is the same bargain `setTitle` makes.

**There is no maximum size**, deliberately. `SDL_SetWindowMaximumSize` is the
obvious symmetry and there is no use for it: a window that must not grow is
`resizable = false`, and a ceiling that is not the display's is a constraint
users resent. It can be added the day something wants it.

## Alternatives considered

**Clamp in the resize handler.** Rejected — see above. It corrects a frame late,
which is a frame the user watched.

**A minimum expressed as a widget's intrinsic size**, so the floor is whatever
the content needs. Rejected as a much larger decision wearing this one's
clothes: it means running layout in an unbounded pass to find a minimum, once per
build, and re-asking the window manager whenever the answer moves. The number an
application would write down is also better: "below 640 the sidebar stops being a
sidebar" is a judgement about the design, not a measurement of it.

**Put it on `Window` only, not on `WindowSpec`.** Rejected: the floor would be
applied after the window is created and shown, so a window could briefly exist —
and be dragged — below its own minimum. Having it in the spec also means the
contradiction with the opening size is caught where both numbers are, rather than
by a runtime surprise.

**Default to something sensible, like 320×240.** Rejected: see above. It is a
number with no argument behind it, and it would silently constrain every
application that never thought about the question.
