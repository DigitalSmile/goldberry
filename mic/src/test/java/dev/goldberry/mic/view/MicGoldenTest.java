package dev.goldberry.mic.view;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.golden.GoldenImage;
import dev.goldberry.mic.analysis.Envelope;
import dev.goldberry.mic.analysis.EnvelopeTracker;
import dev.goldberry.mic.analysis.LevelTracker;
import dev.goldberry.mic.analysis.SpectrumAnalyzer;
import dev.goldberry.mic.source.SampleFormat;
import dev.goldberry.mic.source.Signal;
import dev.goldberry.mic.source.SyntheticSource;
import dev.goldberry.mic.view.draw.LevelBar;
import dev.goldberry.mic.view.draw.MeterOrientation;
import dev.goldberry.mic.view.draw.SpectrumPlot;
import dev.goldberry.mic.view.draw.SpectrumShape;
import dev.goldberry.mic.view.draw.WaveformPlot;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.Density;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.core.Row;

/// The three widgets as `mic.css` draws them, on both themes.
///
/// Each reading is what the widget's own tracker makes of a [SyntheticSource]
/// advanced by a fixed time, so the picture is the same every run: the leaf a
/// widget builds is drawn with it, as the widget would draw it the frame after
/// those blocks arrived.
@DisplayName("the microphone widgets, drawn")
class MicGoldenTest {

    private static final int WIDTH = 320;

    @BeforeEach
    void renderer() {
        RendererRequirement.enforce();
    }

    /// A level meter's reading after `loud` for 200 ms and then `quiet` for
    /// 400 ms: the bar shows the quiet part and the hold mark the loud one.
    private static LevelTracker.Reading reading(double loud, double quiet) {
        var source = SyntheticSource.of(Signal.mix(Signal.tone(440, loud), new Signal.Noise(loud / 20, 7)));
        var tracker = new LevelTracker();
        source.onSamples(tracker);
        source.advance(Duration.ofMillis(200));
        source.play(Signal.mix(Signal.tone(440, quiet), new Signal.Noise(quiet / 20, 7)));
        source.advance(Duration.ofMillis(400));
        return tracker.reading();
    }

    /// Two seconds that sound like someone talking: syllables of a low tone
    /// with breath on it, swelling and fading, and short pauses between words.
    private static float[] talking() {
        var rate = SampleFormat.VOICE.sampleRate();
        var samples = new float[rate * 2];
        var voice = Signal.mix(Signal.tone(180, 1), Signal.tone(360, 0.4), new Signal.Noise(0.15, 11));
        for (var frame = 0; frame < samples.length; frame++) {
            var seconds = (double) frame / rate;
            var syllable = Math.max(0, Math.sin(Math.PI * seconds * 5.5));
            var word = 0.35 + 0.65 * Math.max(0, Math.sin(Math.PI * seconds * 1.3 + 0.4));
            samples[frame] = (float) (0.45 * syllable * word * voice.valueAt(frame, rate));
        }
        return samples;
    }

    private static Widget scene(List<Widget> children) {
        return new Column(children, Attributes.NONE.id("scene"));
    }

    private static void assertGolden(String name, int height, Theme theme, Widget widget) {
        var sheets = new ArrayList<Stylesheet>(Controls.stylesheets(theme, Density.REGULAR));
        sheets.add(MicStyles.stylesheet());
        sheets.add(Stylesheet.parse(
                CascadeLayer.APPLICATION,
                "#scene { padding: 12px; gap: 12px; background: var(--gb-surface); }"
                        + " .meters { gap: 12px; height: 96px; }"));
        GoldenImage.Scene scene = (size, scale) ->
                Offscreen.of(size).scale(scale).stylesheets(sheets).render(widget);
        GoldenImage.assertMatches(name, WIDTH, height, 1f, scene);
    }

    private static Widget levelMeters() {
        var quiet = reading(0.08, 0.03);
        var speaking = reading(0.5, 0.2);
        var shouting = reading(1.0, 0.6);
        var vertical = new Row(
                List.of(
                        new LevelBar(quiet, MeterOrientation.VERTICAL, Attributes.NONE),
                        new LevelBar(speaking, MeterOrientation.VERTICAL, Attributes.NONE),
                        new LevelBar(shouting, MeterOrientation.VERTICAL, Attributes.NONE)),
                Attributes.NONE.classes("meters"));
        return scene(List.of(new LevelBar(quiet), new LevelBar(speaking), new LevelBar(shouting), vertical));
    }

    private static Widget waveforms() {
        var clip = talking();
        var source = SyntheticSource.of(new Signal.Clip(clip, true));
        var tracker = new EnvelopeTracker(
                WaveformView.BUCKETS, WaveformView.framesPerBucket(source, Duration.ofMillis(1500)));
        source.onSamples(tracker);
        source.advance(Duration.ofMillis(1500));
        var live = new WaveformPlot(tracker.envelope());
        var message = new WaveformPlot(Envelope.of(clip, 1, 64), 0.4, true, Attributes.NONE);
        return scene(List.of(live, message));
    }

    private static Widget spectra() {
        var source = SyntheticSource.of(Signal.mix(
                Signal.tone(200, 0.5), Signal.tone(1000, 0.2), Signal.tone(4000, 0.05), new Signal.Noise(0.01, 3)));
        var analyzer = new SpectrumAnalyzer(source.format(), SpectrumView.transformSize(source.format()));
        source.onSamples(analyzer);
        source.advance(Duration.ofMillis(100));
        var high = SpectrumView.highHertz(source.format());
        var bars = SpectrumPlot.levels(analyzer.spectrum().bands(SpectrumView.BANDS, SpectrumView.LOW_HERTZ, high));
        var line = SpectrumPlot.levels(analyzer.spectrum().bands(96, SpectrumView.LOW_HERTZ, high));
        return scene(List.of(
                new SpectrumPlot(bars, SpectrumShape.BARS, Attributes.NONE),
                new SpectrumPlot(line, SpectrumShape.LINE, Attributes.NONE)));
    }

    @Nested
    @DisplayName("on dark")
    class Dark {

        @Test
        @DisplayName("level-meter: quiet, speaking and shouting, across and upright")
        void levelMeter() {
            assertGolden("level-meter-dark", 180, Theme.NORD_DARK, levelMeters());
        }

        @Test
        @DisplayName("waveform-view: a live window, and a voice message two fifths played")
        void waveform() {
            assertGolden("waveform-view-dark", 116, Theme.NORD_DARK, waveforms());
        }

        @Test
        @DisplayName("spectrum-view: three tones over a little noise, as bars and as a line")
        void spectrum() {
            assertGolden("spectrum-view-dark", 164, Theme.NORD_DARK, spectra());
        }
    }

    @Nested
    @DisplayName("on light")
    class Light {

        @Test
        @DisplayName("level-meter")
        void levelMeter() {
            assertGolden("level-meter-light", 180, Theme.NORD_LIGHT, levelMeters());
        }

        @Test
        @DisplayName("waveform-view")
        void waveform() {
            assertGolden("waveform-view-light", 116, Theme.NORD_LIGHT, waveforms());
        }

        @Test
        @DisplayName("spectrum-view")
        void spectrum() {
            assertGolden("spectrum-view-light", 164, Theme.NORD_LIGHT, spectra());
        }
    }
}
