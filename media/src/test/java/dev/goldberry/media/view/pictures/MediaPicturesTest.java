package dev.goldberry.media.view.pictures;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.golden.GoldenImage;
import dev.goldberry.media.FfmpegRequirement;
import dev.goldberry.media.HardwareDecoding;
import dev.goldberry.media.MediaPlayer;
import dev.goldberry.media.PlaybackState;
import dev.goldberry.media.PlayerStatus;
import dev.goldberry.media.audio.AudioFormat;
import dev.goldberry.media.audio.VirtualSink;
import dev.goldberry.media.codec.MediaType;
import dev.goldberry.media.io.MediaIO;
import dev.goldberry.media.io.MediaIOProvider;
import dev.goldberry.media.io.MemoryIO;
import dev.goldberry.media.io.Source;
import dev.goldberry.media.view.AudioPlayer;
import dev.goldberry.media.view.MediaPlayerView;
import dev.goldberry.media.view.MediaStyles;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.render.model.PhysicalSize;
import dev.goldberry.text.font.Fonts;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.Density;
import dev.goldberry.widgets.core.Column;

/// The guide's pictures of the media widgets, in both of its themes at twice the
/// detail, held to what the widgets draw.
///
/// The widget pictures of every other chapter are rendered from the chapter's
/// own `kdl` sample by the example's `BookPicturesTest`. A media widget plays
/// through a player the application binds, so its sample cannot be rendered
/// alone; this test binds one. The player opens `guide-clip.mkv`, a still of a
/// lake at dusk with a subtitle track (`make-guide-clip.sh`), chooses the
/// subtitles and pauses on an accurate seek at four seconds, which is a third
/// of the way along the seek bar and inside the cue.
///
/// Deterministic for the reasons `MediaPlayerGoldenTest` gives: a sink that
/// never plays, a paused accurate seek and bit-exact software decoders.
/// `-Dgoldberry.golden.update=true` retakes the pictures.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("the guide's pictures of the media widgets")
class MediaPicturesTest {

    /// The scale the guide's pictures are taken at; a picture carries half its
    /// file's width as its logical width.
    static final float SCALE = 2f;

    /// Where the player is paused: inside the cue, a third of the way through.
    static final Duration AT = Duration.ofSeconds(4);

    /// The two themes of the book, and the suffix each picture's file carries.
    enum Shade {
        LIGHT(Theme.NORD_LIGHT, "light"),
        DARK(Theme.NORD_DARK, "dark");

        final Theme theme;
        final String suffix;

        Shade(Theme theme, String suffix) {
            this.theme = theme;
            this.suffix = suffix;
        }
    }

    /// One picture: the widget's markup name, which is also its file's stem and
    /// the guide heading it sits under, its logical size, and how to build it
    /// over the player.
    record Picture(String name, int width, int height, Function<MediaPlayer, Widget> widget) {

        String file(Shade shade) {
            return name + "-" + shade.suffix + ".webp";
        }
    }

    static final List<Picture> PICTURES = List.of(
            new Picture("media-player", 664, 384, MediaPlayerView::new),
            new Picture("audio-player", 664, 56, AudioPlayer::new));

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

    private final Path images =
            Path.of("..", "book", "src", "images").toAbsolutePath().normalize();
    private MediaPlayer player;
    private Fonts fonts;

    @BeforeAll
    void setUp() {
        RendererRequirement.enforce();
        FfmpegRequirement.enforce();
        fonts = Fonts.bundled();
        player = MediaPlayer.builder()
                .hardwareDecoding(HardwareDecoding.OFF)
                .sink(() -> new VirtualSink(AudioFormat.DEFAULT, false))
                .ioProviders(List.of(new Memory(fixture("guide-clip.mkv"))))
                .decoderProviders(List.of())
                .build();
        player.open(Source.of(URI.create("mem:///guide-clip.mkv")));
        await(status -> status.state() == PlaybackState.PLAYING);
        player.pause();
        var english =
                player.status().info().orElseThrow().tracks(MediaType.SUBTITLE).getFirst();
        player.selectTrack(english);
        await(status -> status.subtitles().isPresent());
        player.seek(AT);
        var deadline = System.nanoTime() + 10_000_000_000L;
        while (System.nanoTime() < deadline) {
            var picture = player.currentPicture();
            if (picture.isPresent()
                    && picture.get().ptsNanos() == AT.toNanos()
                    && !player.currentSubtitles().isEmpty()) {
                return;
            }
            Thread.onSpinWait();
        }
        throw new AssertionError("the picture and the cue at " + AT + " never came; " + player.status());
    }

    @AfterAll
    void tearDown() {
        if (player != null) {
            player.close();
        }
        if (fonts != null) {
            fonts.close();
        }
    }

    @Test
    @DisplayName("is taken of a player paused inside the cue, on the picture it asked for")
    void thePlayerIsWhereThePictureNeedsIt() {
        assertAll(
                () -> assertEquals(PlaybackState.PAUSED, player.status().state()),
                () -> assertEquals(
                        AT.toNanos(), player.currentPicture().orElseThrow().ptsNanos()),
                () -> assertTrue(player.currentSubtitles().stream()
                        .anyMatch(cue -> cue.text().contains("the last of the light"))));
    }

    @TestFactory
    @DisplayName("a media widget's picture is what the widget draws")
    Stream<DynamicTest> everyPictureIsCurrent() {
        return PICTURES.stream()
                .flatMap(picture -> Stream.of(Shade.values())
                        .map(shade -> DynamicTest.dynamicTest(picture.file(shade), () -> {
                            var physical = new PhysicalSize(
                                    Math.round(picture.width() * SCALE), Math.round(picture.height() * SCALE));
                            var widget =
                                    new Column(List.of(picture.widget().apply(player)), Attributes.NONE.id("scene"));
                            GoldenImage.assertMatchesAtOneScale(
                                    images.resolve(picture.file(shade)),
                                    physical.width(),
                                    physical.height(),
                                    SCALE,
                                    (size, scale) -> Offscreen.of(size)
                                            .scale(scale)
                                            .stylesheets(stylesheets(shade))
                                            .fonts(fonts)
                                            .render(widget));
                        })));
    }

    /// The controls' sheets in `shade`, the media sheet, and a padded scene as
    /// the guide's other widget pictures have. The player is given the height of
    /// the scene, so the clip shows at 640 by 360 with the controls over its foot.
    private static List<Stylesheet> stylesheets(Shade shade) {
        var sheets = new ArrayList<Stylesheet>(Controls.stylesheets(shade.theme, Density.REGULAR));
        sheets.add(MediaStyles.stylesheet());
        sheets.add(
                Stylesheet.parse(
                        CascadeLayer.APPLICATION,
                        "#scene { padding: 12px; flex-grow: 1; background: var(--gb-bg); --gb-media-player-min-height: 360px; }"));
        return sheets;
    }

    private void await(Predicate<PlayerStatus> until) {
        var deadline = System.nanoTime() + 10_000_000_000L;
        while (!until.test(player.status()) && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
    }

    private static byte[] fixture(String name) {
        try (var in = MediaPicturesTest.class.getResourceAsStream("/dev/goldberry/media/fixtures/" + name)) {
            if (in == null) {
                throw new IllegalStateException(name + " is not on the test's class path");
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
