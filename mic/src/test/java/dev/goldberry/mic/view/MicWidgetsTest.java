package dev.goldberry.mic.view;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import dev.goldberry.junit.HeadlessRuntime;
import dev.goldberry.kdl.KdlParser;
import dev.goldberry.mic.analysis.Levels;
import dev.goldberry.mic.source.SampleFormat;
import dev.goldberry.mic.source.Signal;
import dev.goldberry.mic.source.SyntheticSource;
import dev.goldberry.mic.view.draw.LevelBar;
import dev.goldberry.mic.view.draw.MeterOrientation;
import dev.goldberry.mic.view.draw.SpectrumPlot;
import dev.goldberry.mic.view.draw.SpectrumShape;
import dev.goldberry.mic.view.draw.WaveformPlot;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.markup.Named;
import dev.goldberry.widgets.markup.Wiring;

/// The three widgets as an application holds them: inflated from markup,
/// mounted over a source, and following it.
///
/// Under [HeadlessRuntime]: a block a mounted widget hears posts its redraw to
/// the UI thread, which needs a runtime to post to. The redraw itself is not
/// run here (no loop turns); a rebuild the test asks for reads the analysis.
@DisplayName("the microphone widgets")
@ExtendWith(HeadlessRuntime.class)
class MicWidgetsTest {

    private static Widget inflate(String kdl, Wiring wiring) {
        var node = KdlParser.parse(kdl).getFirst();
        return switch (node.name()) {
            case "level-meter" -> LevelMeter.inflate(node, List.of(), wiring);
            case "waveform-view" -> WaveformView.inflate(node, List.of(), wiring);
            case "spectrum-view" -> SpectrumView.inflate(node, List.of(), wiring);
            default -> throw new AssertionError(node.name());
        };
    }

    private static List<Element> walk(Element from) {
        var all = new ArrayList<Element>();
        all.add(from);
        for (var child : from.children()) {
            all.addAll(walk(child));
        }
        return all;
    }

    private static <T> T built(ElementTree tree, Class<T> type) {
        tree.flush();
        return walk(tree.root()).stream()
                .map(Element::widget)
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst()
                .orElseThrow();
    }

    @Nested
    @DisplayName("in markup")
    class Markup {

        private final SyntheticSource mic = SyntheticSource.of(Signal.silence());
        private final Wiring wiring = Wiring.none().with(Named.strict().bind("mic", mic));

        @Test
        @DisplayName("take the source they name, and what else they are told")
        void named() {
            var meter = assertInstanceOf(
                    LevelMeter.class, inflate("level-meter source=\"mic\" orientation=\"vertical\"", wiring));
            var wave =
                    assertInstanceOf(WaveformView.class, inflate("waveform-view source=\"mic\" seconds=1.5", wiring));
            var spectrum = assertInstanceOf(
                    SpectrumView.class, inflate("spectrum-view source=\"mic\" bands=48 shape=\"line\"", wiring));
            assertAll(
                    () -> assertSame(mic, meter.source()),
                    () -> assertEquals(MeterOrientation.VERTICAL, meter.orientation()),
                    () -> assertEquals(Duration.ofMillis(1500), wave.window()),
                    () -> assertEquals(48, spectrum.bands()),
                    () -> assertEquals(SpectrumShape.LINE, spectrum.shape()));
        }

        @Test
        @DisplayName("default to a horizontal meter, three seconds and 32 bars")
        void defaults() {
            var meter = (LevelMeter) inflate("level-meter source=\"mic\"", wiring);
            var wave = (WaveformView) inflate("waveform-view source=\"mic\"", wiring);
            var spectrum = (SpectrumView) inflate("spectrum-view source=\"mic\"", wiring);
            assertAll(
                    () -> assertEquals(MeterOrientation.HORIZONTAL, meter.orientation()),
                    () -> assertEquals(WaveformView.WINDOW, wave.window()),
                    () -> assertEquals(SpectrumView.BANDS, spectrum.bands()),
                    () -> assertEquals(SpectrumShape.BARS, spectrum.shape()));
        }

        @Test
        @DisplayName("draw silence where nothing is bound to the name, as a preview does")
        void unbound() {
            var meter = (LevelMeter) inflate("level-meter source=\"mic\"", Wiring.none());
            assertEquals(SampleFormat.VOICE, meter.source().format());
        }

