# ADR-0086: X11 is the Linux default, for now

- **Status:** Accepted
- **Date:** 2026-08-18
- **Relates to:** `docs/ARCHITECTURE.md` §3; [ADR-0027](0027-prefer-wayland-fall-back-to-x11.md), [ADR-0083](0083-on-gnome-wayland-libdecor-is-not-a-fallback.md), [ADR-0084](0084-the-gtk-plugin-cannot-decorate-a-jvms-window.md)
- **Supersedes:** [ADR-0085](0085-a-window-that-closes-beats-a-sharper-one-that-cannot.md)

## Context

[ADR-0085](0085-a-window-that-closes-beats-a-sharper-one-that-cannot.md) made the
X11 preference conditional: Goldberry probed the libdecor plugin directory and the
calling thread before `SDL_Init`, and asked for `x11,wayland` only when it could
prove a Wayland window would be undecorated. It shipped and worked.

The condition is doing less than it looks. Every input to it is stable for the
whole of the current situation:

- The GTK plugin cannot run under the stock `java` launcher, and that is upstream's
  deliberate position, unconditional in libdecor `master`
  ([ADR-0084](0084-the-gtk-plugin-cannot-decorate-a-jvms-window.md)).
- The Cairo plugin — the one case where the probe says "Wayland is fine" — draws a
  generic titlebar that matches no desktop. It reads no GTK settings, no GSettings
  and no portal. So the branch the probe protects does not deliver native
  decorations either; it delivers *different* non-native ones.
- Under XWayland the window manager decorates the window itself, which is the only
  configuration today that produces a titlebar matching the desktop.

So the conditional bought a Wayland session whose decorations are generic, at the
cost of a probe that has to be right about a distribution's filesystem layout. On
the machine that prompted all of this, installing the Cairo plugin silently moved
the application from X11 back to Wayland and *changed how the titlebar looked* —
recorded as a consequence in ADR-0085 and, in use, simply confusing.

## Decision

**On a Linux Wayland session, ask SDL for `x11,wayland` unconditionally. Delete
the pre-init probe.**

The preference is now one constant with one reason, and the reason holds for every
machine the probe used to distinguish. `x11,wayland` rather than `x11`: a session
with no XWayland must still get a window, and an undecorated one beats `SDL_Init`
failing outright.

**"For now" is part of the decision, not a hedge.** Three things would each end
it, and the title says so to keep the record honest:

- Goldberry drawing its own decorations, which is what every non-GTK toolkit on
  GNOME/Wayland does and what `SdlWindowFlag.BORDERLESS` already describes;
- libdecor's out-of-process GTK plugin (MR 176) reaching distributions, after
  which the GTK plugin works in any JVM;
- a decision to ship a launcher that embeds the VM
  ([ADR-0084](0084-the-gtk-plugin-cannot-decorate-a-jvms-window.md)).

**The diagnostic stays.** `WaylandDecorations` still runs after `SDL_Init` and
still warns, because Wayland is still reachable — through
`-Dgoldberry.backend.videoDriver=wayland`, through `SDL_VIDEO_DRIVER` in the
environment, and on a session with no XWayland at all. Those are exactly the cases
where someone needs the explanation. What was removed is `wouldBeUndecorated`, the
entry point that existed only to be asked before `SDL_Init`.

## Alternatives considered

- **Keep ADR-0085's conditional.** Rejected above: it distinguishes cases that no
  longer differ in the way that matters, and its own consequence — a package
  install changing the video driver — is a surprise nobody asked for.
- **Ask for `x11` alone.** Rejected: it turns a session without XWayland from a
  cosmetic problem into a failure to start.
- **Prefer X11 on every Linux session, not just Wayland ones.** Rejected as noise:
  with no `WAYLAND_DISPLAY` there is nothing to prefer away from, and SDL already
  picks X11. Leaving that path untouched keeps the change to the case it is about.
- **Add `goldberry.backend.preferWayland`.** Rejected as redundant.
  `goldberry.backend.videoDriver` is checked first and short-circuits everything,
  so the escape hatch already exists and there is only one of it.

## Consequences

- **Linux users get XWayland by default, and with it the desktop's own titlebar.**
  This is the visible outcome and the point.
- **[ADR-0027](0027-prefer-wayland-fall-back-to-x11.md) is reversed in practice, for
  now.** Its measurements stand and its reasoning is unchanged — an XWayland window
  resizes visibly worse and scales blurrier. That record is not superseded, because
  what changed is not the measurement but which axis wins while decorations are
  unobtainable on the better one.
- Anyone who wants Wayland says so: `-Dgoldberry.backend.videoDriver=wayland`. The
  `INFO` line at start-up names that flag, so the default is discoverable from a
  log rather than from this record.
- **`verdictForWayland` now has only one caller.** It stays split from `verdict`
  rather than being inlined, because it is the shape a conditional fallback would
  need again, and re-deriving it later is more work than leaving the seam.
- Nothing in CI exercises either driver on Wayland — every leg runs under Xvfb, so
  the default is now the configuration CI has always tested, which is a small
  incidental gain in how much the CI result means.
