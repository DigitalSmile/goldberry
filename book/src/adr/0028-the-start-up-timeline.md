# ADR-0028: The start-up timeline

- **Status:** Accepted
- **Date:** 2026-08-15
- **Relates to:** `docs/ARCHITECTURE.md` §1, §14, [ADR-0023](0023-logging-and-the-example-as-a-subproject.md)

## Context

`docs/ARCHITECTURE.md` opens with "starts in milliseconds", and the whole
CPU-rasterization argument in ADR-0002 rests on it: no GPU context for plain UI,
because a GPU context costs start-up time. That is a claim with a number in it,
and nothing in the toolkit could say whether it was true — or, when it stops
being true, which part stopped it.

The recent resize work is the argument for building this now. Two rounds went
into the frame path on reasoning before anyone measured, and the measurement
turned out to point somewhere else entirely — twice. Start-up will go the same
way: someone will guess that the native library load is the expensive part, or
that it is class loading, and be wrong.

## Decision

`Startup` records named phases and reports them at **trace**, with a table
printed once after the first frame:

```text
start-up timeline (866.6ms to here):
     533.8ms    +533.8ms  runtime starting
     559.6ms     +25.8ms  libgoldberry mapped (1.9ms)
     722.6ms    +162.9ms  SDL video subsystem up (99.2ms)
     728.0ms      +5.4ms  backend ready (185.5ms)
     742.9ms     +12.9ms  SDL window 4 created
     749.5ms      +6.6ms  window "Goldberry — showcase" open
     866.6ms    +117.1ms  first frame presented
```

Four decisions in that shape:

**Timed from process start, not from the toolkit's first line.** `ProcessHandle`
gives the process's start instant, so the first row shows what was spent before
Goldberry ran at all. Leaving it out would flatter the number by exactly the
amount nobody can do anything about — which is the amount most worth knowing.
Deltas come from `nanoTime`, which is monotonic where a wall clock is not.

**Recorded always, reported at trace.** A mark costs a timestamp and a queue
append, so the timeline exists whether or not anyone is listening and can be
summarised on demand. Gating the recording on the log level would mean the one
run where somebody wants the answer is the one run without the data.

**Summarised once, after the first frame.** That is the moment the claim is about.
A second window is not a second start-up.

**Capped at 256 marks.** A mark accidentally left in a loop then costs a counter
increment and nothing else, and the table stays readable.

Alongside it, `Startup.logModules()` lists the Goldberry modules the JVM actually
resolved. That is not the same question as what is on the module path — a module
nothing `requires` is never resolved — and it is the first thing to check when a
package appears to be missing. Today it prints `core`, `natives` and `example`,
which correctly shows that `widgets` and `gpu` are not loaded because nothing
depends on them yet.

## Alternatives considered

**JFR events.** The JDK's own answer, with tooling, no bespoke code, and far more
than this does. It also needs a recording to be started, a file to be moved
around, and a viewer — for a question usually asked as "why did that take so
long?" in the middle of a debugging session. A trace line answers it in place.
JFR remains the right tool for the M1 benchmarks §14 asks for, which are a
different job: tracked over time, not read once.

**`System.nanoTime` alone.** Simpler and it cannot report the prologue. Half the
first measurement above is JVM start-up and Gradle's launcher; a timeline that
started at Goldberry's first line would have shown a healthy 330 ms and hidden
the 534 ms in front of it.

**Log each phase at debug and skip the table.** The individual lines are already
there at trace. The table exists because a timeline is read as deltas — the
interesting column is `+162.9ms`, and reconstructing that from timestamps
scattered through a log is exactly the sort of arithmetic people get wrong.

**Only instrument what looks slow.** That is the mistake this record exists to
prevent. The first run already produced a surprise — SDL's video subsystem costs
99 ms, which is more than mapping a four-megabyte native library by a factor of
fifty.

## Consequences

The start-up claim is now measurable by anyone, on their own machine, with one
property: `-Dgoldberry.log.level=TRACE`.

The first measurements are on the record. `SDL_Init(VIDEO)` is ~99 ms and is by
some distance the largest thing Goldberry does; mapping `libgoldberry` is under
2 ms, which is the opposite of what most people would guess. Neither number has
been optimised — knowing them is the point of this change, not improving them.

The numbers above are measured under `gradle run`, which adds a launcher and its
own JVM. A real start-up measurement needs the example launched directly. Nothing
here does that yet, so the headline figure remains unproven; what is now proven is
the shape.

`Startup` is another exported class in `natives.log` that exists for the toolkit
rather than for applications, for the same reason `Logs` is (ADR-0023): a
qualified export cannot name `:core` from `:natives`. The package is becoming the
place where that compromise accumulates, and is worth watching.