        @Test
        @DisplayName("refuse children, and values they cannot draw")
        void refuses() {
            var child = List.<Widget>of(new LevelMeter(mic));
            var node = KdlParser.parse("level-meter").getFirst();
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> LevelMeter.inflate(node, child, wiring)),
                    () -> assertThrows(
                            IllegalArgumentException.class,
                            () -> inflate("level-meter source=\"mic\" orientation=\"diagonal\"", wiring)),
                    () -> assertThrows(
                            IllegalArgumentException.class, () -> inflate("waveform-view seconds=0", wiring)),
                    () -> assertThrows(
                            IllegalArgumentException.class, () -> inflate("spectrum-view bands=2.5", wiring)),
                    () -> assertThrows(IllegalArgumentException.class, () -> inflate("spectrum-view bands=0", wiring)),
                    () -> assertThrows(
                            IllegalArgumentException.class, () -> inflate("spectrum-view shape=\"dots\"", wiring)));
        }
    }

    @Nested
    @DisplayName("mounted")
    class Mounted {

        @Test
        @DisplayName("a level meter listens while mounted, reads what it heard, and lets go when unmounted")
        void levelMeter() {
            var tone = SyntheticSource.of(Signal.tone(1000, 0.5));
            var source = new CountingSource(tone);
            var tree =
                    new ElementTree(new LevelMeter(source, MeterOrientation.HORIZONTAL, Attributes.NONE.classes("a")));
            var quiet = built(tree, LevelBar.class);
            assertEquals(Levels.SILENCE, quiet.reading().levels(), "nothing heard before it mounted");
            assertEquals(1, source.listening.get());

            tone.advance(Duration.ofMillis(100));
            tree.update(new LevelMeter(source, MeterOrientation.HORIZONTAL, Attributes.NONE.classes("b")));
            var loud = built(tree, LevelBar.class);
            assertEquals(-6.02, loud.reading().levels().peakDecibels(), 0.05);
            assertEquals(Set.of("b"), loud.classes(), "the leaf carries the widget's attributes");
            assertEquals(1, source.listening.get(), "the same source, the same subscription");

            tree.unmount();
            assertEquals(0, source.listening.get());
        }

        @Test
        @DisplayName("a widget given another source starts over on it")
        void anotherSource() {
            var first = SyntheticSource.of(Signal.tone(1000, 0.5));
            var second = new CountingSource(SyntheticSource.of(Signal.silence()));
            var counted = new CountingSource(first);
            var tree = new ElementTree(new LevelMeter(counted));
            built(tree, LevelBar.class);
            first.advance(Duration.ofMillis(100));

            tree.update(new LevelMeter(second));
            var bar = built(tree, LevelBar.class);
            assertAll(
                    () -> assertEquals(0, counted.listening.get(), "the first source let go"),
                    () -> assertEquals(1, second.listening.get()),
                    () -> assertEquals(Levels.SILENCE, bar.reading().levels(), "what the first one said is gone"));
            tree.unmount();
        }

        @Test
        @DisplayName("a waveform view outlines the window it spans, and starts over when the window changes")
        void waveform() {
            var tone = SyntheticSource.of(Signal.tone(100, 0.8));
            var counted = new CountingSource(tone);
            var tree = new ElementTree(new WaveformView(counted, Duration.ofSeconds(1), Attributes.NONE.classes("a")));
            built(tree, WaveformPlot.class);
            tone.advance(Duration.ofSeconds(1));

            tree.update(new WaveformView(counted, Duration.ofSeconds(1), Attributes.NONE.classes("b")));
            var plot = built(tree, WaveformPlot.class);
            assertEquals(WaveformView.BUCKETS, plot.envelope().buckets());
            assertEquals(0.8f, plot.envelope().peak(), 0.01f);

            tree.update(new WaveformView(counted, Duration.ofSeconds(2), Attributes.NONE.classes("b")));
            var fresh = built(tree, WaveformPlot.class);
            assertAll(
                    () -> assertEquals(0f, fresh.envelope().peak(), "a new window, nothing heard in it yet"),
                    () -> assertEquals(1, counted.listening.get()));
            tree.unmount();
        }

        @Test
        @DisplayName("a spectrum view's loudest band is the tone's")
        void spectrum() {
            var tone = SyntheticSource.of(Signal.tone(1000, 1));
            var tree = new ElementTree(new SpectrumView(tone, 16, SpectrumShape.BARS, Attributes.NONE.classes("a")));
            built(tree, SpectrumPlot.class);
            tone.advance(Duration.ofMillis(100));

            tree.update(new SpectrumView(tone, 16, SpectrumShape.BARS, Attributes.NONE.classes("b")));
            var bands = built(tree, SpectrumPlot.class).bands();
            assertEquals(16, bands.size());
            var loudest = 0;
            for (var band = 1; band < bands.size(); band++) {
                if (bands.get(band) > bands.get(loudest)) {
                    loudest = band;
                }
            }
            // 16 bands from 50 Hz to 16 kHz, a factor of 1.434 each: 1 kHz is in
            // the ninth, which starts at 50 × 1.434^8 = 899 Hz.
            assertEquals(8, loudest);
            assertTrue(bands.get(loudest) > -3, () -> "a full-scale tone reads near 0 dB: " + bands);
            tree.unmount();
        }

        @Test
        @DisplayName("a spectrum transform holds 40 ms, a power of two")
        void transformSize() {
            assertAll(
                    () -> assertEquals(2048, SpectrumView.transformSize(new SampleFormat(48_000, 1))),
                    () -> assertEquals(2048, SpectrumView.transformSize(new SampleFormat(44_100, 2))),
                    () -> assertEquals(1024, SpectrumView.transformSize(new SampleFormat(16_000, 1))),
                    () -> assertEquals(3800, SpectrumView.highHertz(new SampleFormat(8_000, 1)), 1e-9));
        }
    }
}
