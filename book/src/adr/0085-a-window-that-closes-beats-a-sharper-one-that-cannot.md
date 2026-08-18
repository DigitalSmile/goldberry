# ADR-0085: A window that closes beats a sharper one that cannot

- **Status:** Superseded by [ADR-0086](0086-x11-is-the-linux-default-for-now.md)
- **Date:** 2026-08-18
- **Relates to:** `docs/ARCHITECTURE.md` §3; [ADR-0019](0019-the-backend-spis-first-cut.md), [ADR-0027](0027-prefer-wayland-fall-back-to-x11.md), [ADR-0083](0083-on-gnome-wayland-libdecor-is-not-a-fallback.md), [ADR-0084](0084-the-gtk-plugin-cannot-decorate-a-jvms-window.md)
- **Amends:** [ADR-0084](0084-the-gtk-plugin-cannot-decorate-a-jvms-window.md) (which decided to report and not act)

## Context

[ADR-0084](0084-the-gtk-plugin-cannot-decorate-a-jvms-window.md) established that a
Wayland window in a stock-launched JVM has no titlebar and cannot be resized
unless a non-GTK libdecor plugin is installed, and chose to *report* that rather
than act on it. Two arguments were given for not acting: a driver that changes
underneath the application surprises people, and Wayland was preferred
deliberately in [ADR-0027](0027-prefer-wayland-fall-back-to-x11.md) for the
resize quality XWayland gives up.

Both still hold. What changed is the weight on the other side, once the full shape
of the problem was known:

- **There is no in-process fix.** libdecor `master` still carries the unconditional
  thread check, and the loader reads only `LIBDECOR_FORCE_CSD`,
  `LIBDECOR_PLUGIN_DIR` and `XDG_CURRENT_DESKTOP` — none of which affects it. The
  remedies are all outside the process: install a package, change the launcher, or
  change the driver.
- **The default is the broken one.** `libdecor-0-plugin-1-gtk` is pulled in as a
  dependency of libdecor; `libdecor-0-plugin-1-cairo` is a separate package almost
  nobody installs. So the out-of-the-box state on Debian and Ubuntu, for every
  Goldberry application, is the undecorated one.
- **A warning does not fix a window.** The person who sees it is usually not the
  person who can act on it — the toolkit's user, running someone else's
  application, cannot install a plugin into a machine they do not administer.

Weighed against that, XWayland's cost is a resize that is visibly worse and
fractional scaling that is blurrier. Neither prevents using the window. Having no
close button does.

## Decision

**On a Linux Wayland session, when a Wayland window is known to come up
undecorated, ask SDL for `x11,wayland` instead of `wayland,x11`.**

The verdict comes from the same `WaylandDecorations` that produces the warning,
through a new `verdictForWayland` that takes no driver name — because the decision
has to be made **before** `SDL_Init`, when there is no driver to ask about yet.
Everything it depends on is available then: the libdecor plugin directory, and
whether this is the process's initial thread. That timing is the only real
constraint in the change, and it is why the check was split rather than reused
as-is.

**Only a definite `UNDECORATED` turns the preference around.** A plugin directory
that could not be located, or a `/proc` that could not answer, leaves the ordinary
`wayland,x11` in place. The fallback trades away real quality, and doing that on a
guess would degrade machines that were working — the same reasoning that makes the
warning stay silent when it is unsure, applied to an action instead of a message.

**Wayland stays on the list, behind X11.** `x11,wayland` rather than `x11`: a
session with no XWayland must still get a window, and an undecorated window beats
`SDL_Init` failing outright. When that happens the driver ends up as `wayland`
after all, and the ADR-0084 warning fires exactly as before — the two mechanisms
compose without either knowing about the other.

`-Dgoldberry.backend.videoDriver=wayland` pins Wayland despite all of this. No new
property was added: the existing override is checked first and short-circuits the
whole routine, so the escape hatch already existed.

## Alternatives considered

- **Keep reporting only**, as [ADR-0084](0084-the-gtk-plugin-cannot-decorate-a-jvms-window.md)
  decided. Rejected on the "a warning does not fix a window" argument above. The
  warning stays; it is now the thing that explains a *pinned* Wayland session
  rather than the only response to a broken one.
- **Fall back only on GNOME**, by reading `XDG_CURRENT_DESKTOP`. Rejected: the
  condition that matters is "libdecor has no plugin that works here", which is
  measured directly and is true or false regardless of desktop. Desktop-sniffing
  would add a second, weaker signal that can disagree with the first.
- **Drop Wayland from the list entirely** when falling back. Rejected: it turns a
  cosmetic problem into a failure to start on a session without XWayland.
- **A dedicated opt-out property.** Rejected as redundant —
  `goldberry.backend.videoDriver` already overrides everything, and a second knob
  for the same job is a second thing to keep in step.

## Consequences

- Out of the box on Debian and Ubuntu, a Goldberry application on GNOME/Wayland
  now gets a decorated, resizable window with the desktop's own titlebar. That is
  the visible outcome and the point of the change.
- **It is a silent driver change, which is exactly what
  [ADR-0084](0084-the-gtk-plugin-cannot-decorate-a-jvms-window.md) argued against.**
  Mitigated rather than avoided: it is announced at `INFO` with the reason, the
  package that would restore Wayland, and the flag that pins it. Anyone debugging
  a scaling or presentation difference will find that line before they find this
  record.
- **Installing `libdecor-0-plugin-1-cairo` now changes the video driver**, because
  the machine stops meeting the fallback condition. Correct, and still surprising:
  a package install moves an application from X11 back to Wayland. The `INFO` line
  names the package for exactly that reason.
- The quality that [ADR-0027](0027-prefer-wayland-fall-back-to-x11.md) bought is
  given up on affected machines. That record's measurements are unchanged and its
  preference is still the default; this is a narrower rule sitting in front of it,
  for the case where the sharper window cannot be closed.
- **Both mechanisms now read the same verdict**, so they cannot disagree about
  whether decorations are available — but they run at different times, and
  `verdictForWayland` is asked before SDL exists while `verdict` is asked after.
  A test asserts the two forms agree across every input combination, because a
  drift between them would produce a fallback with a warning, or neither.
- When upstream's out-of-process GTK plugin (libdecor MR 176) ships, machines with
  it will stop meeting the condition and return to Wayland on their own, with no
  change here. The rule is written against the symptom rather than the version, so
  it expires by itself.
