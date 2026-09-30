# 492. A window says whether it presents through the GPU

Date: 2026-09-30

## Status

Accepted. Builds on
[ADR-0480](0480-windows-present-through-the-gpu-by-default-and-on-the-cpu-where-it-cannot.md),
which promised that "the log says which and why", and
[ADR-0491](0491-a-page-under-x11-keeps-its-window-on-the-gpu.md).

## Context

Whether a window presents through the GPU or on the CPU depends on the machine
as much as the application. There may be no `:gpu` on the module path, no
device, or a driver that refuses the window. A property may say so, a page may
be embedded where stacking is unmeasured, or a GPU may fail mid-run. ADR-0480
logged it, but a reader could not easily find it:

- **Six phrasings in two classes.** "presents through the GPU", "presents on
  the CPU: …", "presents on the CPU from now on: …", "presents on the CPU
  again: …", from `Sdl3Window`. "GPU device ready …" and "no GPU device …",
  from `SdlCompositor`. Nothing common to search for.
- **Silent cases.** A window with no GPU compositor on the module path set
  its reason and logged nothing. Neither did a popup, nor a policy of `never`
  or `off`.
- **A misleading total.** The exit line "presents: N frame(s) composited"
  counts only GPU presents that carried new pixels. A still window on the GPU
  presents with nothing to upload, so ADR-0491's web tab read "2 frame(s)
  composited" after 400 frames on the GPU.
- **Nothing an application could ask.** The showcase could not show which
  path it was on, and no test could assert it.

## Decision

**`Presentation` is a public value in `render.window`:** sealed, `Gpu(driver)`
or `Cpu(reason)`. `path()` is `GPU` or `CPU`, `describe()` is the log's long
form and `label()` a status bar's short one. `Presentation.Cpu.UNDECIDED`
stands before a first frame.

- `BackendWindow.presentation()` defaults to the CPU, "this backend has no
  GPU path".
- `Sdl3Window` answers from what it does. While composited, it is the GPU
  driver (`CompositedWindow.driver()`). Otherwise it is its own reason when
  something put it on the CPU (a refused claim, a failed present, a page).
  When nothing did, it is the policy's (`Composition.whyOnTheCpu`) or a
  popup's.
- `HeadlessWindow` is on the CPU, "headless: nothing is shown on a screen".
  `presentAs` lets a test change that.

**`Window` watches it, after every present.** `Window.presentation()` is the
last frame's. `onPresentationChange` hears each change, the first frame's
included. `presentations()` counts every presented frame by path. Asking after
the present is what makes it cheap: a field compare per frame, and no event
from the backend.

**One log line per change, tagged.** `Window` logs each change at INFO:

```text
[GPU] "Goldberry — showcase on Linux / amd64" presents through the GPU (vulkan)
[CPU] "Goldberry — showcase on Linux / amd64" now presents on the CPU: no GPU layer on screen (goldberry.gpu.composite=auto)
```

`Sdl3Window`'s transition lines are DEBUG now, so each change is said once.
Its warning for a failed GPU present stays, with the exception. The device
lines are tagged `[GPU] device ready …` and `[CPU] no GPU device …`, and the
backend's policy line starts `presentation policy:`.

**The exit summary says where frames went.** A new line, `on screen: 385
frame(s) through the GPU (vulkan), 0 on the CPU`, comes before the cost line,
which now reads `385 GPU present(s) with new pixels; upload mean …`.

**The showcase's bar shows it.** A badge, `class="info"`, bound to
`app.presentation` and set from `onPresentationChange`: `GPU · vulkan`, or
`CPU ·` and the reason.

## Consequences

- Checked on linux-x64 on the showcase's web tab. Under `always`: `[GPU]` at
  the first frame, and `on screen: 385 frame(s) through the GPU (vulkan), 0 on
  the CPU`. Under `auto`: `[CPU]` at start, `[GPU]` when a layer showed,
  `[CPU]` again six seconds later, and `370 … through the GPU (vulkan), 473 on
  the CPU`, with the page open and exit 0. That last switch is ADR-0491's.
- The bar changes 21 gallery goldens by 16 pixels each: the badge shows `…`,
  since an offscreen render never presents. `ShowcaseDocumentsTest` names the
  new badge.
- `CompositedWindow` has one more method to implement, `driver()`. `:gpu`'s is
  the only implementation.
- Tests: `PresentationTest`, `PresentationTallyTest`, `WindowPresentationTest`
  (decided at the first frame, heard once per change, counted every frame),
  and `CompositedBackendTest` asserting `Gpu` with a compiled-in driver and
  `Cpu("goldberry.gpu=off")`.
- A long CPU reason makes a long badge. Under `auto` it is the property's
  whole explanation. The bar has the room, and the reason is the useful part.
