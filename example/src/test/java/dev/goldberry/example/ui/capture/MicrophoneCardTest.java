package dev.goldberry.example.ui.capture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import dev.goldberry.Goldberry;
import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Theme;
import dev.goldberry.example.ShowcaseScene;
import dev.goldberry.golden.GoldenImage;
import dev.goldberry.junit.HeadlessRuntime;
import dev.goldberry.media.view.AudioPlayer;
import dev.goldberry.mic.MicrophoneException;
import dev.goldberry.mic.Microphones;
import dev.goldberry.mic.source.SampleFormat;
import dev.goldberry.mic.source.SyntheticSource;
import dev.goldberry.mic.view.LevelMeter;
import dev.goldberry.mic.view.SpectrumView;
import dev.goldberry.mic.view.WaveformView;
import dev.goldberry.mic.view.draw.LevelBar;
import dev.goldberry.mic.view.draw.WaveformPlot;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.render.event.UiExecutor;
import dev.goldberry.text.font.Fonts;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.core.Column;

/// Under [HeadlessRuntime]: the widgets post their redraws, and the cards
/// their devices' news, to the UI thread, which needs a runtime to post to.
/// Without one the first post would start the desktop's.
@DisplayName("the Microphone card")
@ExtendWith(HeadlessRuntime.class)
class MicrophoneCardTest {

    /// Counts the times the system is asked, and refuses as a machine with no
    /// audio driver does.
    private final AtomicInteger asked = new AtomicInteger();

    private final Supplier<Microphones> noDriver = () -> {
        asked.incrementAndGet();
        throw new MicrophoneException("no audio driver");
    };

    @BeforeEach
    void renderer() {
        RendererRequirement.enforce();
    }

    @Test
    @DisplayName("lists the microphones as it mounts, records nothing, and says it is stopped")
    void idle() {
        try (var card = new CardSession(new MicrophoneCard(noDriver))) {
            assertEquals(1, asked.get(), "the system was not asked as the card mounted");
            assertEquals(
                    "No microphones listed: no audio driver. The test tone still plays.", card.text("microphone-note"));
            assertEquals("stopped", card.text("microphone-status"));
            assertTrue(card.widget("microphone-stop", Button.class).disabled());
            assertFalse(card.widget("microphone-start", Button.class).disabled());
        }
    }

    @Test
    @DisplayName("plays the test tone into all three widgets, and they move as it is advanced")
    void testTone() {
        try (var card = new CardSession(new MicrophoneCard(noDriver))) {
            card.session.click("microphone-start");
            var state = card.state(MicrophoneCard.ID, MicrophoneCard.MicrophoneState.class);

            var source = assertInstanceOf(SyntheticSource.class, state.source());
            assertSame(source, card.widget("microphone-level", LevelMeter.class).source());
            assertSame(
                    source,
                    card.widget("microphone-spectrum", SpectrumView.class).source());
            assertSame(
                    source,
                    card.widget("microphone-waveform", WaveformView.class).source());
            assertEquals("test tone", card.text("microphone-status"));

            card.session.advance(Duration.ofMillis(200));
            // The meter's redraw, posted as the blocks arrived.
            ((UiExecutor) Goldberry.ui()).drain();
            card.session.frame();

            assertTrue(
                    source.position()
                            >= SampleFormat.VOICE.framesIn(
                                    Duration.ofMillis(180).toNanos()),
                    "the ticker advanced the tone to frame " + source.position());
            var peak = card.all("microphone-level", LevelBar.class)
                    .getFirst()
                    .reading()
                    .levels()
                    .peak();
            assertTrue(peak > 0.3f, "the meter reads a peak of " + peak);
            assertEquals(1, asked.get(), "the test tone asked the system for its microphones again");
        }
    }

    @Test
    @DisplayName("stops the tone, and the widgets read silence again")
    void stop() {
        try (var card = new CardSession(new MicrophoneCard(noDriver))) {
            card.session.click("microphone-start");
            card.session.frame();
            card.session.click("microphone-stop");
            var state = card.state(MicrophoneCard.ID, MicrophoneCard.MicrophoneState.class);

            assertFalse(state.running());
            assertEquals("stopped", card.text("microphone-status"));
            var idle = state.source();
            card.session.advance(Duration.ofMillis(200));
            assertSame(idle, state.source());
            assertEquals(0, ((SyntheticSource) idle).position(), "the idle source was advanced");
        }
    }

    @Test
    @DisplayName("asks again on List again, says why it did not open, and the tone still plays")
    void noAudio() {
        try (var card = new CardSession(new MicrophoneCard(noDriver))) {
            card.session.click("microphone-list");

            assertEquals(2, asked.get());
            assertEquals(
                    "No microphones listed: no audio driver. The test tone still plays.", card.text("microphone-note"));

            card.session.click("microphone-start");
            assertEquals("test tone", card.text("microphone-status"));
        }
    }

