# ADR-0678: The text scale is the application's, and changes while it runs

- **Status:** Accepted
- **Date:** 2026-11-09
- **Relates to:** ADR-0626 (the showcase's reading size), the design system's text-scale token

## Context

`WidgetRenderer.textScale(double)` has scaled text by a factor clamped to the
design system's 0.9–1.5 for a long time, but nothing reached it: a window built
its renderer without it, and neither `Application` nor `Host` offered it. A
downstream messenger needs a text-size row in its settings; the showcase
worked around the gap with a `:root` sheet of its own (ADR-0626).

## Decision

- **`Application.textScale()`**, 1 by default: the scale windows open at.
  Clamped rather than refused, and a value that is not finite is 1, because it
  is read from a settings file and must not stop a window opening.
- **`Host.textScale()` and `Host.textScale(double)`**: the scale every window
  of the application draws at, and a change to it from the next frame. One
  value for the application, as the stylesheets are: a settings window's row
  reaches the main window. A change restyles every window; the same value
  again does nothing. A factor that is not finite is refused at run time.
- **`Offscreen.textScale(double)`** draws a render, a strip or a session at a
  scale, and a session's host changes it from the next pass, so a test can
  drive a settings screen.

## Consequences

- `Host` gained two abstract methods; every host implements them (the
  window's, the session's, and the test hosts in `:widgets` and `:example`).
- Only text is scaled, as before: a box sized by its content grows, a box
  given a fixed size does not.
- Tested by `OffscreenTextScaleTest` (the builder, a session host's change
  reaching the next pass, the clamp, the refusal) and `TextScalesTest`.
