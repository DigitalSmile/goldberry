# Closing M5

Working notes for what `book/src/status.md` lists as still open under **M5 —
Hardening** on 2026-09-17, and for the widget work that follows it. One ADR per
decision, in `book/src/adr/`.

Status legend: **done** — code, tests and ADR landed. **in progress** — being built
now. **open** — not started. **not ours** — needs a person and an account, not code.

## M5

| Item | What it asks for | ADR | Status |
|------|------------------|-----|--------|
| Frame evidence: a window resized from outside | `BackendWindow.resize`, `Window.resize`, `--resize=WxH` | 0342 | done |
| Frame evidence: a run that says what it cost | a summary line at exit, `--late-budget=N` | 0342 | done |
| Frame evidence: a ceiling on three runners | `showcase.yml` paints 300 frames while resizing and fails over budget | 0342 | done |
| Licence texts vendored | `checkLicenses -Pgoldberry.releaseCheck=true` passes | 0015 | done |
| Javadoc's doclint errors | published with the lint on | 0343 | done |
| Pruning old showcase snapshots | delete the `goldberry-showcase*` packages ADR-0340 stopped publishing | — | not ours: needs a token with `delete:packages` |
| Central's side | namespace, snapshots, token, signing key, secrets | — | not ours: `docs/releasing.md` §One-time setup |

## Widgets

| Widget | Spec | ADR | Status |
|--------|------|-----|--------|
| `steps` | `docs/core-widgets.md` §6 | — | open |
| `wizard` | `docs/core-widgets.md` §6 | — | open |
| `timeline` | `docs/core-widgets.md` §10 | — | open |
| `link` | `docs/core-widgets.md` §2 | — | open |
| `button` `outlined`, `square`, `circle`, `float` | `docs/core-widgets.md` §3 | — | open |

## What each one touched

### The frame evidence

- `core` `render/window/BackendWindow#resize` — the SPI method, a request; `BackendPopup#resize`
  now overrides it.
- `core` `render/backend/sdl3/Sdl3Window#resize` — `SDL_SetWindowSize`, rounded.
- `core` `render/backend/headless/HeadlessWindow#resize` — clamped to the floor, applied when
  the `Resized` is delivered; the deferral moved up from `HeadlessPopup`, and `HeadlessBackend`
  delivers it for either.
- `core` `Window#resize` — the public face. `Window#launcherOnResize` / `#launcherOnMove` —
  the launcher's own hooks, so `onResize` and `onMove` are the application's.
- `core` `drive/ResizeWalk` — a pixel a frame there and back, from the window's own size.
  `drive/FrameBudgetException` — thrown after shutdown, past the budget.
- `core` `stats/FrameSummary`, `FrameStats#summary`, `FrameRing`'s three totals.
- `core` `Launcher` — `--resize=WxH`, `--late-budget=N`, the walk on a zero-delay timer after
  each frame, the summary line at exit.
- `example/build.gradle` — `-Pgoldberry.example.resize=`, `-Pgoldberry.example.lateBudget=`.
- `.github/workflows/showcase.yml` — 300 frames, the walk, the budget, the step summary.
- Tests: `core` `WindowResizeTest`, `LauncherEvidenceTest`, `LauncherOptionsTest`,
  `drive/ResizeWalkTest`, `drive/FrameBudgetExceptionTest`, `stats/FrameSummaryTest`,
  `stats/FrameRingTest`.

### The javadoc

- `build-logic` `goldberry.publish.gradle` — `-Xdoclint:all,-missing`.
- `natives` — 425 `@param`/`@return`/`@throws` lines moved from thirty `…Calls` holder classes
  onto their `call` methods, under a summary naming the C function.
- Twenty `[links]` qualified, corrected or made code spans, across `core`, `widgets`, `html`
  and `natives`; `PointerRouter.CaretAreaSink`'s tags moved onto `accept`.

### The licences

- `licenses/*.txt` — the seven placeholders replaced by the verbatim upstream file from the
  superbuild's pinned checkout, with the `NOT-VENDORED` marker gone and the revision named.
