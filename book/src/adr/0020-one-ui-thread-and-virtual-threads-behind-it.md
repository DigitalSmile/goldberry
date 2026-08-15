# ADR-0020: One UI thread, virtual threads behind it

- **Status:** Accepted
- **Date:** 2026-08-15
- **Relates to:** `docs/ARCHITECTURE.md` §4, §5, [ADR-0019](0019-the-backend-spis-first-cut.md)

## Context

The UI thread is fixed by a platform: AppKit requires window and event calls on
the process's first thread, so Goldberry takes over the calling thread rather
than spawning one, and the SPI enforces single-thread confinement everywhere
(ADR-0019).

That settles *where* UI work runs and leaves the harder question. A toolkit with
one UI thread is a toolkit one slow call away from a frozen window. Reading a
file, loading a font, waiting on a service — each is milliseconds most of the
time and seconds occasionally, and the occasional case is the one users
remember. So work has to leave the UI thread, and its results have to come back
to it, because only it may touch a window.

Every toolkit solves this and most solve it the same way: a `invokeLater` /
`runOnUiThread` / `Platform.runLater` primitive, and a rule that callers must
remember. The rule is the problem. It is invisible at the call site, the failure
is a race rather than an exception, and it reproduces on someone else's machine.

## Decision

`EventLoop.supplyAsync` runs work on a **virtual thread** and completes its
future **on the UI thread**. Every `thenAccept`, `thenApply` and `whenComplete`
downstream therefore already runs where it is allowed to touch a window, without
anyone writing a hand-off:

```java
loop.supplyAsync(() -> loadTheThing())
    .thenAccept(thing -> window.setTitle(thing.name()));   // on the UI thread
```

`UiExecutor` is the primitive underneath, and it is an `Executor`, so anything
that takes one can target the UI thread. It queues from any thread and wakes the
loop — enqueue first, then wake, because the other order races: the loop can
wake, find nothing, and park again before the task lands.

The loop drains queued work **before and after** each pump. Before, so work
posted while it was parked reaches the frame this pump produces rather than the
next one. After, so work posted by an event handler does not wait for the next
platform event — which, on an idle desktop, may be never.

A drain runs one generation of tasks, not until the queue empties. A task that
posts another runs on the next drain, so a self-scheduling animation cannot
starve events.

Virtual threads rather than a pool because the work this exists for is mostly
*waiting*. A pool sized for CPU throughput is the wrong shape for a hundred
blocked reads, and getting the size right means guessing at a workload the
toolkit does not know. A blocked virtual thread costs a continuation.

## Alternatives considered

**A plain `invokeLater` and nothing else.** Every toolkit has one and it is
genuinely sufficient. It also makes the correct pattern the verbose one: the
caller writes the background dispatch, the hand-off back, and the error path, and
gets one of the three wrong eventually. `supplyAsync` is that pattern with the
ceremony removed; `ui().execute` is still there for the cases it does not fit.

**Complete futures on the background thread and let callers hop.** This is what
`CompletableFuture.supplyAsync` does by default, and it is why so much UI code
has an `invokeLater` inside a `thenAccept`. It also means the *default* is wrong
— a callback that touches a window works in testing and corrupts state under
load. Defaults should be safe and explicit escapes should be available, not the
reverse.

**A fixed thread pool.** Predictable, bounded, and familiar. It also needs a size,
and every size is wrong for some application: too small and a few blocked reads
stall the rest, too large and a CPU-bound task swamps the machine. Virtual
threads make the question not need an answer.

**Render on a second thread.** The largest source of UI-thread time is
rasterization, and moving it is the obvious win. It is also not ours to move:
Blend2D already rasterizes on its own worker threads (§5), and the UI thread
hands over a finished buffer. Adding another layer of threading above that would
be inventing a problem.

**Bound the task queue.** Unbounded queues are a memory leak waiting to happen.
Bounding it means either blocking the producer — reintroducing the stall this
exists to prevent, on a thread that has no idea it is doing UI work — or dropping
UI updates, which is worse than either. A producer that outruns the UI thread is
a bug in the producer, and unbounded makes it show up as memory growth rather
than as a mysterious freeze.

## Consequences

The safe thing is the short thing to write, and the unsafe thing raises an
exception naming the thread it was called from rather than corrupting state
quietly.

`close()` does not wait for background work. An application that cannot exit
because a download is still running is a worse failure than a task that never
delivers its result, and the result has nowhere to go once the UI is gone.

The queue is unbounded, so a runaway producer grows the heap. That is a
deliberate trade and it is the kind of bug a heap dump answers in a minute.

Frame requests are still synthesized rather than vsync-aligned. `requestFrame()`
sets a flag and the next pump turns it into a `FrameDue`, so a requested frame
arrives promptly but not in step with the display. Real vsync alignment needs a
renderer to align to, and it will change this file rather than the SPI.

An event-loop test that never terminates hangs the build rather than failing it —
JUnit's `@Timeout` defaults to the same thread and cannot interrupt an infinite
loop. `EventLoopTest` runs a watchdog that stops the loop after twenty seconds,
which turns that mistake back into an ordinary assertion failure. Every test
driving a loop needs one.
