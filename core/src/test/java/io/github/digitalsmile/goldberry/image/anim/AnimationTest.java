package io.github.digitalsmile.goldberry.image.anim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.image.gif.GifDecoder;

/// Every frame of a GIF, and which of them is being shown — [ADR-0382].
///
/// Two halves, tested apart because they are apart in the code: the decoder,
/// which turns a file into frames under GIF's disposal rules, and [Animation],
/// which is arithmetic over delays and owns no clock.
///
/// The disposal cases are built here rather than checked in as fixtures. A
/// three-frame GIF with a named disposal method is six lines of writer and is
/// unreadable as a binary blob — and what is being asserted *is* the file's
/// structure, so a test that writes the structure says what it means.
class AnimationTest {

    @Nested
    @DisplayName("the sequence in a file")
    class Decoding {

        @Test
        @DisplayName("a still GIF is a sequence of one")
        void still() {
            var sequence = GifDecoder.decodeAll(ByteBuffer.wrap(Gif.writer(2, 2)
                    .frame(Gif.KEEP, 100, new int[] {0, 1, 1, 0})
                    .bytes()));

            assertTrue(sequence.isStill());
            assertEquals(100, sequence.durationMillis());
            assertEquals(1, sequence.loopCount(), "a file with no NETSCAPE block plays once");
        }

        @Test
        @DisplayName("frames are composited onto the ones before them")
        void framesAccumulate() {
            // The case an optimizer writes: a full first frame, then a patch.
            var sequence = GifDecoder.decodeAll(ByteBuffer.wrap(Gif.writer(2, 2)
                    .frame(Gif.KEEP, 100, new int[] {0, 0, 0, 0})
                    .patch(Gif.KEEP, 100, 1, 0, 1, 1, new int[] {1})
                    .bytes()));

            assertEquals(2, sequence.frames().size());
            assertEquals(
                    List.of(Gif.RED, Gif.RED, Gif.RED, Gif.RED),
                    pixels(sequence, 0),
                    "the first frame is the whole screen");
            assertEquals(
                    List.of(Gif.RED, Gif.GREEN, Gif.RED, Gif.RED),
                    pixels(sequence, 1),
                    "and the second is the patch over what was already there");
        }

        @Test
        @DisplayName("`restore to background` clears the frame's own rectangle")
        void disposeToBackground() {
            var sequence = GifDecoder.decodeAll(ByteBuffer.wrap(Gif.writer(2, 2)
                    .frame(Gif.BACKGROUND, 100, new int[] {0, 0, 0, 0})
                    .patch(Gif.KEEP, 100, 0, 0, 1, 1, new int[] {1})
                    .bytes()));

            assertEquals(
                    List.of(Gif.GREEN, 0, 0, 0),
                    pixels(sequence, 1),
                    "the first frame asked to be cleared, so the second starts from nothing");
        }

        @Test
        @DisplayName("`restore to previous` puts back what was under the frame")
        void disposeToPrevious() {
            var sequence = GifDecoder.decodeAll(ByteBuffer.wrap(Gif.writer(2, 2)
                    .frame(Gif.KEEP, 100, new int[] {0, 0, 0, 0})
                    .patch(Gif.PREVIOUS, 100, 0, 0, 1, 1, new int[] {1})
                    .patch(Gif.KEEP, 100, 1, 1, 1, 1, new int[] {2})
                    .bytes()));

            assertEquals(List.of(Gif.GREEN, Gif.RED, Gif.RED, Gif.RED), pixels(sequence, 1));
            assertEquals(
                    List.of(Gif.RED, Gif.RED, Gif.RED, Gif.BLUE),
                    pixels(sequence, 2),
                    "the green patch was undone, because the frame carrying it said to put back what it covered");
        }

        @Test
        @DisplayName("a NETSCAPE block says how many times to play, and 0 is for ever")
        void loopCount() {
            assertEquals(
                    0,
                    GifDecoder.decodeAll(ByteBuffer.wrap(Gif.writer(2, 2)
                                    .loop(0)
                                    .frame(Gif.KEEP, 100, new int[] {0, 0, 0, 0})
                                    .bytes()))
                            .loopCount());
            assertEquals(
                    3,
                    GifDecoder.decodeAll(ByteBuffer.wrap(Gif.writer(2, 2)
                                    .loop(3)
                                    .frame(Gif.KEEP, 100, new int[] {0, 0, 0, 0})
                                    .bytes()))
                            .loopCount());
        }

