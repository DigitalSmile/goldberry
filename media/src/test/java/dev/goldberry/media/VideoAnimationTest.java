package dev.goldberry.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.css.Theme;
import dev.goldberry.image.Image;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.core.image.AnimationView;

/// A video sticker playing as a moving picture: decoded on demand, with its
/// alpha, looped, and drawn by `AnimationView` over what is beneath it on a
/// virtual clock.
///
/// `sticker-vp9-alpha.webm` is 64×64 at 10 fps for one second, red, with an
/// opaque square at x `4n` to `4n + 15`, y 8 to 23 in picture `n`, half alpha from
/// row 40 down, and transparent elsewhere.
@DisplayName("VideoAnimation, a video sticker")
class VideoAnimationTest {

    private static final int BLUE = 0xFF0000FF;

    @BeforeEach
    void requireFfmpeg() {
        FfmpegRequirement.enforce();
    }

    static byte[] fixture(String name) {
        try (var in = VideoAnimationTest.class.getResourceAsStream("fixtures/" + name)) {
            if (in == null) {
                throw new IllegalStateException("no fixture " + name);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static VideoAnimation sticker() {
        return VideoAnimation.of(fixture("sticker-vp9-alpha.webm"));
    }

    private static int channel(int argb, int shift) {
        return (argb >>> shift) & 0xFF;
    }

    /// Whether `argb` is opaque red.
    private static boolean red(int argb) {
        return channel(argb, 24) == 0xFF && channel(argb, 16) > 240 && channel(argb, 8) < 8 && channel(argb, 0) < 8;
    }

    /// The left edge of the opaque square in row 16 of `image`, or -1.
    private static int squareAt(Image image) {
        for (var x = 0; x < image.width(); x++) {
            if (red(image.argb(x, 16))) {
                return x;
            }
        }
        return -1;
    }

    @Test
    @DisplayName("reads its size, one pass's length and whether it has alpha")
    void describes() {
        try (var sticker = sticker()) {
            assertEquals(64, sticker.width());
            assertEquals(64, sticker.height());
            assertEquals(1000, sticker.durationMillis());
            assertTrue(sticker.hasAlpha());
            assertTrue(sticker.isEndless());
            assertFalse(sticker.isDoneAt(60_000));
        }
        try (var clip = VideoAnimation.of(ByteBuffer.wrap(fixture("clip-vp9.webm")))) {
            assertEquals(160, clip.width());
            assertEquals(90, clip.height());
            assertFalse(clip.hasAlpha(), "a video without AlphaMode is opaque");
        }
    }

    @Test
    @DisplayName("the picture at a moment: premultiplied, transparent where the video is, and looped after a pass")
    void pictures() {
        try (var sticker = sticker()) {
            var first = sticker.imageAt(0);
            assertEquals(0, squareAt(first));
            assertEquals(0, first.argb(40, 16), "transparent is nothing at all");
            assertEquals(128, channel(first.argb(32, 50), 24), "half alpha");
            assertEquals(253, channel(first.argb(32, 50), 16), 2, "red, read back straight");
            var stored = first.pixels().pixels().getInt(50 * first.pixels().stride() + 32 * 4);
            assertEquals(126, channel(stored, 16), 2, "and stored premultiplied: half the red");

            assertEquals(20, squareAt(sticker.imageAt(550)), "picture 5 from 500 ms to 600 ms");
            assertEquals(36, squareAt(sticker.imageAt(999)), "the last picture");
            assertEquals(8, squareAt(sticker.imageAt(1_250)), "a second pass, picture 2");
            assertEquals(4, squareAt(sticker.imageAt(100)), "and back again, from the start");
        }
    }

    @Test
    @DisplayName("an AnimationView draws it over the background: through the transparent, blended with the half")
    void drawsOverTheBackground() {
        try (var sticker = sticker();
                var strip = Offscreen.of(64, 64)
                        .stylesheets(Controls.stylesheets(Theme.NORD_DARK))
                        .background(BLUE)
                        .strip(new AnimationView(sticker, "a red square"))) {
            var first = strip.frame();
            assertTrue(red(first.argb(4, 16)), "the opaque square");
            assertEquals(BLUE, first.argb(40, 16), "the background through the transparent");
            assertEquals(BLUE, first.argb(32, 35), "and between the two");
            var blended = first.argb(32, 50);
            assertEquals(0xFF, channel(blended, 24));
            assertEquals(126, channel(blended, 16), 3, "half red");
            assertEquals(127, channel(blended, 0), 3, "over half blue");
            assertTrue(strip.isAnimating(), "it plays, and asks for the next frame");
        }
    }

    @Test
    @DisplayName("moves on the frame clock, and loops after one second")
    void loopsOnTheVirtualClock() {
        try (var sticker = sticker();
                var strip = Offscreen.of(64, 64)
                        .stylesheets(Controls.stylesheets(Theme.NORD_DARK))
                        .background(BLUE)
                        .strip(AnimationView.decorative(sticker))) {
            assertEquals(0, squareAt(strip.frame()));
            assertEquals(20, squareAt(strip.advance(500).frame()), "half a second in: picture 5");
            assertEquals(36, squareAt(strip.advance(450).frame()), "950 ms: the last picture");
            assertEquals(4, squareAt(strip.advance(150).frame()), "1100 ms: picture 1 of the second pass");
            assertTrue(strip.isAnimating(), "endless");
        }
    }

    @Test
    @DisplayName("played once, it holds its last picture and stops asking for frames")
    void playsOnce() {
        try (var sticker = sticker();
                var strip = Offscreen.of(64, 64)
                        .stylesheets(Controls.stylesheets(Theme.NORD_DARK))
                        .background(BLUE)
                        .strip(AnimationView.decorative(sticker.loops(1)))) {
            strip.frame();
            assertTrue(strip.isAnimating());
            var last = strip.advance(1_500).frame();
            assertFalse(strip.isAnimating(), "done after its one pass");
            assertEquals(36, squareAt(last), "on its last picture");
        }
    }

    @Test
    @DisplayName("with autoplay off, stands on its first picture and asks for nothing")
    void autoplayOff() {
        try (var sticker = sticker();
                var strip = Offscreen.of(64, 64)
                        .stylesheets(Controls.stylesheets(Theme.NORD_DARK))
                        .background(BLUE)
                        .strip(AnimationView.decorative(sticker).autoplay(false))) {
            assertEquals(0, squareAt(strip.frame()));
            assertFalse(strip.isAnimating());
            assertEquals(0, squareAt(strip.advance(500).frame()), "still on its first picture");
        }
    }

    @Test
    @DisplayName("closed, it draws nothing and gives no picture")
    void closed() {
        var sticker = sticker();
        sticker.close();
        sticker.close();
        assertThrows(IllegalStateException.class, () -> sticker.imageAt(0));
        var drawn = Offscreen.of(64, 64).background(BLUE).paint((frame, size) -> sticker.paint(frame, 0, 0, 0, 64, 64));
        assertEquals(BLUE, drawn.argb(4, 16));
    }

    @Test
    @DisplayName("bytes that are not a video are refused as media errors")
    void refusesNonVideo() {
        var thrown = assertThrows(MediaException.class, () -> VideoAnimation.of(new byte[] {1, 2, 3, 4}));
        assertTrue(
                thrown.error() instanceof MediaError.InvalidData
                        || thrown.error() instanceof MediaError.UnsupportedContainer,
                thrown.error().toString());
        var sound = assertThrows(MediaException.class, () -> VideoAnimation.of(fixture("tone.flac")));
        assertTrue(sound.getMessage().contains("no video"), sound.getMessage());
    }
}
