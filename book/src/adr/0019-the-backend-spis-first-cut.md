# ADR-0019: The backend SPI's first cut

- **Status:** Accepted
- **Date:** 2026-08-15
- **Relates to:** `docs/ARCHITECTURE.md` §4, §14, [ADR-0003](0003-sdl3-as-the-only-desktop-backend.md), [ADR-0007](0007-jpms-modules-enforce-the-native-boundary.md)

## Context

§4 sketches the whole SPI: windows, popups, tray icons, a clipboard, a GPU
surface, cursors, event pumping. It is a good sketch, and most of it cannot be
written yet — not because the work is large, but because there is nothing to
check it against. An interface designed against no caller is designed twice, and
the second time is after something depends on the first.

What M0 needs is narrower: a window, a way to hand it pixels, a scale factor that
is right at 125% and 150%, and enough events to resize and close.

Three things about the surrounding design push hard on the shape.

**HiDPI is not a feature to add later.** §4 requires fractional scales to be
day-1 correct. The bug this produces is famous and quiet: layout works in one
unit, rasterization in another, and mixing them looks perfect on the developer's
100% display and is wrong by half on a user's.

**macOS decides the threading model for everyone.** AppKit requires window and
event calls on the process's first thread, so `Goldberry.launch(app)` takes over
the calling thread rather than spawning one. A rule that only bites on one
platform is a rule discovered at release time.

**§3.1 forbids a raw `MemorySegment` escaping `:natives`.** But the CPU
presentation path is precisely a native buffer travelling from Blend2D to a
backend, and a copy per frame is not acceptable.

## Decision

**Only what M0 needs.** `Backend`, `BackendWindow`, `WindowSpec`, the geometry
types, a sealed `BackendEvent` with five cases, and `BackendException`. Popups,
tray, clipboard, cursors and `GpuSurface` are deferred until something needs
them. Pointer and keyboard events are deferred too, and for a stronger reason:
they need the §7 dispatch model — capture/target/bubble, pointer capture, the
`KeyEvent`/`TextEvent` split that keeps IME preedit possible — and a blank window
cannot tell us whether we got it right.

**Logical and physical pixels are different types.** `LogicalSize` is floats and
is what layout, styling and application code use. `PhysicalSize` is ints and is
what gets rasterized. `DisplayScale` is the only bridge, and it owns the one
rounding rule — round half away from zero, applied once, at the boundary. Nothing
else may reimplement it. The compiler now catches what a code review would not.

**UI-thread confinement is enforced, not documented.** The backend captures its
creating thread and throws from every method called on another. `wakeup()` is the
single exception and exists so other threads have exactly one legal way in.

**`headless` ships in `:core` and depends on nothing native.** It keeps presented
frames instead of drawing them, and everything above the SPI is testable with no
`libgoldberry` at all. It also enforces every rule the interfaces state — damage
bounds, frame-size agreement, frame coalescing, thread confinement — so those
rules have tests before the first real backend exists.

**Pixels cross as a `ByteBuffer`.** `MemorySegment.asByteBuffer()` produces one
without copying, so Blend2D's own memory reaches the backend directly while
`:core` never sees a segment. §3.1 is satisfied by the module graph, not by a
convention about who calls what.

## Alternatives considered

**Build the whole §4 sketch now.** It is written down, so it feels like a
transcription rather than a design. It is not: `Optional<BackendPopup>` implies a
decision about what a popup is, `Clipboard` implies a decision about formats and
ownership, and `GpuSurface` implies knowing how composition is driven. Writing
them against no caller produces interfaces that are hard to change precisely when
the first caller shows they are wrong.

**One `Size` type with floats everywhere.** Simpler, and it is what most toolkits
do. It also makes the HiDPI bug expressible: `present()` takes a buffer, layout
produces a size, and nothing stops the second being handed to the first. The two
types cost a conversion call at each boundary, which is the point — the
conversion is where the rounding rule lives, and it is now impossible to skip.

**Document the threading rule instead of enforcing it.** Cheaper, and a check on
every call is not free. Rejected because the failure it prevents is a crash
inside AppKit with a stack that names none of Goldberry's code, on a platform
that not every contributor has. The check pays for itself the first time it
fires.

**`headless` in its own module.** Cleaner dependency-wise, and it keeps a test
backend out of the published `goldberry-core` jar. Rejected for now because §15
fixes the module list at four, and because a test-only artifact that users cannot
depend on is a worse default than a small one they can — golden-image testing is
something applications should be able to do too.

**`PixelBuffer` as an interface.** More flexible: a backend could accept a
Blend2D image directly and skip the descriptor. It also means every backend
handles every implementation, and the validation that currently happens once in a
record constructor happens nowhere or everywhere.

## Consequences

Everything above the SPI can be built and tested with no window, no display and
no native library, on any machine. The rules the SPI states have tests before any
real backend exists, so `sdl3` inherits a conformance suite rather than a
document.

Mixing logical and physical pixels is a compile error. The cost is that
`scale.toPhysical(...)` appears at every boundary, and code that genuinely does
not care about the distinction has to pick one anyway.

The deferred surface is real debt, and it is not free to add later: `Backend` will
grow methods, and every implementation gains them at once. That is the trade
accepted here — three implementations are planned and the list is closed
(ADR-0003), so the cost of adding a method is bounded and known, while the cost
of an interface designed too early is not.

`BackendEvent` being sealed means adding pointer events will stop every
exhaustive switch from compiling until it says what it does with them. That is
the intended behaviour and it will be briefly annoying.

The headless backend's `pumpEvents` parks for its timeout rather than blocking on
a real queue, so it can report a wakeup that carries no event — which is exactly
what a real backend does, and the reason the SPI says callers must not treat the
frame loop as event-driven only.