    @Test
    @DisplayName("records a clip of the tone, outlines it, and drops it on Discard")
    void clip() {
        try (var card = new CardSession(new MicrophoneCard(noDriver))) {
            var state = card.state(MicrophoneCard.ID, MicrophoneCard.MicrophoneState.class);
            assertTrue(card.widget("microphone-clip-record", Button.class).disabled(), "a clip of nothing running");

            card.session.click("microphone-start");
            card.session.click("microphone-clip-record");
            assertTrue(card.text("microphone-clip").startsWith("recording"), card.text("microphone-clip"));
            card.session.advance(Duration.ofMillis(500));
            card.session.click("microphone-clip-stop");

            var clip = state.clip();
            assertNotNull(clip);
            assertEquals(0.5, clip.duration().toMillis() / 1000.0, 0.03);
            assertEquals(0.35f, clip.envelope(1).peak(), 0.05f, "the tone's peak");
            assertEquals(
                    1, card.all("microphone-clip-section", WaveformPlot.class).size(), "the clip's outline");
            assertFalse(card.widget("microphone-clip-play", Button.class).disabled());

            card.session.click("microphone-clip-discard");
            assertNull(state.clip());
            assertEquals("Discarded.", card.text("microphone-clip-note"));
            assertEquals(List.of(), card.all("microphone-clip-section", WaveformPlot.class));
        }
    }

    @Test
    @DisplayName("finishes the clip when the source stops")
    void stopFinishesTheClip() {
        try (var card = new CardSession(new MicrophoneCard(noDriver))) {
            var state = card.state(MicrophoneCard.ID, MicrophoneCard.MicrophoneState.class);
            card.session.click("microphone-start");
            card.session.click("microphone-clip-record");
            card.session.advance(Duration.ofMillis(200));
            card.session.click("microphone-stop");

            assertNotNull(state.clip());
            assertTrue(state.clipMessage().startsWith("Kept 0.2 s"), state.clipMessage());
        }
    }

    @Test
    @DisplayName("plays the clip from a WAV file it writes, and deletes the file on Discard")
    void play() throws Exception {
        try (var card = new CardSession(new MicrophoneCard(noDriver))) {
            var state = card.state(MicrophoneCard.ID, MicrophoneCard.MicrophoneState.class);
            card.session.click("microphone-start");
            card.session.click("microphone-clip-record");
            card.session.advance(Duration.ofMillis(300));
            card.session.click("microphone-clip-stop");
            card.session.click("microphone-clip-play");

            var file = state.clipFile();
            assertNotNull(file, state.clipMessage());
            assertArrayEquals(Objects.requireNonNull(state.clip()).wav(), Files.readAllBytes(file));
            assertEquals(
                    1, card.all("microphone-clip-section", AudioPlayer.class).size(), "the clip's player");

            card.session.click("microphone-clip-discard");
            assertFalse(Files.exists(file), "the temporary file outlived the clip");
        }
    }

    @Test
    @DisplayName(
            "draws a second of the test tone: a level near -9 dBFS, a peak at 440 Hz, a steady outline, and half a second kept as a clip")
    void golden() {
        // In a `#root`, as the window has it, which is what gives a bare text
        // its colour.
        try (var scene = new ShowcaseScene();
                var fonts = Fonts.bundled()) {
            GoldenImage.assertMatchesAtOneScale("capture-microphone-tone", 640, 1000, 1.0f, (size, scale) -> {
                try (var session = Offscreen.of(size)
                        .scale(scale)
                        .stylesheets(scene.stylesheets(Theme.NORD_DARK))
                        .fonts(fonts)
                        .session(new Column(List.of(new MicrophoneCard(noDriver)), Attributes.NONE.id("root")))) {
                    session.frame();
                    session.click("microphone-start");
                    session.advance(Duration.ofMillis(500));
                    session.click("microphone-clip-record");
                    session.advance(Duration.ofMillis(500));
                    session.click("microphone-clip-stop");
                    // The buttons' colours ease between enabled and not.
                    session.advance(Duration.ofMillis(400));
                    ((UiExecutor) Goldberry.ui()).drain();
                    return session.frame();
                }
            });
        }
    }

    @Test
    @DisplayName("shows metrics of the running tone: its format and blocks, how many arrived, and its level")
    void metrics() {
        try (var card = new CardSession(new MicrophoneCard(noDriver))) {
            assertEquals(
                    List.of("Format", "–", "Rate", "–", "Blocks", "–", "Level", "–", "Buffered", "–"),
                    card.texts("microphone-metrics"));

            card.session.click("microphone-start");
            card.session.advance(Duration.ofMillis(600));
            card.session.frame();

            var lines = card.texts("microphone-metrics");
            assertEquals("48,000 Hz, mono, blocks of 480 frames (10.0 ms)", lines.get(1));
            // Read at the metrics' last tick, half a second in: two blocks a tick.
            assertTrue(lines.get(5).matches("\\d+ heard, every .*"), lines.get(5));
            assertTrue(lines.get(7).matches("peak -[89]\\.\\d dBFS, RMS -1\\d\\.\\d dBFS"), lines.get(7));
            assertEquals("none: the test tone is made as it is read", lines.get(9));

            card.session.click("microphone-stop");
            assertEquals("–", card.texts("microphone-metrics").get(1));
        }
    }

    @Test
    @DisplayName("describes a format as a reader would")
    void describe() {
        assertEquals("48,000 Hz, mono", MicrophoneCard.MicrophoneState.describe(SampleFormat.VOICE));
        assertEquals("44,100 Hz, 2 channels", MicrophoneCard.MicrophoneState.describe(new SampleFormat(44_100, 2)));
    }
}
