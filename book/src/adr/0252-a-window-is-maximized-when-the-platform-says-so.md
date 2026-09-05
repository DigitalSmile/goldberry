# 252. A window is maximized when the platform says so

Date: 2026-09-05

## Status

Accepted. Closes the `TODO.md` entry opened by
[ADR-0221](0221-a-window-may-open-maximized.md).

## Context

The entry lists three absences and the question that had kept them:

> `Application.maximized()` is a creation flag: it becomes
> `SDL_WINDOW_MAXIMIZED` and after that nobody involved knows whether the window
> still is one. There is no `Window.maximize()`, no `restore()`, no
> `isMaximized()`, and no `SDL_EVENT_WINDOW_MAXIMIZED` plumbed through — so an
> application cannot find out that the *user* maximized it, which is the half
> that makes a "remember my window size" preference possible. Each of the three
> is small on its own; together they are a window-state feature with a question
> in it that nothing has asked yet (what does `isMaximized()` return between the
> request and the event?).

## Decision

### `isMaximized()` answers what the platform last *reported*

That is the question, and the answer follows from what maximizing actually is.
ADR-0221 already established it: maximized is a **state**, not a size. Every
platform routes the ask through a window manager that may refuse it, delay it, or
grant it in part — a tiling compositor has its own idea, and a window with a
maximum size may be given less than the work area.

So a flag set on the way out would be a lie the moment one of those happened.
`isMaximized()` reports what `SDL_EVENT_WINDOW_MAXIMIZED` and `…_RESTORED` last
said.

**The cost is stated rather than hidden**: between `maximize()` and the event,
`isMaximized()` still answers `false`. That is a window that has been asked and
has not yet agreed, and there is no third answer that is true. A test asserts
exactly this, because it is the kind of thing a later reader would "fix".

It is also what makes the interesting half work. An application can find out that
**the user** maximized it, which is what a "remember my window size" preference
needs and which no amount of tracking one's own calls can produce.

### One event for both directions

`BackendEvent.MaximizedChanged(window, boolean)`, which is `FocusChanged`'s shape
and for its reason: SDL reports two events and every consumer wants the boolean.

SDL's `RESTORED` fires for un-maximizing **and** un-minimizing, and both mean the
same thing to a window that tracks only the one state.

### `setMaximized` is a request, all the way down

`BackendWindow.setMaximized(boolean)` returns nothing, at every layer, because at
no layer is the answer known yet. It defaults to a no-op, so a backend with no
window manager has nothing to ask; the headless one overrides it to agree and
report, which is what lets a test drive the whole path.

`HeadlessWindow.reportMaximized` is the other half — a change the application did
**not** ask for. Every other route into this state starts with the application,
and that is the route that does not.

## Alternatives considered

- **Report the request optimistically**, and correct it if the event disagrees.
  It makes `isMaximized()` true for a window that never became one, on exactly
  the platforms where the answer matters most.
- **Return a boolean from `maximize()`.** What could it mean? SDL's return says
  whether the *ask* was accepted, not whether the window changed — a `true` that
  does not imply the thing the caller wanted is worse than no return at all.
- **A `Minimized` state beside it.** Nothing has asked, and SDL's `RESTORED`
  collapses the two undos into one event — so tracking both needs a rule about
  what `RESTORED` means when both were set, for a state no entry mentions.
- **Expose `SDL_GetWindowFlags` and ask on demand.** It is a synchronous call per
  question rather than a field per event, it does not exist on the headless
  backend at all, and it still cannot tell an application that something
  *changed*.

## Consequences

- **Two native bindings, and the export list and the C shim both had to learn
  them.** `SDL_MaximizeWindow` and `SDL_RestoreWindow` go in
  `goldberry.symbols`; the two event constants go in `goldberry_shim.c`, because
  `LayoutVerificationTest` refuses a constant declared in Java that nothing
  verifies against the compiled library.
- **That refusal earned its keep immediately.** `SDL_EVENT_WINDOW_MAXIMIZED` is
  `0x20A` and `RESTORED` is `0x20B`, which were derived by counting an
  unnumbered C enum from the last explicit value — precisely the arithmetic that
  is silently wrong. The verifier checked both against the real library.
- **`GoldberryRuntime`'s switch is exhaustive over a sealed interface**, so
  adding the event failed the compile until it was routed. That is the design
  working rather than an inconvenience.
- **Six tests**, including the one that pins the decision — asking does not make
  it so — and the one that makes the feature worth having: the user maximizing a
  window nobody asked to.
- **`Application.maximized()` is unchanged.** It is still the creation flag
  ADR-0221 shipped; what is new is that the window can now be asked and told
  afterwards.
