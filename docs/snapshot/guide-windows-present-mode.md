# Guide addition held back until the release: present mode and frame cap

For `book/src/guide/windows.md`, a new section after `## Fullscreen` and before
`## Where a window opens`. And one line in `book/src/performance/measuring.md`,
at the end.

## Vertical sync and a frame cap

A window presents in a `PresentMode`. `VSYNC` waits for the display's vertical
blank, which is the default and what every window has. `MAILBOX` shows the
newest frame at the blank and drops the older ones, so the loop never waits.
`IMMEDIATE` shows each frame at once and may tear. A game's options screen
switches between them while it runs:

```java
window.presentMode(PresentMode.MAILBOX).frameCap(144);
var inEffect = window.presentMode();
```

A driver without the mode asked for presents in the nearest it has: `MAILBOX`
falls back to `IMMEDIATE`, then `VSYNC`. `presentMode()` says which mode is in
effect, which is what an options screen shows. A window presenting on the CPU
keeps the request and presents as its window surface does until it moves to
the GPU.

The frame loop follows the mode. A window in `VSYNC` is painted at most once a
refresh. A window in `MAILBOX` or `IMMEDIATE` is painted as fast as it asks,
and `frameCap(hertz)` limits it. Zero lifts the cap. A cap above the display's
rate changes nothing in `VSYNC`. A frame the cap holds back is not counted
late, and a `hud`'s budgets are a share of the cap where it is slower than the
display. The `pacing` reading shows both settings.

---

For `book/src/performance/measuring.md`, after the `goldberry.backend.vsync`
row's table, one sentence:

`goldberry.backend.vsync=false` makes every window open asking for `MAILBOX`,
and an application changes it from then on with `Window.presentMode`.
