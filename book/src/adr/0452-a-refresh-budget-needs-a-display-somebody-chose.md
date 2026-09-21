# 452. A refresh budget nobody has measured is not a gate

Date: 2026-09-21

## Status

Accepted.

## Context

[ADR-0342] gave the showcase a verdict. Each native image runs for three
hundred frames with its window walked a pixel at a time, the launcher counts
every display refresh that went by with a frame wanted and undelivered, and
`--late-budget=30` turns that count into a non-zero exit. `showcase.yml` ran
all three platforms that way, under a comment that said:

> The budget is a tenth of the frames, and it is a ceiling on a GPU-less
> virtual machine painting into a software X server, Cocoa or D3D — evidence
> about three platforms' drivers, not a claim about hardware.

It was not evidence about any of them, and the first run to reach the check
said so. macOS painted its three hundred frames and failed:

```
over the late-frame budget of 30: 300 frame(s) painted, 197 late;
paint mean 7.05 ms, worst 331.22 ms; display 60.0 Hz
```

The natural reading is a regression, and it is wrong. Re-running the last green
showcase — run 20, at `64596a97`, whose own logs had expired — showed what the
macOS leg used to do:

```
./goldberry-showcase-macos-aarch64 --frames=3
```

Three frames, no walk, no budget. The 300-frame budgeted run arrived *with*
ADR-0342 and after that green run, so run 21 was the first time macOS was ever
asked, and it has never passed. Nor has anyone else: Windows has never got past
building `libgoldberry` ([ADR-0450]), and Linux has never got past its own
missing foreign registration ([ADR-0451]). **No leg on any platform has
completed the run.** The number 30 was reasoned about, not measured.

So what went wrong is not a frame rate. It is that a ceiling was written for
three machines on evidence from none, and then made a verdict.

## Decision

**No budget is asserted, on any platform. All three run the identical walk and
report what it cost.**

This is the second answer. The first kept the budget on Linux, on the grounds
that `showcase.yml` pins that display and a refresh budget is a number about a
display:

```yaml
xvfb-run -a --server-args="-screen 0 1920x1200x24"
```

The first Linux run to get past its own missing foreign registration
([ADR-0451]) and its missing font ([ADR-0453]) printed the line that disproves
it:

```
frames: 302 frame(s) painted, 75 late; paint mean 10.14 ms,
worst 1799.59 ms; display 0.0 Hz
```

**`display 0.0 Hz`.** Xvfb pins the geometry and reports no refresh rate at
all, so `adoptDisplayRate` adopts nothing, the pacer keeps its default
interval, and "late" counts missed ticks of a software timer. That is exactly
what it counts on macOS. The carve-out was reasoning from a premise the
evidence does not support, and it is left in this record rather than edited
out because the premise was checkable before the run and was not checked.

What the legs actually measure, now that each has produced a number:

| leg | frames | late | paint mean | worst | display |
|---|---|---|---|---|---|
| `linux-x64` | 302 | 75 | 10.14 ms | 1799.59 ms | 0.0 Hz |
| `macos-aarch64` | 300 | 200 | 6.42 ms | 200.26 ms | 60.0 Hz |

Both are far over 30, neither is a regression, and on each the worst frame is
start-up rather than anything the walk did. Two samples locate a ceiling no
better than none: a budget has to sit above what the machine does and below
what a regression does, and nothing here says where either edge is.

### What still fails the step

`grep -q "painted 300 frame(s); exiting"`. A hang, a crash, an image that will
not start, or one that dies part-way — as Linux did on the emoji font — all
still turn the job red. That is most of what the step was ever catching; the
budget caught nothing, because it had never once been under.

### A `--frames=300` run said "exiting" three times

Found in the same log and fixed here, because it is the sort of thing that
makes a number untrustworthy:

```
painted 300 frame(s); exiting
painted 301 frame(s); exiting
painted 302 frame(s); exiting
```

`Goldberry.stop()` ends the loop; it does not unschedule the frames already in
flight, and by then the resize walk's zero-delay timer and an animating
renderer have each asked for one. Those frames still paint, and each re-ran the
`painted >= frames` branch. The launcher latches now, so the line is logged
once. The count still reads 302 rather than 300, which is honest: three
hundred and two frames were painted.

**Unguarded by a test, deliberately.** The only observable difference is the
log line — the latch changes no frame, no count and no exit code — and `:core`
has no log-capture harness and no logback on its test classpath. Adding one
for a single assertion about a line of INFO is a worse trade than saying here
that this one is held by review. `LauncherEvidenceTest` already covers the
thing that could actually break, which is that a frame-limited run still
terminates.

## Consequences

**The showcase can go green on all three platforms** for the first time since
the 300-frame walk was added.

**No frame regression is caught automatically**, and that is a real loss said
plainly. What replaces it is the summary line in each job's step summary, which
a human reads. The honest position is that nothing was caught automatically
before either — the budget had failed every single time it ran, on every leg
that reached it, which catches nothing and hides everything behind a red tick
that means "this runner is a VM".

**There is now a baseline.** Two legs have reported, and the third will. Once
there are several green runs the numbers can be looked at as a distribution and
a ceiling set from the top of it — per platform, since 75 and 200 are clearly
not the same machine. The note in `showcase.yml` says so and carries the
numbers.

**The ADR-0342 mechanism is untouched.** `--late-budget=N` still exists, still
throws `FrameBudgetException`, and is still what a developer runs locally
against a real display, which is where it was always meaningful. What changed
is that CI stopped asserting a number nobody had measured.

[ADR-0342]: 0342-a-window-is-resized-from-outside-and-the-run-says-what-it-cost.md
[ADR-0453]: 0453-a-face-that-moved-module-takes-its-declaration-with-it.md
[ADR-0450]: 0450-the-webview2-runtime-ships-with-windows-its-headers-do-not.md
[ADR-0451]: 0451-a-shape-is-declared-where-it-can-be-reached-not-where-it-is-linked.md
