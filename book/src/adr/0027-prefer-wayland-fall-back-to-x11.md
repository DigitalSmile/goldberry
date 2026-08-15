# ADR-0027: Prefer Wayland, fall back to X11

- **Status:** Accepted (supersedes the decision in [ADR-0026](0026-sdl-picks-the-video-driver.md); its findings stand)
- **Date:** 2026-08-15
- **Relates to:** `docs/ARCHITECTURE.md` §4, [ADR-0003](0003-sdl3-as-the-only-desktop-backend.md), [ADR-0024](0024-a-repaint-must-wake-the-loop.md), [ADR-0026](0026-sdl-picks-the-video-driver.md)

## Context

ADR-0026 established why resizing felt worse than a native window: on a Wayland
session SDL was choosing X11, so the window ran through XWayland. It also decided
**not** to override that, on the grounds that SDL's preference check exists for a
measured reason and one report is not enough to overrule upstream on every
machine.

That was the right call to make with the evidence available, and the evidence has
since changed. The Wayland path was tried on the reported setup — GNOME on
Wayland — and resizes better. Not marginally: better enough to be the reason this
record exists.

Worth restating, because it is the counter-intuitive part: the Wayland path
reports *higher* per-frame numbers. `present` goes from 3.2–5.8 ms on X11 to
4.3–17.6 ms on Wayland. That is not the backend being slower. It is the
compositor pacing the client — the same thing a native window experiences — and
it looks better precisely because it is synchronised rather than free-running.

What SDL is protecting against without `wp_fifo_manager_v1` is unreliable frame
pacing. What it falls back to is XWayland, whose resize is not synchronised with
the compositor at all. On this evidence the fallback is the worse of the two.

## Decision

On Linux, when `WAYLAND_DISPLAY` names a session, Goldberry asks SDL for
`wayland,x11`.

The hint takes a comma-separated list and SDL tries each entry in turn, so this is
a **preference and not a demand**: a machine with no Wayland, or one where the
Wayland driver fails to start, gets X11 exactly as before — resolved inside SDL,
with no fallback logic in Goldberry to get wrong.

Three things override it, in order:

1. `-Dgoldberry.backend.videoDriver=<name>` — an explicit choice wins.
2. `SDL_VIDEO_DRIVER` or `SDL_VIDEODRIVER` already in the environment. SDL reads
   this hint from the environment too, so setting it unconditionally would
   silently beat what the user put in their shell.
3. Not being on Linux. Windows and macOS have one driver each and nothing to
   choose between.

The chosen driver is still logged at info, which is how any of this was found.

## Alternatives considered

**Leave it to SDL, as ADR-0026 decided.** Defensible until it was tested. The
argument was that SDL knows its backends better than this project does, which is
true in general and turned out not to be true for this trade on this compositor.
A decision made to avoid overruling upstream without evidence should change when
evidence arrives; that is what it was waiting for.

**Force `wayland` with no fallback.** Simpler to read and it makes the failure
mode catastrophic: a session where the Wayland driver cannot start gets no window
at all rather than an X11 one. The comma-separated list costs one character over
the demand and removes that whole class of report.

**Only prefer Wayland when `wp_fifo_manager_v1` is absent** — that is, invert
SDL's own check. It sounds precise and it is unreachable: the check needs a
Wayland registry round trip, which means connecting to the compositor before
`SDL_Init`, which is exactly what SDL is doing internally and not something to
reimplement across the FFM boundary for a hint.

**Wait for a renderer-backed present instead.** ADR-0026 called this the real
answer and it still is — `SDL_Renderer` with a streaming texture would move the
upload to the GPU and let SDL multi-buffer. It is also a §5 rendering-pipeline
decision that collides with ADR-0002's "no GPU context for plain UI", and it is
months away. This is one line and helps today.

## Consequences

Resizing on a Wayland session is smooth, which is the whole point.

Goldberry now disagrees with SDL about SDL's own driver preference on one
platform. That is a real thing to have taken on: if a future SDL changes the check
— or if a compositor advertises `wp_fifo_manager_v1` and the calculus flips — this
override will be silently stale. It is one constant and a documented reason, which
is the cheapest form that debt comes in.

The preference is evidence from **one compositor**. GNOME is the common case, so
being right there matters, but KDE, Sway and the rest have not been tried and this
record should not be read as if they had. Anyone who finds the opposite has two
documented ways to say so, and the log line to prove which driver they got.

X11-only sessions are unaffected: no `WAYLAND_DISPLAY`, no hint, no change.
