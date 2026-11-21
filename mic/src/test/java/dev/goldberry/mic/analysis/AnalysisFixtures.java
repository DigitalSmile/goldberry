package dev.goldberry.mic.analysis;

import java.time.Duration;

import dev.goldberry.mic.source.SampleFormat;
import dev.goldberry.mic.source.SampleListener;
import dev.goldberry.mic.source.Signal;
import dev.goldberry.mic.source.SyntheticSource;

/// Feeds a signal to an analysis through a synthetic source.
final class AnalysisFixtures {

    private AnalysisFixtures() {}

    static void feed(SampleListener listener, Signal signal, Duration duration) throws Exception {
        feed(listener, SampleFormat.VOICE, signal, duration);
    }

    static void feed(SampleListener listener, SampleFormat format, Signal signal, Duration duration) throws Exception {
        var source = new SyntheticSource(format, signal, format.sampleRate() / 100);
        try (var _ = source.onSamples(listener)) {
            source.advance(duration);
        }
    }
}
