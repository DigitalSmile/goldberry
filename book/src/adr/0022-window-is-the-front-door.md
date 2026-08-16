# ADR-0022: `Window` is the front door

- **Status:** Accepted
- **Date:** 2026-08-15
- **Relates to:** `docs/ARCHITECTURE.md` §4, [ADR-0019](0019-the-backend-spis-first-cut.md), [ADR-0020](0020-one-ui-thread-and-virtual-threads-behind-it.md)

## Context

The SPI works, and writing against it is a chore. Opening a window meant naming a
backend, constructing an event loop, writing a `switch` over five event types,
allocating a `PixelBuffer` at the right physical size, building a `DamageRect`
list, and remembering to `present` — about forty lines before anything appeared,
every line of it identical between applications.

Worse, that surface exposes decisions an application has no business making. Which
backend? What size should the buffer be — logical or physical? Who clears the
frame request? Each has one right answer and no reason to be asked.

The SPI is not wrong; it is aimed at the wrong reader. It exists so `headless`
and `sdl3` can be peers (ADR-0003), which is a toolkit-implementer's concern.

## Decision

`Window` and `Goldberry` are the public front door, and they are enough:

```java
var window = Window.open("Hello", 960, 640);
window.onPaint(frame -> frame.fill(0xFF2E3440));
Goldberry.run();
```

Opening the first window starts the backend and the event loop on the calling
thread, which becomes the UI thread. No application names `Sdl3Backend`,
constructs an `EventLoop`, or handles a `BackendEvent`.

Painting is a callback taking a [Frame], in **logical** coordinates. The buffer,
its physical size, its format, its damage list and its lifetime are the toolkit's
business. `Frame` reuses one buffer between frames while the size holds, because
repainting is the common case.

The backend packages stay exported. An application that wants to drive the SPI
directly still can — this is a front door, not a wall.

## Alternatives considered

**A `launch(app)` callback, as §4 sketched.** `Goldberry.launch(app)` inverts
control: the toolkit calls the application. It reads well for the single-window
case and gets awkward the moment there are two windows, or a window opened in
response to a menu item. `Window.open` plus `Goldberry.run()` composes without a
lifecycle interface to implement.

**A builder for every window.** `Window.builder().title(...).size(...).build()`
is more extensible and more to type for the common case. `WindowSpec` already
exists for anything beyond title and size, so `Window.open(WindowSpec)` covers it
without a second builder.

**Let the application own the backend explicitly.** Honest, and it makes the
dependency visible. It also means every `main` starts with three lines that are
the same in every application, and the one time somebody writes them differently
is a bug. The runtime is created lazily and can still be replaced.

**Expose `PixelBuffer` in `onPaint` instead of `Frame`.** Fewer types. It also
hands the application physical pixels and a stride, which is precisely the
information that makes HiDPI code wrong — `Frame` takes logical coordinates
because that is what application code should be thinking in.

## Consequences

An application needs two types and about six lines. That is the number that
matters for a toolkit nobody has used yet.

The runtime is process-global and started implicitly. Two windows share a backend
and a loop, which is right, but it also means the first `Window.open` decides
which thread is the UI thread — surprising if it happens somewhere unexpected.
`GoldberryRuntime.install` exists for tests to substitute `headless`, and is not
public: a setter that must be called before an implicit initialization is a bad
shape to publish.

`Frame` is a placeholder. It has `fill` and `fillRect` and nothing else, because
that is what a blank window needs; Blend2D replaces it in M1 with a real canvas.
The signature — a callback receiving a drawing surface in logical coordinates —
is meant to survive that.

Building this surfaced a bug the SPI's own tests could not have found.
`present()` cleared the pending frame request, so a painter that asked for the
next frame *while painting this one* had its request wiped by the present
immediately after — every animation would have stopped after exactly one frame.
The SPI tests never repainted from inside a frame; the showcase did it on its
first run. A frame request is now consumed when its `FrameDue` is delivered,
which is what "requested" meant all along, and both backends have a test for it.

`PixelBuffer` now normalises its `ByteBuffer` to little-endian. The format is BGRA
in *memory order*, so a `0xAARRGGBB` int write only lands correctly on a
little-endian view — and both `ByteBuffer.allocate()` and
`MemorySegment.asByteBuffer()` default to big-endian. The old showcase was writing
its channels backwards and painting a translucent blue nobody had looked at
closely enough to question.
