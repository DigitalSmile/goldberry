package io.github.digitalsmile.goldberry.media.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.media.FfmpegRequirement;
import io.github.digitalsmile.goldberry.media.MediaPlayer;
import io.github.digitalsmile.goldberry.media.PlaybackState;
import io.github.digitalsmile.goldberry.media.PlayerStatus;
import io.github.digitalsmile.goldberry.media.Wav;
import io.github.digitalsmile.goldberry.media.audio.AudioFormat;
import io.github.digitalsmile.goldberry.media.audio.VirtualSink;
import io.github.digitalsmile.goldberry.media.io.MediaIO;
import io.github.digitalsmile.goldberry.media.io.MediaIOProvider;
import io.github.digitalsmile.goldberry.media.io.MemoryIO;
import io.github.digitalsmile.goldberry.media.io.Source;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;
import io.github.digitalsmile.goldberry.widgets.controls.slider.Slider;
import io.github.digitalsmile.goldberry.widgets.markup.Named;
import io.github.digitalsmile.goldberry.widgets.markup.Wiring;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// `audio-player`, mounted without a window: what it builds for each state, and
/// what its controls do to the player.
///
/// The widget reads the player's status on every build, so each check mounts a
/// fresh tree after the player has reached the state being checked.
@DisplayName("audio-player")
class AudioPlayerTest {

    private MediaPlayer player;
    private VirtualSink sink;

    @AfterEach
    void close() {
        if (player != null) {
            player.close();
        }
    }

    record Memory(byte[] data) implements MediaIOProvider {
        @Override
        public Set<String> schemes() {
            return Set.of("mem");
        }

        @Override
        public MediaIO open(Source source) {
            return new MemoryIO(data);
        }
    }

    private void open(byte[] wav, boolean instant) {
        FfmpegRequirement.enforce();
        sink = new VirtualSink(AudioFormat.DEFAULT, instant);
        player = MediaPlayer.builder()
                .sink(() -> sink)
                .ioProviders(List.of(new Memory(wav)))
                .decoderProviders(List.of())
                .build();
        player.open(Source.of(URI.create("mem:///clip.wav")));
    }