        @Test
        @DisplayName("a delay below the floor every renderer applies becomes 100ms")
        void delayFloor() {
            // A file asking for 0 means "as fast as possible"; nobody obeys it,
            // and 10fps is what Firefox and Chromium both substitute.
            var sequence = GifDecoder.decodeAll(ByteBuffer.wrap(
                    Gif.writer(2, 2).frame(Gif.KEEP, 0, new int[] {0, 0, 0, 0}).bytes()));

            assertEquals(100, sequence.frames().getFirst().delayMillis());
        }

        @Test
        @DisplayName("and the first frame alone is what `decode` still reads")
        void singleFrameDecodeIsUnchanged() {
            var bytes = Gif.writer(2, 2)
                    .frame(Gif.KEEP, 100, new int[] {0, 0, 0, 0})
                    .patch(Gif.KEEP, 100, 0, 0, 1, 1, new int[] {1})
                    .bytes();

            var first = GifDecoder.decode(ByteBuffer.wrap(bytes));
            assertEquals(Gif.RED, first.argb()[0], "the patch belongs to the second frame");
        }

        private static List<Integer> pixels(GifDecoder.Sequence sequence, int frame) {
            var image = sequence.frames().get(frame).image();
            var found = new ArrayList<Integer>();
            for (var pixel : image.argb()) {
                found.add(pixel);
            }
            return found;
        }
    }

    @Nested
    @DisplayName("which frame is showing")
    class Playback {

        private static Animation of(int loops, int... delays) {
            var frames = new ArrayList<Animation.Frame>();
            for (var delay : delays) {
                frames.add(new Animation.Frame(
                        io.github.digitalsmile.goldberry.image.Image.ofArgb(1, 1, new int[] {0xFF000000 + delay}),
                        delay));
            }
            return new Animation(frames, loops);
        }

        @Test
        @DisplayName("a still is itself at every moment")
        void still() {
            var animation = of(0, 40);
            assertTrue(animation.isStill());
            assertSame(animation.frames().getFirst(), animation.at(0));
            assertSame(animation.frames().getFirst(), animation.at(10_000));
        }

        @Test
        @DisplayName("each frame is shown for its own delay")
        void frameByDelay() {
            var animation = of(0, 100, 200);

            assertEquals(100, animation.at(0).delayMillis());
            assertEquals(100, animation.at(99).delayMillis());
            assertEquals(200, animation.at(100).delayMillis(), "the second frame starts where the first ends");
            assertEquals(200, animation.at(299).delayMillis());
        }

        @Test
        @DisplayName("an endless animation wraps")
        void wraps() {
            var animation = of(0, 100, 200);
            assertEquals(300, animation.durationMillis());
            assertTrue(animation.isEndless());
            assertEquals(-1, animation.totalMillis(), "nothing is ever waiting for it to end");
            assertEquals(100, animation.at(300).delayMillis(), "back to the first frame");
            assertEquals(200, animation.at(3_000_100).delayMillis(), "and still counting an hour later");
        }

        @Test
        @DisplayName("a finite one stops on its last frame")
        void stops() {
            var animation = of(2, 100, 200);

            assertEquals(600, animation.totalMillis(), "two passes of 300ms");
            assertEquals(100, animation.at(300).delayMillis(), "the second pass starts again");
            assertEquals(200, animation.at(600).delayMillis(), "and then it stays on the last picture");
            assertEquals(200, animation.at(60_000).delayMillis());
        }

        @Test
        @DisplayName("a moment before it started is its first frame")
        void beforeTheStart() {
            // A caller comparing two clocks should get the first frame rather
            // than an exception.
            assertEquals(100, of(0, 100, 200).at(-5000).delayMillis());
        }

        @Test
        @DisplayName("an animation with no frames is not one")
        void empty() {
            assertThrows(IllegalArgumentException.class, () -> new Animation(List.of(), 0));
        }

