# 452. A refresh budget needs a display somebody chose

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

**The budget is asserted where the display is pinned, which is Linux and only
Linux.**

The reason is already in the workflow, three lines under the budget:

```yaml
xvfb-run -a --server-args="-screen 0 1920x1200x24"
```

A budget counts refreshes missed *against a display*. That is a number about a
particular screen at a particular size and rate, so it compares across runs
only where those are the same across runs — and Xvfb is the only one of the
three this workflow chooses. macOS and Windows take whatever the runner has:
the macOS window maximized to 1920×942 because that is the VM's screen, and
nothing in this repository asked for it or would notice if it changed.

macOS and Windows run the **identical** walk and report their summary into the
step summary. They do not turn it into a verdict. The `painted 300 frame(s)`
grep still fails them, so a hang, a crash or an image that will not start is
still a red job — which is most of what the step was for.

### Why not give macOS a budget of its own

Because the only honest number would be an invented one. There is a single
macOS measurement — 197 — and a budget has to sit above what the machine does
and below what a regression does. One sample locates neither edge. Picking a
number from it would repeat exactly the mistake this record is about, with one
data point instead of none.

### Why not lower the frame count on macOS instead

The walk is the interesting load and the reason ADR-0342 exists; running fewer
frames would keep the verdict and throw away the measurement, which is backwards.
macOS keeps the full three hundred and reports what they cost.

## Consequences

**The macOS and Windows showcase legs can go green**, on the same work they do
now.

**A macOS frame regression stops being caught automatically**, and that is a
real loss rather than a technicality. What replaces it is the summary line in
the job's step summary, which a human reads. The honest position is that it was
never caught automatically — the check has failed every time it has run — and a
gate that is always red catches nothing either.

**Linux keeps the verdict**, and once it has run green a few times there will
be, for the first time, a measured distribution to set a ceiling from. That is
also the moment to reconsider macOS and Windows: the note in `showcase.yml`
says so, and says to tighten it once there are runs to set one from.

**The workflow's comment no longer claims evidence it does not have.** That
claim is what made a guess look like a measurement, and it survived review
because it was confidently phrased.

[ADR-0342]: 0342-a-window-is-resized-from-outside-and-the-run-says-what-it-cost.md
[ADR-0450]: 0450-the-webview2-runtime-ships-with-windows-its-headers-do-not.md
[ADR-0451]: 0451-a-shape-is-declared-where-it-can-be-reached-not-where-it-is-linked.md
