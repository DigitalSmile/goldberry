# Closing G39–G43, and three kinds of motion

Working notes for the seventh batch of `docs/gaps.md` entries, which arrived on
2026-09-17, and for the three animation mechanisms asked for alongside them.
One ADR per entry, in `book/src/adr/`.

Status legend: **done** means the code, tests and ADR have landed. **in progress** means it is being built now.
**open** means not started.

| Item | What it asks for | ADR | Status |
|------|------------------|-----|--------|
| G39  | faces an application ships, reachable from `font-family` | [0349](../book/src/adr/0349-a-face-an-application-ships-is-found-after-the-bundled-ones.md) | done |
| G40  | a window icon | [0351](../book/src/adr/0351-a-window-icon-is-several-sizes-and-the-backend-picks-the-base.md) | done |
| G41  | a `canvas` that can ask for its next frame | [0348](../book/src/adr/0348-a-canvas-asks-for-its-next-frame-with-what-it-was-painted-with.md) | done |
| G42  | hand a URL to the desktop | [0346](../book/src/adr/0346-a-link-is-a-word-and-the-desktop-opens-the-rest.md) | done (before this batch) |
| G43  | `text-area`'s gutter strip does not reach its padding | [0350](../book/src/adr/0350-a-gutter-strip-is-outside-the-clip-its-numbers-are-inside.md) | done |
| M1   | an entering state: `@starting-style` | [0352](../book/src/adr/0352-an-element-enters-from-its-starting-style.md) | done |
| M2   | `@keyframes` and `animation` | [0353](../book/src/adr/0353-a-stylesheet-may-name-keyframes.md) | done |
| M3   | a choreographed settle, painted on a canvas | [0354](../book/src/adr/0354-a-choreography-is-a-function-of-time-and-a-timer-wakes-it.md) | done |

## What each one touched

### G41 — a canvas that asks for its next frame

- `core` `widget/style/Paints#isAnimating(ComputedStyle, Context)`, defaulting to the
  no-argument form. `WidgetRenderer` calls it inside the window `render` runs in.
- `widgets` `core/canvas/Canvas` gains a fourth component, `animating`, with
  `Canvas.animating(Predicate<CanvasStyle>)`. The three-component constructor is kept.
- Tests: `widgets` `core/canvas/CanvasAnimatingTest`.

### G39 — faces an application ships

- `core` `assets/Face`: a sealed interface over `BundledFont` and `FontSource`, which
  owns the one matching rule (`Face.match`). `BundledFont.of` delegates to it.
- `core` `text/font/FontSource`: a record plus `resource(…)` and `of(…)` factories.
- `core` `text/font/Fonts.bundled(List<FontSource>)`: bundled faces are searched first,
  shipped faces are opened lazily, and an unreadable one is logged once and falls back
  to the UI face.
- `core` `Application#fonts()`, read by `Launcher` when the book is opened, and
  `offscreen/Offscreen#fonts(List<FontSource>)`.
- Tests: `core` `text/font/ShippedFontsTest`, `widgets` `text/ShippedFaceTest`.

### G43 — the gutter strip, and a wrap width

- `widgets` `form/textarea/TextAreaBox#render` draws two layers: a clipped content
  layer on the content box, and the strip beside it, inset from the border's
  inner edge with fitted corners.
- `widgets` `form/textarea/AreaPadding` replaces the two-edge record. `TextAreaState`
  subtracts each edge once from the wrap width and the visible height.
- The drifting numbers in the report were **not reproduced** against this checkout.
  ADR-0350 says so.
- Tests: `widgets` `form/textarea/TextAreaGutterStripTest` (pixels). `TextAreaGutterTest`
  now looks for the numbers in the content layer.

### G40 — a window icon

- `natives`: `SDL_SetWindowIcon` and `SDL_AddSurfaceAlternateImage` on the export list,
  bound as optional in `SdlWindowCalls` and `SdlSurfaceCalls`.
  `sdl/window/SdlIconImage` and `SdlVideo#setWindowIcon` hang the alternates off the
  base and destroy every surface before returning.
- `core` `render/window/IconImage` (straight alpha) and `BackendWindow#setIcon`.
  `Sdl3Window` picks the base (the smallest size ≥ 48, else the largest), and
  `HeadlessWindow` records the sizes.
