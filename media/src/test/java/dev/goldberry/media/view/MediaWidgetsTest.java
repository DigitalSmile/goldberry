package dev.goldberry.media.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.input.event.KeyEvent;
import dev.goldberry.input.event.PointerEvent;
import dev.goldberry.input.key.Key;
import dev.goldberry.input.key.Modifiers;
import dev.goldberry.junit.HeadlessRuntime;
import dev.goldberry.kdl.KdlParser;
import dev.goldberry.media.FfmpegRequirement;
import dev.goldberry.media.HardwareDecoding;
import dev.goldberry.media.MediaPlayer;
import dev.goldberry.media.PlaybackState;
import dev.goldberry.media.PlayerStatus;
import dev.goldberry.media.Wav;
import dev.goldberry.media.audio.AudioFormat;
import dev.goldberry.media.audio.VirtualSink;
import dev.goldberry.media.io.MediaIO;
import dev.goldberry.media.io.MediaIOProvider;
import dev.goldberry.media.io.MemoryIO;
import dev.goldberry.media.io.Source;
import dev.goldberry.media.picture.PictureForm;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.option.Option;
import dev.goldberry.widgets.controls.select.Select;
import dev.goldberry.widgets.controls.slider.Slider;
import dev.goldberry.widgets.core.image.Fit;
import dev.goldberry.widgets.markup.Named;
import dev.goldberry.widgets.markup.Wiring;
import dev.goldberry.widgets.text.Text;

/// `media-controls`, `video-view` and `media-player`, mounted without a window:
/// what each builds, what its keys and its seek bar do to the player, and when
/// the player's controls hide.
/// Under [HeadlessRuntime]: every one of these widgets posts the player's status
/// changes to the UI thread, which needs a runtime to post to.
@DisplayName("media-controls, video-view and media-player")
@ExtendWith(HeadlessRuntime.class)
class MediaWidgetsTest {

    private static final int RATE = AudioFormat.DEFAULT.sampleRate();

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

    private MediaPlayer player;

    @BeforeEach
    void requireFfmpeg() {
        FfmpegRequirement.enforce();
    }

    @AfterEach
    void close() {
        if (player != null) {
            player.close();
        }
    }

    private void open(byte[] data, String name) {
        player = MediaPlayer.builder()
                .hardwareDecoding(HardwareDecoding.OFF)
                .sink(() -> new VirtualSink(AudioFormat.DEFAULT, false))
                .ioProviders(List.of(new Memory(data)))
                .decoderProviders(List.of())
                .build();
        player.open(Source.of(URI.create("mem:///" + name)));
    }

    /// Twelve seconds of silence, playing, on a sink that never plays: the
    /// position stays where a seek puts it.
    private void openPlaying() {
        open(Wav.silence(RATE, 2, RATE * 12), "clip.wav");
        await(status -> status.state() == PlaybackState.PLAYING);
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

    static byte[] fixture(String name) {
        try (var in = MediaWidgetsTest.class.getResourceAsStream("/dev/goldberry/media/fixtures/" + name)) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Element> walk(Element root) {
        var all = new ArrayList<Element>();
        all.add(root);
        for (var child : root.children()) {
            all.addAll(walk(child));
        }
        return all;
    }

    /// Every element `widget` builds, the styled ones with their classes.
    private static List<Element> elements(Widget widget) {
        return mount(widget);
    }

    private static List<Element> mount(Widget widget) {
        return walk(new ElementTree(widget).root());
    }

    private static <W> W first(List<Element> elements, Class<W> type) {
        return elements.stream()
                .map(Element::widget)
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + type.getSimpleName()));
    }

    private static Slider seekBar(List<Element> elements) {
        return elements.stream()
                .map(Element::widget)
                .filter(Slider.class::isInstance)
                .map(Slider.class::cast)
                .filter(slider -> slider.attributes().classes().contains("media-seek"))
                .findFirst()
                .orElseThrow();
    }

    private static KeyEvent key(Key key) {
        return new KeyEvent(KeyEvent.Kind.PRESSED, key, Modifiers.NONE, false, null);
    }

