# ADR-0026: SDL picks the video driver, and says which

- **Status:** Accepted
- **Date:** 2026-08-15
- **Relates to:** `docs/ARCHITECTURE.md` §4, [ADR-0003](0003-sdl3-as-the-only-desktop-backend.md), [ADR-0024](0024-a-repaint-must-wake-the-loop.md)

## Context

After the frame path was made prompt and the redundant copy removed (ADR-0024),
resizing still felt worse than a native window. The frame numbers said it should
not: about 5 ms of work per frame at 1080p, most of it inside SDL's upload.

The numbers were measuring the wrong thing. Binding `SDL_GetCurrentVideoDriver`
and logging it at start-up answered it in one line:

```text
sdl3 backend started on SDL 3.2.0, video driver x11
```

On a Wayland session. Every Wayland library was installed, `libdecor` included,
and SDL was compiled with both drivers — SDL had simply chosen X11, which means
the window was going through **XWayland**. XWayland resize is not synchronised
with the compositor the way a native Wayland surface is, and looking worse than a
native window while resizing is precisely what it does.

SDL's choice turns out to be deliberate. From `SDL_video.c`, the Wayland driver
is tried before X11 only through `Wayland_preferred_bootstrap`, and
`Wayland_IsPreferred` returns true only if the compositor advertises
`wp_fifo_manager_v1`. Without that protocol SDL judges its own Wayland
presentation to have no reliable frame pacing and prefers X11. GNOME's Mutter does
not advertise it at the time of writing, so on the most common Linux desktop, SDL
deliberately chooses XWayland.

Forcing Wayland works and reports *higher* per-frame numbers — present rises from
3.2–5.8 ms to 4.3–17.6 ms. That is not the backend being slower; it is the
compositor pacing the client, which is what a native window experiences too.

## Decision

**SDL's judgement stands.** Goldberry does not override the video driver by
default. SDL knows more about its own backends than this project does, the check
exists for a real reason, and forcing Wayland on every machine would trade one
platform's stutter for another's.

**But the choice is visible and available.** The chosen driver is logged at
start-up at info level, so it appears in any bug report, and
`-Dgoldberry.backend.videoDriver=wayland` sets `SDL_VIDEO_DRIVER` before
initialization for anyone who wants to make the trade the other way. Both paths
log what happened.

## Alternatives considered

**Prefer Wayland whenever `WAYLAND_DISPLAY` is set.** It would very likely make
resizing look better on GNOME today, which is the reported complaint. It also
overrules an upstream check written by people who measured the failure mode it
guards against, on every user's machine, for a symptom observed on one. If
Goldberry is going to disagree with SDL about SDL's backends, it should be with
evidence across several compositors rather than one.

**Ship our own Wayland backend.** ADR-0003 closed that door on purpose: SDL3 is
the permanent desktop windowing layer, and the SPI exists to serve `headless` and
`scarlet`, not to grow hand-written platform backends. Resize smoothness on one
compositor is not the thing that reopens it.

**Use `SDL_Renderer` with a streaming texture instead of the window surface.**
This is the real technical alternative and it is not dismissed — it would move the
upload to the GPU and let SDL manage multiple buffers, which is roughly what
native toolkits do. It also creates a GPU context for plain UI, which ADR-0002
specifically avoids for start-up time, so it is a decision about §5's rendering
pipeline rather than a fix to reach for while chasing a resize glitch. When there
is a real renderer behind it, this deserves measuring properly.

## Consequences

The driver is in the log, so the next time windowing behaves oddly the first
question is answered before it is asked. That is worth more than it looks: nothing
else in the process reveals that a Wayland session is running an X11 window.

Anyone can try the other driver with one property, and the frame path is
instrumented (`-Dgoldberry.log.level=TRACE`) well enough to compare them.

Goldberry now has a property that changes platform behaviour, which is a category
that grows. It is deliberately named as a backend detail rather than a general
setting.

The reported problem is **not fully fixed**, and this record should not be read as
if it were. What has been established is where the remaining cost lives: not in
the toolkit's frame path, which is a few milliseconds of memory writes and one
SDL call, but in the presentation path SDL chose. The next real improvement is a
renderer-backed present, not another round of micro-optimisation above it.
