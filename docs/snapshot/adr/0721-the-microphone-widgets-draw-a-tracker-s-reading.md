# ADR-0721: The microphone widgets draw a tracker's reading

- **Status:** Accepted, as phase 3 of `docs/camera-and-mic.md` (W3.4,
  Tessera's T-05). Builds ADR-0719's D9 over ADR-0720's sample sources.
- **Date:** 2026-11-22
- **Relates to:** ADR-0719 (the plan's decisions), ADR-0720 (sample sources
  and the analysis), ADR-0190 (content modules)

## Context

`ARCHITECTURE.md` §11.1 names three microphone widgets, `level-meter`,
`waveform-view` and `spectrum-view`, and D9 took all three in this work. What
was left to decide is what each is to the element tree and to a stylesheet,
how a block heard on the source's thread reaches the UI thread, where the
colours come from, and how they are golden-tested with no device. Tessera
also draws a recorded voice message's outline, which no live source feeds.

## Decision

1. **Two halves per widget.** The `@Markup` widget in `dev.goldberry.mic.view`
   (`LevelMeter`, `WaveformView`, `SpectrumView`) is stateful: it makes a
   tracker of ADR-0720's analysis, subscribes it to its `SampleSource` while
   mounted, and builds a leaf from the tracker's reading. The leaves in the
   exported `dev.goldberry.mic.view.draw` (`LevelBar`, `WaveformPlot`,
   `SpectrumPlot`) paint one reading and carry the widget's node name as their
   CSS type, as the surface a `PictureView` builds is a `video-view`.
2. **A redraw is coalesced.** The listener feeds the tracker on the source's
   thread and hands one rebuild to `Goldberry.ui()`, and no other until that
   one has run, however many 10 ms blocks arrive in between. A widget given
   another source, or a waveform another window, starts a fresh tracker; one
   shown again starts from silence.
3. **Colours are component tokens read as the leaf renders**
   (`Paints.Context.color`): `--gb-mic-level`, `-hot`, `-clip`, `-hold`,
   `-wave`, `-wave-ahead`, `-spectrum`, set in `mic.css` from the theme's
   tokens, with Nord's values as the fallback without a sheet. Sizes and the
   meter's track are ordinary CSS on the node. `mic.css` is in the toolkit's
   base layer, in a package opened to `:core` and declared for native images.
4. **The scales are fixed**: a meter is 60 dB long and turns hot at −6 dBFS
   and clipped at −1; a spectrum is 90 dB tall over log-spaced bands from
   50 Hz to 16 kHz or 0.95 of Nyquist, through a transform of about 40 ms; a
   live waveform cuts its window into 256 buckets at full scale.
5. **A clip is a `WaveformPlot`** of `Envelope.of(samples, channels,
   buckets)`, with the share played from the left in `--gb-mic-wave` and the
   rest in `--gb-mic-wave-ahead`, optionally normalized to its loudest bucket.
   Few buckets for the width are drawn as bars with gaps; many as one shape.
6. **Markup tolerates an unbound source**: `source=` naming nothing draws a
   source that never delivers, so a preview shows the widget at rest.
7. **The goldens draw the leaves** with readings made by the widgets' own
   trackers from a `SyntheticSource` advanced a fixed time, on both themes,
   under the scale sweep. The live half is tested by mounting it under a
   headless runtime and rebuilding.

## Consequences

- `goldberry-mic` now requires `goldberry-widgets` (`api`), as `goldberry-media`
  does; an application that only records still carries the widget catalog.
- `MarkupNames.MODULES` includes `mic`, so `BookTest` wants the three
  sections, which are parked with the chapter.
- An application that holds a reading itself draws it with the leaves; there
  is no `@Markup` name for a clip's outline.
- Spectrum bars are not smoothed between frames; a falling cap per band can be
  added on the state without changing the leaf.