- `core` `Window#icon(List<Image>)` and `Application#icon()`. `Launcher` sets the icon
  straight after the window opens.
- `example` `brand/ShowcaseIcon`: the showcase's own icon, computed at four sizes.
- Tests: `natives` `sdl/SdlWindowIconTest`, `core` `WindowIconTest` and
  `render/backend/sdl3/WindowIconOrderTest`, `example` `brand/ShowcaseIconTest`. The
  showcase was run once on a Wayland session with the icon set, with no error.

### M1 — `@starting-style`

- `core` `css/parse/CssParser`: the block form, which marks its rules `StyleRule.starting`.
- `core` `css/cascade/StyleResolver`: starting rules are kept in their own buckets.
  `hasStartingStyles()` and `resolveStarting(element)` are added.
- `core` `widget/Element#firstStyled` and `widget/WidgetRenderer`: on an element's first
  styled frame, the renderer observes the starting style before the real one.
- `widgets` `controls.css`: `button.float` enters from `opacity: 0; transform: scale(0.9)`,
  and a `button.float:active` rule keeps the press instant.
- Tests: `core` `css/parse/MotionAtRulesTest`, `css/cascade/MotionCascadeTest` and
  `widget/EnteringAndKeyframesTest`, and `widgets` `controls/button/FloatEntranceTest`.

### M2 — `@keyframes` and `animation`

- `core` `css/Keyframes`, and `Stylesheet.keyframes` (the two-component constructor is kept).
- `core` `css/cascade/KeyframeAnimations`: seven lists, `entries()`, and `reduced()`.
- `core` `css/ComputedStyle`: an `animations` component, the shorthand and its seven
  longhands, and `applied(declarations, context)` for layering a keyframe onto a style.
- `core` `css/cascade/StyleResolver`: `keyframes(name)` (a later block wins) and
  `resolveKeyframe(element, frame)`.
- `core` `motion/KeyframeTrack` (resolution and CSS's timing model) and
  `motion/Animatables` (the read, write, compare and interpolate switches, moved out of
  `Animations` so both layers share them). `motion/Animations#animate` runs beneath
  transitions.
- Tests: `core` `css/AnimationPropertyTest`, `motion/KeyframeTimingTest` and
  `widget/EnteringAndKeyframesTest`, and `widgets` `ToolkitLoopsTest` (§1.7's rule 4 for
  the toolkit's sheets).

### M3 — the settle

- `example` `motion/Settle` (a pure pose function), `motion/TileFloor` (the floor, its
  swaps, and `isMoving`) and `motion/Rotated` (turning a path without a frame transform).
- `example` `ui/MotionScreen`, the gallery's twelfth screen and its last, so no digit
  shortcut moves. Its keyframes and starting style are in `showcase.css`.
- Tests: `example` `motion/SettleTest`, `motion/TileFloorTest`, `motion/RotatedTest` and
  `MotionScreenTest`. `ShowcaseShellTest` names the new screen.

## Goldens

Thirteen gallery goldens were re-recorded, and each difference is accounted for.
The tab strip has a twelfth tab, **Motion**, which is 248 pixels on every screen.
The two Forms goldens also changed where the numbered `text-area`'s gutter strip
now reaches the border (G43), which is the band the entry reported, gone. The full
`./gradlew build` is green. `FrameBudgetTest`'s icon-sheet budget failed once under
the parallel build and passed when run alone, as it is known to.

## Left open, and then closed

Three things were written down as not done when the batch landed. All three are
closed in [ADR-0355](../book/src/adr/0355-a-button-leaves-a-field-counts-both-paddings-and-a-floor-starts-on-its-first-frame.md):

| Item | Status |
|------|--------|
| `text-input` subtracted `2 × left padding` for its room | done: `TextEditor.laidOut` takes both edges. Test: `widgets` `form/textinput/AsymmetricPaddingTest` |
| A floating button had no way out | done: `FloatSlot` plus `button.float.leaving`, removed by a host timer after `--gb-motion-fast`. Tests: `ButtonShapeTest`, `FloatEntranceTest` |
| The Motion screen's floor was blank offscreen, so it had no golden | done: `TileFloor.at(now)` is called from the canvas's predicate on every render. Golden: `gallery-motion`. Test: `TileFloorTest` |

Still not done, and not an item: G43's reported number drift did not reproduce
here. If it survives a snapshot with ADR-0350 in it, it is a new entry.
