/// What the microphone's widgets draw, each from a reading it is handed: a
/// [LevelBar] from a [dev.goldberry.mic.analysis.LevelTracker.Reading], a
/// [WaveformPlot] from an [dev.goldberry.mic.analysis.Envelope], a
/// [SpectrumPlot] from a spectrum's bands.
///
/// The widgets in [dev.goldberry.mic.view] follow a source and build these.
/// An application that holds a reading of its own draws one directly: a
/// recorded clip's outline, with the part already played in another colour.
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
@NullMarked
package dev.goldberry.mic.view.draw;

import org.jspecify.annotations.NullMarked;