    @Nested
    @DisplayName("media-controls")
    class Controls {

        private MediaControlsBar bar() {
            return first(
                    mount(new MediaControls(
                            player, Attributes.NONE.id("transport").classes("compact"))),
                    MediaControlsBar.class);
        }

        @Test
        @DisplayName("is one focusable media-controls node, with the widget's id, classes and state")
        void node() {
            openPlaying();
            var bar = bar();
            assertEquals("media-controls", bar.cssType());
            assertEquals("transport", bar.id());
            assertTrue(bar.classes().containsAll(Set.of("compact", "is-playing")));
            assertTrue(bar.isFocusable());
            assertEquals(6, bar.children().size(), "play, elapsed, seek, remaining, mute, volume");
        }

        @Test
        @DisplayName("Space and K play and pause; a held key does it once")
        void playPause() {
            openPlaying();
            bar().onKey(key(Key.SPACE));
            assertEquals(PlaybackState.PAUSED, player.status().state());
            bar().onKey(key(Key.K));
            assertEquals(PlaybackState.PLAYING, player.status().state());
            var repeat = new KeyEvent(KeyEvent.Kind.PRESSED, Key.SPACE, Modifiers.NONE, true, null);
            bar().onKey(repeat);
            assertEquals(PlaybackState.PLAYING, player.status().state());
        }

        @Test
        @DisplayName("arrows seek five seconds, clamped to the source; Home goes to the start")
        void arrowsSeek() {
            openPlaying();
            bar().onKey(key(Key.RIGHT));
            assertEquals(Duration.ofSeconds(5), player.status().position());
            bar().onKey(key(Key.RIGHT));
            bar().onKey(key(Key.RIGHT));
            assertEquals(Duration.ofSeconds(12), player.status().position());
            bar().onKey(key(Key.LEFT));
            assertEquals(Duration.ofSeconds(7), player.status().position());
            bar().onKey(key(Key.HOME));
            assertEquals(Duration.ZERO, player.status().position());
            player.seek(Duration.ofSeconds(2));
            bar().onKey(key(Key.LEFT));
            assertEquals(Duration.ZERO, player.status().position());
        }

        @Test
        @DisplayName("up and down move the volume a twentieth, clamped; M mutes and unmutes")
        void volumeAndMute() {
            openPlaying();
            bar().onKey(key(Key.DOWN));
            assertEquals(0.95f, player.status().volume(), 1e-6f);
            bar().onKey(key(Key.UP));
            bar().onKey(key(Key.UP));
            assertEquals(1f, player.status().volume());
            bar().onKey(key(Key.M));
            assertTrue(player.status().muted());
            bar().onKey(key(Key.M));
            assertFalse(player.status().muted());
        }

