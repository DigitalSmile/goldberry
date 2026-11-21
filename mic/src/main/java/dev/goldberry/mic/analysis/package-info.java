/// What a stream of samples says: [Levels] per block and a held peak
/// ([LevelTracker]), a [Spectrum] through a Hann window ([SpectrumAnalyzer],
/// over an [Fft]), and a waveform's outline ([EnvelopeTracker]). Each tracker
/// is a [dev.goldberry.mic.source.SampleListener], fed on the source's thread
/// and read from any other.
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
@NullMarked
package dev.goldberry.mic.analysis;

import org.jspecify.annotations.NullMarked;
