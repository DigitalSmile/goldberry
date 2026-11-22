# Selection, damage and frame pacing (2026-11-22)

Working notes and status for three fixes asked for together before going on.
The records are ADR-0728 to ADR-0730, parked in `docs/snapshot/adr/`; the
guide pieces are parked in `docs/snapshot/guide-styling-css-subset.md` and
`docs/snapshot/guide-windows-activity.md`. Next free ADR: 0731.

## Status

| # | Item | State | Where |
|---|---|---|---|
| 1 | `user-select` ignored, text cannot be selected | **done**, tests green | ADR-0728: `input.select.TextSelection` (exported), `input.TextSelecting`, `WidgetRenderer.selectable`, `RichTextState`/`RichTextBox` read the property |
| 2 | every icon is damage on every frame; past 8 rects the whole frame | **done**, tests green | ADR-0729: `IconPainter`, `SparklinePainter`, record painters in `ColorPlane`, `ColorRamp`, `QrCode`, `ImagePaint`, `Picture`; `Icon.equals`; `paint.tree.DamageMerge` |
| 3 | animating window paints at full rate hidden, unfocused or on battery | **done**, tests green; hand checks owed | ADR-0730: `frame.pacing.AnimationPacing`, `BackendEvent.OcclusionChanged`, `Window.isOccluded/onOcclusionChanged/onFocusChanged/powerState/onPowerChanged/inactiveFrameCap`, `:window-inactive`, `DesktopPower`, `SDL_GetPowerInfo`, native ABI 30 |

## Decisions taken without asking

- `user-select: auto` stays unselectable on a plain `text` (a label is pressed).
  `text`/`all` opt in, and inheritance covers a panel.
- A press on a control (a focusable `Handles` on the chain) keeps the selection,
  like a browser's toolbar button; other primary presses clear it.
- An unfocused window is **not** throttled by default (video, a second monitor);
  `Window.inactiveFrameCap(hz)` opts in. Occluded windows always pause their
  animation frames; a saving mode caps them at 30 Hz.
- Battery alone changes nothing the toolkit does; it is reported.
- Painters that read live data at paint time (the mic views, `VideoSurface`)
  stay lambdas on purpose.

## Tests added

`TextSelectionTest`, `UserSelectTest`, `UserSelectCopyTest`, `DamageMergeTest`,
`DamageTest.tooManyRegions` (rewritten), `IconEqualityTest`, `IconPainterTest`,
`SparklinePainterTest`, `AnimationPacingTest`, `WindowActivityTest`,
`Sdl3EventPathTest.occlusionEventsReachTheSink`, `DesktopPowerTest`,
`PowerProfilesTest`.

## Left

- Hand checks: an occluded window going quiet on macOS and X11 (and staying
  visible on Wayland); macOS Low Power Mode and Windows battery saver turning
  the 30 Hz cap on; power-profiles-daemon on a GNOME laptop.
- A selection across several boxes (a browser's range across elements) is not
  built: one box's words at a time.
- The CI natives must rebuild for ABI 30 (the shim and export list changed).

## Log

- 2026-11-22: built and tested; committed after the Snapshot #89 fix (cf7a10ac, PipeWireHeadersTest on Windows). CI has not run on the batch yet.