        @Test
        @DisplayName("comma and period step a picture back and on; shifted, they make it slower and faster")
        void stepAndRate() {
            open(fixture("clip-vp9.webm"), "clip-vp9.webm");
            await(status -> status.state() == PlaybackState.PLAYING);
            player.pause();
            await(status -> player.currentPicture().isPresent());
            bar().onKey(key(Key.PERIOD));
            await(status -> player.currentPicture()
                    .map(p -> p.ptsNanos() == 40_000_000L)
                    .orElse(false));
            bar().onKey(key(Key.COMMA));
            await(status -> player.currentPicture().map(p -> p.ptsNanos() == 0L).orElse(false));
            assertEquals(PlaybackState.PAUSED, player.status().state());

            var shift = new Modifiers(true, false, false, false);
            var faster = new KeyEvent(KeyEvent.Kind.PRESSED, Key.PERIOD, shift, false, null);
            bar().onKey(faster);
            assertTrue(faster.isConsumed());
            assertEquals(1.25f, player.status().rate());
            var labels = mount(new MediaControls(player)).stream()
                    .filter(e -> e.classes().contains("media-rate"))
                    .map(e -> ((Text) e.widget()).content())
                    .toList();
            assertEquals(List.of("1.25\u00d7"), labels);
            for (var i = 0; i < 10; i++) {
                bar().onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.COMMA, shift, false, null));
            }
            assertEquals(0.25f, player.status().rate(), "no slower than the slowest step");
            bar().onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.COMMA, shift, false, null));
            assertEquals(0.25f, player.status().rate());
            // Back at 1 the label goes.
            player.setRate(1f);
            assertTrue(mount(new MediaControls(player)).stream()
                    .noneMatch(e -> e.classes().contains("media-rate")));
        }

        @Test
        @DisplayName("the rate label says as few digits as the rate needs")
        void rateLabel() {
            assertEquals("2\u00d7", Transport.rateLabel(2f));
            assertEquals("0.75\u00d7", Transport.rateLabel(0.75f));
            assertEquals("1.5\u00d7", Transport.rateLabel(1.5f));
        }

        @Test
        @DisplayName("consumes the keys it answers, and leaves the rest and every modified key alone")
        void consumes() {
            openPlaying();
            var answered = key(Key.M);
            bar().onKey(answered);
            assertTrue(answered.isConsumed());
            var other = key(Key.A);
            bar().onKey(other);
            assertFalse(other.isConsumed());
            var modified = new KeyEvent(
                    KeyEvent.Kind.PRESSED, Key.SPACE, new Modifiers(true, false, false, false), false, null);
            bar().onKey(modified);
            assertFalse(modified.isConsumed());
            assertEquals(PlaybackState.PLAYING, player.status().state());
        }

        @Test
        @DisplayName("the seek bar scrubs paused while held, and plays on from the exact position on release")
        void scrub() {
            openPlaying();
            var elements = mount(new MediaControls(player));
            var seek = seekBar(elements);
            seek.onChange().accept(3.0);
            assertEquals(PlaybackState.PAUSED, player.status().state(), "paused for the drag");
            assertTrue(first(mount(new MediaControls(player)), MediaControlsBar.class)
                    .classes()
                    .contains("is-paused"));
            seek.onChange().accept(4.0);
            assertEquals(Duration.ofSeconds(4), player.status().position());
            seek.onCommit().accept(4.5);
            assertEquals(Duration.ofMillis(4500), player.status().position());
            await(status -> status.state() == PlaybackState.PLAYING);
        }

        @Test
        @DisplayName("scrubbing a paused player leaves it paused")
        void scrubPaused() {
            openPlaying();
            player.pause();
            var seek = seekBar(mount(new MediaControls(player)));
            seek.onChange().accept(2.0);
            seek.onCommit().accept(2.0);
            assertEquals(PlaybackState.PAUSED, player.status().state());
            assertEquals(Duration.ofSeconds(2), player.status().position());
        }

        @Test
        @DisplayName("inflates from markup with a named player, and refuses children")
        void markup() {
            try (var named = MediaPlayer.builder()
                    .sink(() -> new VirtualSink(AudioFormat.DEFAULT, true))
                    .build()) {
                var wiring = Wiring.none().with(Named.strict().bind("tv", named));
                var node = KdlParser.parse("media-controls player=\"tv\"").getFirst();
                assertSame(named, ((MediaControls) MediaControls.inflate(node, List.of(), wiring)).player());
                assertThrows(
                        IllegalArgumentException.class,
                        () -> MediaControls.inflate(node, List.of(new Text("x")), wiring));
            }
        }
    }

    @Nested
    @DisplayName("video-view")
    class Video {

        @Test
        @DisplayName("draws the current picture, and the player says when the next one is due")
        void surface() {
            open(fixture("clip-vp9.webm"), "clip-vp9.webm");
            await(status -> status.state() == PlaybackState.PLAYING);
            var surface = first(mount(new VideoView(player, Fit.COVER)), VideoSurface.class);
            assertEquals("video-view", surface.cssType());
            assertEquals(Fit.COVER, surface.fit());
            assertFalse(surface.isAnimating(), "the owner paces it, not the frame loop");
            var box = surface.render(ComputedStyle.INITIAL, List.of(), null);
            assertTrue(box.painting() != null, "a picture is drawn");
            // The sink never plays, so the clock stands at zero and the pictures
            // after the first wait their time: 40 ms for the next, once the video
            // thread has queued it, which PLAYING (the first picture) does not wait
            // for.
            await(status -> player.untilNextPicture().isPresent());
            var until = player.untilNextPicture().orElseThrow();
            assertTrue(until.compareTo(Duration.ofMillis(40)) <= 0 && !until.isNegative(), until.toString());
            player.pause();
            // Not clickable on its own: a video-view is only the picture.
            var click = new PointerEvent(PointerEvent.Kind.CLICKED, 1, 1, PointerEvent.Button.PRIMARY, 1, 1, 1, null);
            surface.onPointer(click);
            assertEquals(PlaybackState.PAUSED, player.status().state());
        }

        @Test
        @DisplayName("draws nothing before the first picture, and for a source with no video")
        void nothingYet() {
            openPlaying();
            var surface = first(mount(new VideoView(player)), VideoSurface.class);
            assertNull(surface.render(ComputedStyle.INITIAL, List.of(), null).painting());
        }

        @Test
        @DisplayName("while mounted it asks for converted pictures, so a view of planes cannot starve it")
        void attachesConverted() {
            try (var shared = MediaPlayer.builder()
                    .sink(() -> new VirtualSink(AudioFormat.DEFAULT, true))
                    .build()) {
                var planes = shared.attachView(PictureForm.PLANES);
                assertEquals(PictureForm.PLANES, shared.pictureForm());
                var tree = new ElementTree(new VideoView(shared));
                assertEquals(PictureForm.CONVERTED, shared.pictureForm(), "the CPU view draws BGRA alone");
                tree.unmount();
                assertEquals(PictureForm.PLANES, shared.pictureForm(), "unmounted, it lets go");
                planes.close();
                assertEquals(PictureForm.CONVERTED, shared.pictureForm());
            }
        }

        @Test
        @DisplayName("inflates with a named player and a fit, and refuses a fit it does not know")
        void markup() {
            try (var named = MediaPlayer.builder()
                    .sink(() -> new VirtualSink(AudioFormat.DEFAULT, true))
                    .build()) {
                var wiring = Wiring.none().with(Named.strict().bind("tv", named));
                var view = (VideoView) VideoView.inflate(
                        KdlParser.parse("video-view player=\"tv\" fit=\"cover\"")
                                .getFirst(),
                        List.of(),
                        wiring);
                assertEquals(Fit.COVER, view.fit());
                assertEquals(
                        Fit.CONTAIN,
                        ((VideoView) VideoView.inflate(
                                        KdlParser.parse("video-view player=\"tv\"")
                                                .getFirst(),
                                        List.of(),
                                        wiring))
                                .fit());
                assertThrows(
                        IllegalArgumentException.class,
                        () -> VideoView.inflate(
                                KdlParser.parse("video-view player=\"tv\" fit=\"zoom\"")
                                        .getFirst(),
                                List.of(),
                                wiring));
            }
        }
    }

    @Nested
    @DisplayName("media-player")
    class Player {

        private MediaPlayerBox box() {
            return first(mount(new MediaPlayerView(player)), MediaPlayerBox.class);
        }

        @Test
        @DisplayName("is the picture first, then the overlay with the controls")
        void structure() {
            open(fixture("clip-vp9.webm"), "clip-vp9.webm");
            await(status -> status.state() == PlaybackState.PLAYING);
            var elements = mount(new MediaPlayerView(player, Fit.FILL, Attributes.NONE.id("tv")));
            var box = first(elements, MediaPlayerBox.class);
            assertEquals("media-player", box.cssType());
            assertEquals("tv", box.id());
            assertInstanceOf(VideoSurface.class, box.parts().getFirst());
            assertTrue(box.classes().contains("is-playing"));
            assertTrue(elements.stream().anyMatch(e -> e.classes().contains("media-overlay")));
            first(elements, MediaControlsBar.class);
        }

        @Test
        @DisplayName("a stream's title is a line in the overlay, over the controls")
        void nowPlaying() {
            var io = new MemoryIO(fixture("clip-vp9.webm"));
            io.title = "Goldberry TV - The Mandelbrot Hour";
            player = MediaPlayer.builder()
                    .hardwareDecoding(HardwareDecoding.OFF)
                    .sink(() -> new VirtualSink(AudioFormat.DEFAULT, false))
                    .ioProviders(List.of(new MediaIOProvider() {
                        @Override
                        public Set<String> schemes() {
                            return Set.of("mem");
                        }

                        @Override
                        public MediaIO open(Source source) {
                            return io;
                        }
                    }))
                    .decoderProviders(List.of())
                    .build();
            player.open(Source.of(URI.create("mem:///clip-vp9.webm")));
            await(status -> status.nowPlaying().isPresent());
            var elements = mount(new MediaPlayerView(player));
            var overlay = elements.stream()
                    .filter(e -> e.classes().contains("media-overlay"))
                    .findFirst()
                    .orElseThrow();
            var title = overlay.children().getFirst();
            assertTrue(title.classes().contains("media-now-playing"));
            assertEquals("Goldberry TV - The Mandelbrot Hour", ((Text) title.widget()).content());
        }

        @Test
        @DisplayName("subtitles: a menu of the tracks with off, the chosen cue as lines over the picture")
        void subtitles() {
            open(fixture("clip-vp9-subs.mkv"), "clip-vp9-subs.mkv");
            await(status -> status.state() == PlaybackState.PLAYING);
            player.pause();
            var menu = elements(new MediaPlayerView(player)).stream()
                    .map(Element::widget)
                    .filter(Select.class::isInstance)
                    .map(Select.class::cast)
                    .filter(select -> select.attributes().classes().contains("media-subtitles-menu"))
                    .findFirst()
                    .orElseThrow();
            assertEquals("off", menu.value());
            var labels = menu.children().stream()
                    .map(Option.class::cast)
                    .map(Option::label)
                    .toList();
            assertEquals(3, labels.size(), labels.toString());
            assertEquals("Subtitles off", labels.getFirst());
            var english = menu.children().stream()
                    .map(Option.class::cast)
                    .filter(option -> !option.value().equals("off"))
                    .findFirst()
                    .orElseThrow();
            menu.onChange().accept(english.value());
            await(status -> status.subtitles().isPresent());
            player.seek(Duration.ofMillis(200));
            await(status -> !player.currentSubtitles().isEmpty());
            var lines = elements(new MediaPlayerView(player)).stream()
                    .filter(e -> e.classes().contains("media-subtitle"))
                    .map(e -> ((Text) e.widget()).content())
                    .toList();
            assertEquals(List.of("First line"), lines);

            menu.onChange().accept("off");
            assertTrue(player.status().subtitles().isEmpty());
            assertTrue(elements(new MediaPlayerView(player)).stream()
                    .noneMatch(e -> e.classes().contains("media-subtitles")));
        }

        @Test
        @DisplayName("media-controls has the subtitles menu too, and audio-player, which draws no subtitles, does not")
        void subtitleMenuWhereSubtitlesShow() {
            open(fixture("clip-vp9-subs.mkv"), "clip-vp9-subs.mkv");
            await(status -> status.state() == PlaybackState.PLAYING);
            assertTrue(hasSubtitleMenu(new MediaControls(player)));
            assertFalse(hasSubtitleMenu(new AudioPlayer(player)));
        }

        @Test
        @DisplayName("a source with no subtitles gets no subtitles menu")
        void noSubtitlesNoMenu() {
            open(fixture("clip-vp9.webm"), "clip-vp9.webm");
            await(status -> status.state() == PlaybackState.PLAYING);
            assertFalse(hasSubtitleMenu(new MediaPlayerView(player)));
        }

        private boolean hasSubtitleMenu(Widget widget) {
            return elements(widget).stream()
                    .map(Element::widget)
                    .filter(Select.class::isInstance)
                    .map(Select.class::cast)
                    .anyMatch(select -> select.attributes().classes().contains("media-subtitles-menu"));
        }

        @Test
        @DisplayName("a click on the picture pauses and plays")
        void clickToggles() {
            open(fixture("clip-vp9.webm"), "clip-vp9.webm");
            await(status -> status.state() == PlaybackState.PLAYING);
            var surface = (VideoSurface) box().parts().getFirst();
            var click = new PointerEvent(PointerEvent.Kind.CLICKED, 1, 1, PointerEvent.Button.PRIMARY, 1, 1, 1, null);
            surface.onPointer(click);
            assertTrue(click.isConsumed());
            assertEquals(PlaybackState.PAUSED, player.status().state());
        }

        @Test
        @DisplayName(
                "the controls hide when the pointer leaves while playing, show when it moves, and never hide paused")
        void idleHides() {
            openPlaying();
            var tree = new ElementTree(new MediaPlayerView(player));
            var box = first(walk(tree.root()), MediaPlayerBox.class);
            assertFalse(box.classes().contains("is-pointer-idle"));

            box.onPointer(new PointerEvent(PointerEvent.Kind.EXITED, 1, 1, null, 0, null));
            tree.flush();
            assertTrue(first(walk(tree.root()), MediaPlayerBox.class).classes().contains("is-pointer-idle"));

            box.onPointer(new PointerEvent(PointerEvent.Kind.MOVED, 1, 1, null, 0, null));
            tree.flush();
            assertFalse(first(walk(tree.root()), MediaPlayerBox.class).classes().contains("is-pointer-idle"));

            box.onPointer(new PointerEvent(PointerEvent.Kind.EXITED, 1, 1, null, 0, null));
            player.pause();
            tree.flush();
            assertFalse(
                    first(walk(tree.root()), MediaPlayerBox.class).classes().contains("is-pointer-idle"),
                    "a paused player shows its controls");
        }

        @Test
        @DisplayName("a player with nothing open is .is-idle, and its controls are not hidden for it")
        void nothingOpen() {
            try (var idle = MediaPlayer.builder()
                    .sink(() -> new VirtualSink(AudioFormat.DEFAULT, true))
                    .build()) {
                var box = first(mount(new MediaPlayerView(idle)), MediaPlayerBox.class);
                assertTrue(box.classes().contains("is-idle"));
                assertFalse(box.classes().contains("is-pointer-idle"));
            }
        }

        @Test
        @DisplayName("keys reach the player from the box itself")
        void keys() {
            openPlaying();
            box().onKey(key(Key.SPACE));
            assertEquals(PlaybackState.PAUSED, player.status().state());
        }

        @Test
        @DisplayName("an H.264/AAC file shows which codecs it could not play, over the picture")
        void unsupported() {
            open(fixture("clip-h264-aac.mp4"), "clip-h264-aac.mp4");
            await(status -> status.state() == PlaybackState.ERROR);
            var texts = mount(new MediaPlayerView(player)).stream()
                    .filter(e -> e.classes().contains("media-error"))
                    .map(e -> ((Text) e.widget()).content())
                    .toList();
            assertEquals(List.of("no decoder for h264, aac"), texts);
            assertTrue(box().classes().contains("is-error"));
        }

        @Test
        @DisplayName("inflates with a named player and a fit")
        void markup() {
            try (var named = MediaPlayer.builder()
                    .sink(() -> new VirtualSink(AudioFormat.DEFAULT, true))
                    .build()) {
                var wiring = Wiring.none().with(Named.strict().bind("tv", named));
                var view = (MediaPlayerView) MediaPlayerView.inflate(
                        KdlParser.parse("media-player player=\"tv\" fit=\"fill\"")
                                .getFirst(),
                        List.of(),
                        wiring);
                assertEquals(Fit.FILL, view.fit());
                assertSame(named, view.player());
                assertThrows(
                        IllegalArgumentException.class,
                        () -> MediaPlayerView.inflate(
                                KdlParser.parse("media-player player=\"tv\"").getFirst(),
                                List.of(new Text("x")),
                                wiring));
            }
        }
    }
}
