# ADR-0524: A test drives a session with a host under it

- **Status:** Accepted
- **Date:** 2026-10-02
- **Relates to:** [ADR-0284](0284-a-picture-with-no-window-under-it.md),
  [ADR-0424](0424-a-tree-mounted-once-and-photographed-repeatedly.md),
  [ADR-0102](0102-a-popup-is-a-window-the-platform-may-refuse.md),
  [ADR-0232](0232-modality-is-one-flag-and-not-a-scrim.md),
  `docs/goldberry-gaps.md` entries 8 and 9

## Context

An application could not test its own screens through input. The launcher's
`PointerRouter` is private, `GoldberryRuntime.install` is package-private on
purpose, and `Offscreen` and `Filmstrip` build a router and keep it. The
toolkit's own harnesses (`HeadlessRuntime`, `GoldberryTestAccess`, `TestHost`)
are test fixtures and are not published. Deploy Orc's tests therefore called
model commands directly. A modal scrim that swallowed every click passed every
one of them, and diagnosing it took a reflective probe with `--add-opens`.

The second half was rendering. `Offscreen.render` mounts `new ElementTree(root)`
with no host, so `Dialogs.show` cannot run, and a window with a dialog over it
cannot be drawn. Goldens fake one by putting the dialog in the scene as a child,
and the application made two `Confirm` methods public only for that.

## Decision

**`Offscreen.session(root)` opens a `Session`: a mounted tree under a
`WindowRoot`, with a host of its own, driven through the real router.**

- It sits beside `Filmstrip` in `dev.goldberry.offscreen` and is built on it.
  The strip takes a router and a tree from the session and owns them; its mount
  pass is now `pass()`, a build and layout that captures the regions and paints
  nothing. No new package, so `module-info` is unchanged.
- Every input method settles before and after. It builds, lays out, captures
  regions and fires due timers, up to ten turns, because a window paints
  between two events and the second one is answered against the first one's
  frame. `frame()` is the only call that rasterizes.
- `click(id)` and `click(Element)` press at the centre of the visible part of
  the node, or of the outermost box drawn under it for a composite. They
  **refuse** with `IllegalStateException` when the router's hovered node is not
  the target or inside it, and the message names what is on top. A click a user
  could not make is never delivered elsewhere. `click(x, y)` presses
  unconditionally. `hover`, `wheel`, `type`, `key(Key[, Modifiers])`,
  `key("Ctrl+S")` and `focus(id)` complete the set. `router()` is the escape
  hatch for a drag.
- Queries are `byId`, `byRole(role)`, `byRole(role, name)`, `elementAt`,
  `regions`, `focused`, `hovered`, `overlays` and `overruns` (ADR-0525).
- The clock is virtual and starts at zero. Input does not move it.
  `advance(Duration)` steps the clock **to** each due timer in turn and settles
  after each, so a timer scheduled by a timer fires at its own time.
- The host (`SessionHost`, package-private) is real where a test can see the
  difference. `fill` and `overlay` go on the layer the `WindowRoot` draws,
  `after` is a timer on the session clock, `focus`, accelerators and `isModal`
  are the router's, and `anchor` answers from the last capture. Elsewhere it
  gives the `dummy` driver's answers: no popups (ADR-0102), no tray, web view,
  clipboard, file dialogs or desktop theme. `window()` throws.
- Two pieces the host needed are public, because a test's own host needs them
  too:
  - `dev.goldberry.OverlayLayer` is the list a `WindowRoot` reads and the door
    overlays go on and off by. It hands back **attached** overlays, which
    `Overlay.attached` being package-private had kept to the launcher.
  - `dev.goldberry.render.event.TimerQueue` is `EventLoop`'s timer list and
    firing rule, taken out of the loop. The loop now delegates to it, and
    `TimerQueue.over(clock)` times one against a virtual clock.
    `EventLoop.Timer`'s constructor stays package-private.
- `GoldberryRuntime.install` stays package-private, and the fixtures stay
  unpublished. The session needs neither.

## Consequences

- A screen test now goes through `Dialogs.show` on the host its widget was
  built with. It clicks the dialog's button, advances past the closing
  animation, and sees the handler's answer. `DialogSessionTest` does exactly
  that, and a click on the window under an open dialog is refused with the
  scrim named. That is the failure entry 1 needed and could not get.
- The guide's manual `PointerRouter` recipe is replaced by the session. The
  router stays public for a one-widget test.
- `Launcher.attach` still keeps its own copy of what `OverlayLayer` does. Moving
  the launcher onto it is a follow-up, left out here so as not to collide with
  the overlay-identity work on `WindowRoot` in the same batch.
- A session has no popup windows, so a `select` or a menu opens nothing, which
  is what they do under `dummy`. A test of a popup's content still inflates it
  in a tree of its own.
- Context menus and bare-modifier taps are accepted and never fired. Both need
  a window.