        @Test
        @DisplayName("a still image is an animation, so a caller needs no branch")
        void everyImageIsOne() {
            var animation =
                    Animation.still(io.github.digitalsmile.goldberry.image.Image.ofArgb(1, 1, new int[] {0xFF112233}));

            assertTrue(animation.isStill());
            assertTrue(animation.isEndless());
            assertFalse(animation.frames().isEmpty());
            assertEquals(0xFF112233, animation.imageAt(5000).argb(0, 0));
        }
    }

    /// The smallest GIF writer that can say what these tests need to say: a
    /// palette of three colours, frames of literal pixels, and a named disposal
    /// method on each.
    ///
    /// The LZW is deliberately the dumbest correct encoder there is — a clear
    /// code before every pair of pixels, so the code width never grows past its
    /// first size. That is a legal GIF and an inefficient one, which for a 2×2
    /// picture is exactly the right trade.
    private static final class Gif {

        static final int RED = 0xFFFF0000;
        static final int GREEN = 0xFF00FF00;
        static final int BLUE = 0xFF0000FF;

        static final int KEEP = 1;
        static final int BACKGROUND = 2;
        static final int PREVIOUS = 3;

        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private final int width;
        private final int height;

        private Gif(int width, int height) {
            this.width = width;
            this.height = height;
            write("GIF89a".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            short16(width);
            short16(height);
            // A global table of four colours, the fourth unused, so the packed
            // size field is 1 (2^(1+1) = 4).
            byte8(0x80 | 0x01);
            byte8(0);
            byte8(0);
            for (var colour : new int[] {RED, GREEN, BLUE, 0xFF000000}) {
                byte8((colour >> 16) & 0xFF);
                byte8((colour >> 8) & 0xFF);
                byte8(colour & 0xFF);
            }
        }

        static Gif writer(int width, int height) {
            return new Gif(width, height);
        }

        /// A NETSCAPE2.0 application extension — `times` of 0 means for ever.
        Gif loop(int times) {
            byte8(0x21);
            byte8(0xFF);
            byte8(11);
            write("NETSCAPE2.0".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            byte8(3);
            byte8(1);
            short16(times);
            byte8(0);
            return this;
        }

        /// A full-screen frame.
        Gif frame(int disposal, int delayMillis, int[] indices) {
            return patch(disposal, delayMillis, 0, 0, width, height, indices);
        }

        /// A frame covering part of the screen.
        Gif patch(int disposal, int delayMillis, int left, int top, int w, int h, int[] indices) {
            byte8(0x21);
            byte8(0xF9);
            byte8(4);
            byte8((disposal << 2));
            short16(delayMillis / 10);
            byte8(0);
            byte8(0);

            byte8(0x2C);
            short16(left);
            short16(top);
            short16(w);
            short16(h);
            byte8(0);
            lzw(indices);
            return this;
        }

        byte[] bytes() {
            byte8(0x3B);
            return out.toByteArray();
        }

        /// Literal codes at a fixed width, cleared often enough that the width
        /// never has to grow.
        private void lzw(int[] indices) {
            var minimum = 2;
            byte8(minimum);
            var codes = new ArrayList<Integer>();
            var clear = 1 << minimum;
            var end = clear + 1;
            codes.add(clear);
            var sinceClear = 0;
            for (var index : indices) {
                if (sinceClear == 2) {
                    codes.add(clear);
                    sinceClear = 0;
                }
                codes.add(index);
                sinceClear++;
            }
            codes.add(end);

            var packed = new ByteArrayOutputStream();
            var bits = 0;
            var buffer = 0;
            for (var code : codes) {
                buffer |= code << bits;
                bits += minimum + 1;
                while (bits >= 8) {
                    packed.write(buffer & 0xFF);
                    buffer >>= 8;
                    bits -= 8;
                }
            }
            if (bits > 0) {
                packed.write(buffer & 0xFF);
            }
            var data = packed.toByteArray();
            byte8(data.length);
            write(data);
            byte8(0);
        }

        private void byte8(int value) {
            out.write(value & 0xFF);
        }

        private void short16(int value) {
            out.write(value & 0xFF);
            out.write((value >> 8) & 0xFF);
        }

        private void write(byte[] bytes) {
            try {
                out.write(bytes);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