    private PlayerStatus await(Predicate<PlayerStatus> condition) {
        var deadline = System.nanoTime() + 10_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (condition.test(player.status())) {
                return player.status();
            }
            Thread.onSpinWait();
        }
        throw new AssertionError("timed out; last " + player.status());
    }

    private static List<Element> walk(Element root) {
        var all = new ArrayList<Element>();
        all.add(root);
        for (var child : root.children()) {
            all.addAll(walk(child));
        }
        return all;
    }

    private static List<Element> mount(MediaPlayer player) {
        return walk(new ElementTree(new AudioPlayer(player)).root());
    }

    private static List<Element> withClass(List<Element> elements, String cssClass) {
        return elements.stream().filter(e -> e.classes().contains(cssClass)).toList();
    }

    private static Button button(List<Element> elements, String cssClass) {
        return of(elements, cssClass, Button.class);
    }

    /// The widget of `type` carrying `cssClass`, by the widget's own attributes:
    /// a slider's element carries none, and hands its classes to the control it
    /// builds.
    private static <W extends Attributed<?>> W of(List<Element> elements, String cssClass, Class<W> type) {
        return elements.stream()
                .map(Element::widget)
                .filter(type::isInstance)
                .map(type::cast)
                .filter(widget -> attributesOf(widget).classes().contains(cssClass))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + type.getSimpleName() + " with ." + cssClass));
    }

    private static Attributes attributesOf(Object widget) {
        return switch (widget) {
            case Slider slider -> slider.attributes();
            case Button button -> button.attributes();
            default -> throw new IllegalArgumentException(widget.toString());
        };
    }

    /// The classes on the player's own root, the column it builds.
    private static Set<String> rootClasses(List<Element> elements) {
        return withClass(elements, "audio-player").getFirst().classes();
    }

    private static List<String> texts(List<Element> elements, String cssClass) {
        return withClass(elements, cssClass).stream()
                .map(Element::widget)
                .filter(Text.class::isInstance)
                .map(w -> ((Text) w).content())
                .toList();
    }

    @Test
    @DisplayName("before anything is opened: disabled play, no seek bar, volume at full")
    void idle() {
        try (var idle = MediaPlayer.builder()
                .sink(() -> new VirtualSink(AudioFormat.DEFAULT, true))
                .build()) {
            var elements = mount(idle);
            assertTrue(rootClasses(elements).contains("is-idle"));
            assertTrue(button(elements, "media-play").disabled());
            assertTrue(withClass(elements, "media-seek").isEmpty());
            assertTrue(withClass(elements, "media-live").isEmpty());
            var volume = of(elements, "media-volume", Slider.class);
            assertEquals(1.0, volume.value());
            assertEquals(List.of("0:00"), texts(elements, "media-time"));
        }
    }

    @Test
    @DisplayName("every glyph it draws is in the bundled icon set")
    void glyphs() {
        for (var glyph : Transport.Glyph.values()) {
            try (var icon = Icon.bundled(glyph.lucide, Transport.ICON_SIZE)) {
                assertEquals(glyph.lucide, icon.name());
            }
        }
    }

    @Test
    @DisplayName("while playing: a seek bar the length of the source, and the times either side of it")
    void playing() {
        open(Wav.silence(AudioFormat.DEFAULT.sampleRate(), 2, AudioFormat.DEFAULT.sampleRate() * 2), false);
        await(status -> status.state() == PlaybackState.PLAYING);
        var elements = mount(player);
        assertTrue(rootClasses(elements).contains("is-playing"));
        var seek = of(elements, "media-seek", Slider.class);
        assertEquals(2.0, seek.max(), 1e-9);
        assertEquals(List.of("0:00", "-0:02"), texts(elements, "media-time"));
        assertFalse(button(elements, "media-play").disabled());
    }

    @Test
    @DisplayName("its controls drive the player: pause, play, mute, volume and seek")
    void controls() {
        open(Wav.silence(AudioFormat.DEFAULT.sampleRate(), 2, AudioFormat.DEFAULT.sampleRate() * 2), false);
        await(status -> status.state() == PlaybackState.PLAYING);
        var elements = mount(player);

        button(elements, "media-play").onPress().run();
        assertEquals(PlaybackState.PAUSED, player.status().state());
        button(mount(player), "media-play").onPress().run();
        assertEquals(PlaybackState.PLAYING, player.status().state());

        button(elements, "media-mute").onPress().run();
        assertTrue(player.status().muted());
        assertEquals(0f, sink.gain());

        of(elements, "media-volume", Slider.class).onChange().accept(0.5);
        assertEquals(0.5f, player.status().volume());

        of(elements, "media-seek", Slider.class).onChange().accept(1.25);
        assertEquals(Duration.ofMillis(1250), player.status().position());
    }

    @Test
    @DisplayName("play at the end starts again from the top")
    void replay() {
        open(Wav.silence(AudioFormat.DEFAULT.sampleRate(), 2, AudioFormat.DEFAULT.sampleRate() / 4), true);
        await(status -> status.state() == PlaybackState.ENDED);
        var elements = mount(player);
        assertTrue(rootClasses(elements).contains("is-ended"));
        button(elements, "media-play").onPress().run();
        await(status -> sink.clears() == 1 && status.state() == PlaybackState.ENDED);
    }

    @Test
    @DisplayName("in ERROR the reason is shown under the controls")
    void error() {
        open(Wav.withFormatTag(Wav.silence(8_000, 1, 800), 6), true);
        await(status -> status.state() == PlaybackState.ERROR);
        var elements = mount(player);
        assertTrue(rootClasses(elements).contains("is-error"));
        assertEquals(List.of("no decoder for pcm_alaw"), texts(elements, "media-error"));
    }

    @Test
    @DisplayName("inflates from markup with a named player, and refuses children")
    void markup() {
        try (var named = MediaPlayer.builder()
                .sink(() -> new VirtualSink(AudioFormat.DEFAULT, true))
                .build()) {
            var wiring = Wiring.none().with(Named.strict().bind("episode", named));
            var node = KdlParser.parse("audio-player player=\"episode\" class=\"compact\"")
                    .getFirst();
            var widget = (AudioPlayer) AudioPlayer.inflate(node, List.of(), wiring);
            assertSame(named, widget.player());
            assertTrue(widget.attributes().classes().contains("compact"));
            assertThrows(
                    IllegalArgumentException.class, () -> AudioPlayer.inflate(node, List.of(new Text("x")), wiring));
        }
    }

    @Test
    @DisplayName("the stylesheet loads and declares the component tokens")
    void stylesheet() throws Exception {
        MediaStyles.stylesheet();
        try (var in = MediaStyles.class.getResourceAsStream(MediaStyles.RESOURCE)) {
            var css = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(css.contains("--gb-media-gap"));
        }
    }
}
