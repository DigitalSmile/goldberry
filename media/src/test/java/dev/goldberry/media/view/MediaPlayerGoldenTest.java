package dev.goldberry.media.view;

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
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.assets.BundledFont;
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
import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.MediaType;
import dev.goldberry.media.io.MediaIO;
import dev.goldberry.media.io.MediaIOProvider;
import dev.goldberry.media.io.MemoryIO;
import dev.goldberry.media.io.Source;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.text.font.Font;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.Density;
import dev.goldberry.widgets.core.Column;

/// `media-player` and `video-view` as they are drawn: paused on a known picture,
/// and failed.
///
/// Deterministic because the sink is a [VirtualSink] that never plays and the
/// player is paused on an accurate seek, so the picture is the one at 400 ms
/// every run, and the decoders and the conversion are bit-exact.
///
/// **One scale**, for the reason `GoldenImage.assertMatchesAtOneScale` names: a
/// decoded 160×90 picture is scaled to its box at the blit, so the 2× render is a
/// different resampling of the same pixels rather than the same picture in more
/// of them.
@DisplayName("media-player and video-view, drawn")
class MediaPlayerGoldenTest {

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
    private Font font;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        FfmpegRequirement.enforce();
        font = Font.bundled(BundledFont.UI, 13);
    }

    @AfterEach
    void tearDown() {
        if (player != null) {
            player.close();
        }
        if (font != null) {
            font.close();
        }
    }

    private void open(String fixture, Predicate<PlayerStatus> until) {
        byte[] data;
        try (var in = getClass().getResourceAsStream("/dev/goldberry/media/fixtures/" + fixture)) {
            data = in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        player = MediaPlayer.builder()
                .hardwareDecoding(HardwareDecoding.OFF)
                .sink(() -> new VirtualSink(AudioFormat.DEFAULT, false))
                .ioProviders(List.of(new Memory(data)))
                .decoderProviders(List.of())
                .build();
        player.open(Source.of(URI.create("mem:///" + fixture)));
        awaitStatus(until);
    }

    private void awaitStatus(Predicate<PlayerStatus> until) {
        var deadline = System.nanoTime() + 10_000_000_000L;
        while (!until.test(player.status()) && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
    }

    /// Paused on the picture at 400 ms.
    private void pausedAt400() {
        open("clip-vp9.webm", status -> status.state() == PlaybackState.PLAYING);
        player.pause();
        player.seek(Duration.ofMillis(400));
        var deadline = System.nanoTime() + 10_000_000_000L;
        while (System.nanoTime() < deadline) {
            var picture = player.currentPicture();
            if (picture.isPresent() && picture.get().ptsNanos() == 400_000_000L) {
                return;
            }
            Thread.onSpinWait();
        }
        throw new AssertionError("the picture at 400 ms never came; " + player.status());
    }

    private void assertGolden(String name, Widget widget, int width, int height) {
        var sheets = new ArrayList<Stylesheet>(Controls.stylesheets(Theme.NORD_DARK, Density.REGULAR));
        sheets.add(MediaStyles.stylesheet());
        sheets.add(Stylesheet.parse(CascadeLayer.APPLICATION, "#scene { padding: 12px; flex-grow: 1; }"));
        var scene = new Column(List.of(widget), Attributes.NONE.id("scene"));
        GoldenImage.assertMatchesAtOneScale(
                name,
                width,
                height,
                1f,
                (size, scale) -> Offscreen.of(size)
                        .scale(scale)
                        .stylesheets(sheets)
                        .font(font)
                        .render(scene));
    }

    @Test
    @DisplayName("media-player, paused: the picture letterboxed, and the controls over its foot")
    void paused() {
        pausedAt400();
        assertGolden("media-player-paused", new MediaPlayerView(player), 584, 204);
    }

    @Test
    @DisplayName("media-player with subtitles: a two-line cue over the foot, above the controls")
    void subtitles() {
        open("clip-vp9-subs.mkv", status -> status.state() == PlaybackState.PLAYING);
        player.pause();
        var french = player.status().info().orElseThrow().tracks(MediaType.SUBTITLE).stream()
                .filter(track -> track.codec() == CodecId.ASS)
                .findFirst()
                .orElseThrow();
        player.selectTrack(french);
        awaitStatus(status -> status.subtitles().isPresent());
        player.seek(Duration.ofMillis(300));
        var deadline = System.nanoTime() + 10_000_000_000L;
        while (System.nanoTime() < deadline) {
            var picture = player.currentPicture();
            if (picture.isPresent()
                    && picture.get().ptsNanos() == 280_000_000L
                    && !player.currentSubtitles().isEmpty()) {
                break;
            }
            Thread.onSpinWait();
        }
        assertGolden("media-player-subtitles", new MediaPlayerView(player), 584, 204);
    }

    @Test
    @DisplayName("video-view with cover: the picture filling the box, cropped")
    void cover() {
        pausedAt400();
        assertGolden("video-view-cover", new VideoView(player, dev.goldberry.widgets.core.image.Fit.COVER), 400, 160);
    }

    @Test
    @DisplayName("media-player, failed: which codecs it could not play")
    void failed() {
        open("clip-h264-aac.mp4", status -> status.state() == PlaybackState.ERROR);
        assertGolden("media-player-error", new MediaPlayerView(player), 584, 204);
    }
}
