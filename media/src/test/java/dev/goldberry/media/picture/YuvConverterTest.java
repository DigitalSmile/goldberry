package dev.goldberry.media.picture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import dev.goldberry.media.MediaNativesRequirement;
import dev.goldberry.media.codec.ColorDescription;
import dev.goldberry.media.codec.PixelFormat;
import dev.goldberry.media.codec.VideoFrame;
import dev.goldberry.media.ffm.MediaNatives;

/// libyuv through [YuvConverter], on pictures written by hand: flat fields whose
/// RGB is known from the matrices, patterns whose layouts must agree, and a
/// test card held to [YuvReference], the arithmetic GPU present is held to too.
@DisplayName("YuvConverter")
class YuvConverterTest {

    /// One BGRA pixel read as `0xAARRGGBB`.
    private static final ValueLayout.OfInt PIXEL = ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN);

    private static final int WIDTH = 6;
    private static final int HEIGHT = 4;

    /// The test card's size: wide enough for chroma blocks that change every
    /// three chroma samples, both ways.
    private static final int CARD_WIDTH = 36;
    private static final int CARD_HEIGHT = 12;

    /// How far libyuv's fixed point may land from the reference's floating
    /// point, a channel.
    private static final int REFERENCE_TOLERANCE = 2;

    /// The same for a ten-bit layout: libyuv's ten-bit path truncates its last
    /// shift where the eight-bit one rounds, a level more.
    private static final int TEN_BIT_REFERENCE_TOLERANCE = 3;

    /// A picture's colour and layout, for the flat fields: 8-bit values; the
    /// 16-bit layouts scale them to ten bits.
    private record Fill(int y, int u, int v, int alpha) {
        Fill(int y, int u, int v) {
            this(y, u, v, 255);
        }
    }

    private Arena arena = Arena.ofConfined();

    @BeforeEach
    void requireNatives() {
        MediaNativesRequirement.enforce();
    }

    @AfterEach
    void closeArena() {
        arena.close();
    }

    @Test
    @DisplayName("limited-range BT.601: Y 235 is white and Y 16 is black")
    void limitedRangeExtremes() {
        assertEquals(
                0xFFFFFFFF, flat(PixelFormat.I420, new Fill(235, 128, 128, 255), VideoFrame.ColorMatrix.BT601, false));
        assertEquals(
                0xFF000000, flat(PixelFormat.I420, new Fill(16, 128, 128, 255), VideoFrame.ColorMatrix.BT601, false));
    }

    @Test
    @DisplayName("full-range BT.601: Y 255 is white and Y 0 is black")
    void fullRangeExtremes() {
        assertEquals(
                0xFFFFFFFF, flat(PixelFormat.I420, new Fill(255, 128, 128, 255), VideoFrame.ColorMatrix.BT601, true));
        assertEquals(
                0xFF000000, flat(PixelFormat.I420, new Fill(0, 128, 128, 255), VideoFrame.ColorMatrix.BT601, true));
    }

    @Test
    @DisplayName("BT.601 red (81, 90, 240) lands within two levels of (255, 0, 0)")
    void red() {
        var pixel = flat(PixelFormat.I420, new Fill(81, 90, 240), VideoFrame.ColorMatrix.BT601, false);
        assertClose(0xFFFF0000, pixel, 2);
    }

    @Test
    @DisplayName("BT.601 green and blue land where the matrix puts them")
    void greenAndBlue() {
        assertClose(0xFF00FF00, flat(PixelFormat.I420, new Fill(145, 54, 34), VideoFrame.ColorMatrix.BT601, false), 2);
        assertClose(0xFF0000FF, flat(PixelFormat.I420, new Fill(41, 240, 110), VideoFrame.ColorMatrix.BT601, false), 2);
    }

    @Test
    @DisplayName("BT.709 and BT.2020 give visibly different RGB from BT.601 for the same chroma")
    void matricesDiffer() {
        var fill = new Fill(128, 90, 200);
        var bt601 = flat(PixelFormat.I420, fill, VideoFrame.ColorMatrix.BT601, false);
        var bt709 = flat(PixelFormat.I420, fill, VideoFrame.ColorMatrix.BT709, false);
        var bt2020 = flat(PixelFormat.I420, fill, VideoFrame.ColorMatrix.BT2020, false);
        assertTrue(distance(bt601, bt709) >= 4, "601 " + hex(bt601) + " vs 709 " + hex(bt709));
        assertTrue(distance(bt601, bt2020) >= 4, "601 " + hex(bt601) + " vs 2020 " + hex(bt2020));
        assertTrue(distance(bt709, bt2020) >= 2, "709 " + hex(bt709) + " vs 2020 " + hex(bt2020));
        // Each matrix is white at the top of its range, whatever the chroma does
        // in between.
        for (var matrix : VideoFrame.ColorMatrix.values()) {
            assertEquals(0xFFFFFFFF, flat(PixelFormat.I420, new Fill(235, 128, 128), matrix, false), matrix.name());
            assertEquals(0xFFFFFFFF, flat(PixelFormat.I420, new Fill(255, 128, 128), matrix, true), matrix.name());
        }
    }

    @Test
    @DisplayName("NV12 and I420 of the same picture are the same bytes")
    void nv12MatchesI420() {
        try (var converter = new YuvConverter(MediaNatives.get())) {
            var i420 = convert(converter, pattern(PixelFormat.I420, 0));
            var nv12 = convert(converter, pattern(PixelFormat.NV12, 0));
            assertArrayEquals(i420, nv12);
            var padded = convert(converter, pattern(PixelFormat.NV12, 32));
            assertArrayEquals(i420, padded, "padded rows");
        }
    }

    @Test
    @DisplayName("I010 and P010 convert ten-bit values like the eight-bit layouts, and agree with each other")
    void tenBit() {
        // libyuv's ten-bit path truncates its last shift where the eight-bit one
        // rounds, so white is a level short of 255: within one, not equal.
        assertClose(
                0xFFFFFFFF, flat(PixelFormat.I010, new Fill(235, 128, 128), VideoFrame.ColorMatrix.BT601, false), 1);
        assertClose(0xFF000000, flat(PixelFormat.I010, new Fill(16, 128, 128), VideoFrame.ColorMatrix.BT601, false), 1);
        assertClose(
                0xFFFFFFFF, flat(PixelFormat.P010, new Fill(235, 128, 128), VideoFrame.ColorMatrix.BT709, false), 1);
        assertClose(0xFF000000, flat(PixelFormat.P010, new Fill(16, 128, 128), VideoFrame.ColorMatrix.BT709, false), 1);
        assertClose(0xFFFFFFFF, flat(PixelFormat.I010, new Fill(255, 128, 128), VideoFrame.ColorMatrix.BT601, true), 1);
        assertClose(0xFFFF0000, flat(PixelFormat.I010, new Fill(81, 90, 240), VideoFrame.ColorMatrix.BT601, false), 2);
        assertClose(0xFFFF0000, flat(PixelFormat.P010, new Fill(81, 90, 240), VideoFrame.ColorMatrix.BT601, false), 2);
        try (var converter = new YuvConverter(MediaNatives.get())) {
            var i010 = convert(converter, pattern(PixelFormat.I010, 0));
            var p010 = convert(converter, pattern(PixelFormat.P010, 16));
            // P010's chroma is interpolated on its samples as stored, ten bits
            // high, and I010's on ten-bit codes: a level apart where it changes.
            for (var i = 0; i < i010.length; i++) {
                assertClose(i010[i], p010[i], 1);
            }
            var i420 = convert(converter, pattern(PixelFormat.I420, 0));
            for (var i = 0; i < i420.length; i++) {
                assertClose(i420[i], i010[i], TEN_BIT_REFERENCE_TOLERANCE);
            }
        }
    }

    static Stream<Arguments> everyReading() {
        var all = Stream.<Arguments>builder();
        for (var format : PixelFormat.values()) {
            for (var matrix : VideoFrame.ColorMatrix.values()) {
                for (var fullRange : new boolean[] {false, true}) {
                    all.add(Arguments.of(format, matrix, fullRange));
                }
            }
        }
        return all.build();
    }

    @ParameterizedTest(name = "{0} {1} full range {2}")
    @MethodSource("everyReading")
    @DisplayName("converts as GPU present does: chroma blended at its centre, the tagged matrix and range")
    void asGpuPresent(PixelFormat format, VideoFrame.ColorMatrix matrix, boolean fullRange) {
        try (var converter = new YuvConverter(MediaNatives.get())) {
            var frame = card(format, matrix, fullRange);
            var tolerance = format.bytesPerSample() == 2 ? TEN_BIT_REFERENCE_TOLERANCE : REFERENCE_TOLERANCE;
            YuvDiagnosis.assertReadsAsTagged(
                    YuvSource.of(frame), convert(converter, frame), tolerance, format + " " + matrix);
        }
    }

    /// How far an HDR picture lands from the reference, a channel: the
    /// natives convert in floats, so what is left is the tables'
    /// interpolation and the last rounding.
    private static final int HDR_REFERENCE_TOLERANCE = 1;

    static Stream<Arguments> hdrReadings() {
        return Stream.of(
                Arguments.of(PixelFormat.I010, ColorDescription.fromH273(9, 16).withLightLevels(1000, 0)),
                Arguments.of(PixelFormat.P010, ColorDescription.fromH273(9, 16).withLightLevels(4000, 0)),
                Arguments.of(PixelFormat.I010, ColorDescription.fromH273(9, 18)),
                Arguments.of(PixelFormat.P010, ColorDescription.fromH273(9, 14)),
                Arguments.of(PixelFormat.I420, ColorDescription.fromH273(12, 1)),
                Arguments.of(PixelFormat.NV12, ColorDescription.fromH273(9, 16)));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("hdrReadings")
    @DisplayName("maps HDR and wide gamut to SDR BT.709 as the reference does")
    void hdr(PixelFormat format, ColorDescription colour) {
        try (var converter = new YuvConverter(MediaNatives.get())) {
            var frame = card(format, VideoFrame.ColorMatrix.BT2020, false).withColourFrom(colour);
            var tolerance = HDR_REFERENCE_TOLERANCE;
            YuvDiagnosis.assertReadsAsTagged(
                    YuvSource.of(frame), convert(converter, frame), tolerance, format + " " + colour);
        }
    }

    @Test
    @DisplayName("an HDR picture is not drawn as its SDR reading: the mapping is seen")
    void hdrIsMapped() {
        try (var converter = new YuvConverter(MediaNatives.get())) {
            var sdr = card(PixelFormat.I010, VideoFrame.ColorMatrix.BT2020, false);
            var pq = sdr.withColourFrom(ColorDescription.fromH273(9, 16));
            assertTrue(
                    YuvReference.worst(convert(converter, sdr), convert(converter, pq)) > 20,
                    "PQ is drawn as PQ, not as the SDR the same codes would be");
        }
    }

    @Test
    @DisplayName("a picture with alpha is drawn as SDR whatever its description says")
    void alphaStaysSdr() {
        try (var converter = new YuvConverter(MediaNatives.get())) {
            var plain = card(PixelFormat.I420A, VideoFrame.ColorMatrix.BT709, false);
            var tagged = plain.withColourFrom(ColorDescription.fromH273(9, 16));
            assertArrayEquals(convert(converter, plain), convert(converter, tagged));
        }
    }

    @Test
    @DisplayName("chroma is blended across a hard edge, not repeated over each 2×2 block")
    void blendsChroma() {
        try (var converter = new YuvConverter(MediaNatives.get())) {
            var frame = card(PixelFormat.I420, VideoFrame.ColorMatrix.BT601, false);
            var source = YuvSource.of(frame);
            var actual = convert(converter, frame);
            var repeated = YuvReference.argb(
                    source,
                    new YuvReference.Reading(
                            VideoFrame.ColorMatrix.BT601, false, YuvReference.Chroma.REPEATED, false, false));
            assertTrue(
                    YuvReference.worst(repeated, actual) > 40,
                    "the card's chroma edges must tell blended from repeated");
            assertEquals(
                    YuvReference.Chroma.BLENDED,
                    YuvDiagnosis.closest(source, actual).reading().chroma());
        }
    }

    @Test
    @DisplayName("I420A premultiplies: alpha 128 on white is (128, 128, 128, 128), alpha 0 is all zeroes")
    void premultiplies() {
        var half = flat(PixelFormat.I420A, new Fill(235, 128, 128, 128), VideoFrame.ColorMatrix.BT601, false);
        assertClose(0x80808080, half, 1);
        assertEquals(0, flat(PixelFormat.I420A, new Fill(235, 128, 128, 0), VideoFrame.ColorMatrix.BT601, false));
        assertEquals(
                0xFFFFFFFF, flat(PixelFormat.I420A, new Fill(235, 128, 128, 255), VideoFrame.ColorMatrix.BT601, false));
        var quarterRed = flat(PixelFormat.I420A, new Fill(81, 90, 240, 64), VideoFrame.ColorMatrix.BT601, false);
        assertClose(0x40400000, quarterRed, 2);
    }

    @ParameterizedTest
    @EnumSource(PixelFormat.class)
    @DisplayName("converting twice gives identical bytes, through a wider target stride too")
    void repeatable(PixelFormat format) {
        try (var converter = new YuvConverter()) {
            var frame = pattern(format, 8);
            var first = convert(converter, frame);
            assertArrayEquals(first, convert(converter, frame));
            var stride = WIDTH * 4 + 64;
            var wide = arena.allocate((long) stride * HEIGHT);
            wide.fill((byte) 0xAB);
            converter.toBgra(frame, wide, stride);
            for (var y = 0; y < HEIGHT; y++) {
                for (var x = 0; x < WIDTH; x++) {
                    assertEquals(
                            first[y * WIDTH + x],
                            wide.get(PIXEL, (long) y * stride + 4L * x),
                            "(" + x + ", " + y + ")");
                }
                // The padding is nobody's to write.
                assertEquals((byte) 0xAB, wide.get(ValueLayout.JAVA_BYTE, (long) y * stride + 4L * WIDTH));
            }
        }
    }

    @Test
    @DisplayName("a ten-bit plane's stride is passed in samples: rows wider than the picture convert the right pixels")
    void tenBitStrideInSamples() {
        try (var converter = new YuvConverter(MediaNatives.get())) {
            var tight = convert(converter, pattern(PixelFormat.I010, 0));
            var tightP010 = convert(converter, pattern(PixelFormat.P010, 0));
            // The padding is filled with a value no sample has, so a stride taken
            // in bytes would read it as picture from the second row on.
            for (var padding : new int[] {2, 10, 64}) {
                assertArrayEquals(tight, convert(converter, pattern(PixelFormat.I010, padding)), "I010 + " + padding);
                assertArrayEquals(
                        tightP010, convert(converter, pattern(PixelFormat.P010, padding)), "P010 + " + padding);
            }
            var rows = new int[HEIGHT];
            for (var y = 0; y < HEIGHT; y++) {
                rows[y] = tight[y * WIDTH];
            }
            assertEquals(
                    HEIGHT, Arrays.stream(rows).distinct().count(), "the rows differ, so a wrong stride would show");
        }
    }

    @Test
    @DisplayName("an odd-sized picture converts, its chroma rounded up")
    void oddSize() {
        try (var converter = new YuvConverter(MediaNatives.get())) {
            var frame =
                    flatFrame(PixelFormat.I420, 5, 3, new Fill(235, 128, 128), VideoFrame.ColorMatrix.BT601, false, 0);
            var target = arena.allocate(5L * 4 * 3);
            converter.toBgra(frame, target, 5 * 4);
            for (var i = 0; i < 15; i++) {
                assertEquals(0xFFFFFFFF, target.get(PIXEL, 4L * i), "pixel " + i);
            }
        }
    }

    @Test
    @DisplayName("a converter refuses a target too small for the picture, before libyuv writes to it")
    void refusesSmallTarget() {
        try (var converter = new YuvConverter(MediaNatives.get())) {
            var frame = pattern(PixelFormat.I420, 0);
            var small = arena.allocate(16);
            assertThrows(IllegalArgumentException.class, () -> converter.toBgra(frame, small, WIDTH * 4));
            var target = arena.allocate((long) WIDTH * 4 * HEIGHT);
            assertThrows(IllegalArgumentException.class, () -> converter.toBgra(frame, target, WIDTH));
            var short1 = arena.allocate((long) WIDTH * 4 * HEIGHT - 1);
            assertThrows(IllegalArgumentException.class, () -> converter.toBgra(frame, short1, WIDTH * 4));
        }
    }

    @Test
    @DisplayName("a sixteen-bit plane with an odd stride is refused")
    void refusesOddStride() {
        try (var converter = new YuvConverter(MediaNatives.get())) {
            var frame = pattern(PixelFormat.I010, 1);
            var target = arena.allocate((long) WIDTH * 4 * HEIGHT);
            assertThrows(IllegalArgumentException.class, () -> converter.toBgra(frame, target, WIDTH * 4));
        }
    }

    @Test
    @DisplayName("a closed converter refuses to convert; closing twice is nothing")
    void closedConverter() {
        var converter = new YuvConverter(MediaNatives.get(), false);
        converter.close();
        converter.close();
        var frame = pattern(PixelFormat.I420, 0);
        var target = arena.allocate((long) WIDTH * 4 * HEIGHT);
        assertThrows(IllegalStateException.class, () -> converter.toBgra(frame, target, WIDTH * 4));
    }

    @Test
    @DisplayName("the fast flag changes nothing: the same bytes either way")
    void exactFlagIsNominal() {
        try (var exact = new YuvConverter(true);
                var fast = new YuvConverter(false)) {
            var frame = pattern(PixelFormat.I420A, 0);
            assertArrayEquals(convert(exact, frame), convert(fast, frame));
        }
    }

    /// The one colour every pixel of a flat `format` picture of `fill` converts
    /// to, as `0xAARRGGBB`.
    private int flat(PixelFormat format, Fill fill, VideoFrame.ColorMatrix matrix, boolean fullRange) {
        try (var converter = new YuvConverter(MediaNatives.get())) {
            var pixels = convert(converter, flatFrame(format, WIDTH, HEIGHT, fill, matrix, fullRange, 0));
            var distinct = Arrays.stream(pixels).distinct().toArray();
            assertEquals(1, distinct.length, "a flat picture is one colour, not " + hexes(distinct));
            return distinct[0];
        }
    }

    private int[] convert(YuvConverter converter, VideoFrame frame) {
        var stride = frame.width() * 4;
        var target = arena.allocate((long) stride * frame.height());
        converter.toBgra(frame, target, stride);
        var pixels = new int[frame.width() * frame.height()];
        for (var i = 0; i < pixels.length; i++) {
            pixels[i] = target.get(PIXEL, 4L * i);
        }
        return pixels;
    }

    /// A `width × height` picture of `format` whose every sample is `fill`'s, with
    /// `padding` bytes more a row than the rows need.
    private VideoFrame flatFrame(
            PixelFormat format,
            int width,
            int height,
            Fill fill,
            VideoFrame.ColorMatrix matrix,
            boolean fullRange,
            int padding) {
        return frame(format, width, height, matrix, fullRange, padding, (plane, x, y) -> switch (format) {
            case I420, I010 -> plane == 0 ? fill.y() : plane == 1 ? fill.u() : fill.v();
            case NV12, P010 -> plane == 0 ? fill.y() : (x & 1) == 0 ? fill.u() : fill.v();
            case I420A -> plane == 0 ? fill.y() : plane == 1 ? fill.u() : plane == 2 ? fill.v() : fill.alpha();
        });
    }

    /// A [#CARD_WIDTH]`×`[#CARD_HEIGHT] picture of `format` whose chroma is
    /// saturated blocks with hard edges between them, an odd number of luma
    /// columns into each, and luma a gradient: what tells every reading of a
    /// picture apart. Opaque, where the layout has alpha.
    private VideoFrame card(PixelFormat format, VideoFrame.ColorMatrix matrix, boolean fullRange) {
        return frame(format, CARD_WIDTH, CARD_HEIGHT, matrix, fullRange, 6, (plane, x, y) -> {
            if (plane == 0) {
                return 20 + x * 6 + (y & 1) * 5;
            }
            var semiPlanar = format == PixelFormat.NV12 || format == PixelFormat.P010;
            var component = semiPlanar ? 1 + (x & 1) : plane;
            var column = semiPlanar ? x / 2 : x;
            var block = column / 3 + (y / 2) * 7;
            return switch (component) {
                case 1 -> 30 + (block * 53) % 196;
                case 2 -> 226 - (block * 71) % 196;
                default -> 255;
            };
        });
    }

    /// A [#WIDTH]`×`[#HEIGHT] BT.709 picture of `format` with a gradient in each
    /// plane, the same picture whatever the layout: luma rises along the rows,
    /// chroma along the columns, alpha with both.
    private VideoFrame pattern(PixelFormat format, int padding) {
        return frame(format, WIDTH, HEIGHT, VideoFrame.ColorMatrix.BT709, false, padding, (plane, x, y) -> {
            if (plane == 0) {
                return 16 + (y * WIDTH + x) * 219 / (WIDTH * HEIGHT - 1);
            }
            var semiPlanar = format == PixelFormat.NV12 || format == PixelFormat.P010;
            var component = semiPlanar ? 1 + (x & 1) : plane;
            var column = semiPlanar ? x / 2 : x;
            if (component == 1) {
                return 60 + column * 40 + y * 7;
            }
            if (component == 2) {
                return 200 - column * 30 - y * 11;
            }
            return 255 - (x * 37 + y * 53) % 256;
        });
    }

    /// What a plane's sample is at a position, as an 8-bit value.
    private interface Sampler {
        int sample(int plane, int x, int y);
    }

    private VideoFrame frame(
            PixelFormat format,
            int width,
            int height,
            VideoFrame.ColorMatrix matrix,
            boolean fullRange,
            int padding,
            Sampler sampler) {
        var planes = new ArrayList<MemorySegment>();
        var strides = new ArrayList<Integer>();
        var bytes = format.bytesPerSample();
        for (var plane = 0; plane < format.planes(); plane++) {
            var rowBytes = format.planeRowBytes(plane, width);
            var rows = format.planeRows(plane, height);
            var stride = rowBytes + padding;
            var segment = arena.allocate((long) stride * rows, 64);
            segment.fill((byte) 0x7E);
            for (var y = 0; y < rows; y++) {
                for (var x = 0; x < rowBytes / bytes; x++) {
                    var value = sampler.sample(plane, x, y);
                    var at = (long) y * stride + (long) x * bytes;
                    if (bytes == 1) {
                        segment.set(ValueLayout.JAVA_BYTE, at, (byte) value);
                    } else {
                        // Ten bits: in the low bits for I010, the high bits for P010.
                        var tenBit = value * 4;
                        var stored = format == PixelFormat.P010 ? tenBit << 6 : tenBit;
                        segment.set(ValueLayout.JAVA_SHORT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN), at, (short)
                                stored);
                    }
                }
            }
            planes.add(segment);
            strides.add(stride);
        }
        return new VideoFrame(format, width, height, List.copyOf(planes), List.copyOf(strides), matrix, fullRange, 0);
    }

    private static void assertClose(int expected, int actual, int tolerance) {
        for (var shift = 0; shift < 32; shift += 8) {
            var e = (expected >>> shift) & 0xFF;
            var a = (actual >>> shift) & 0xFF;
            assertTrue(
                    Math.abs(e - a) <= tolerance,
                    "expected " + hex(expected) + " within " + tolerance + ", got " + hex(actual));
        }
    }

    /// The sum of the channel differences.
    private static int distance(int a, int b) {
        var sum = 0;
        for (var shift = 0; shift < 24; shift += 8) {
            sum += Math.abs(((a >> shift) & 0xFF) - ((b >> shift) & 0xFF));
        }
        return sum;
    }

    private static String hex(int pixel) {
        return String.format("%08X", pixel);
    }

    private static String hexes(int[] pixels) {
        var names = new ArrayList<String>();
        for (var pixel : pixels) {
            names.add(hex(pixel));
        }
        return names.toString();
    }

    @Test
    @DisplayName("a flat picture's frame is itself consistent: a sanity check of the fixture builder")
    void fixtureBuilder() {
        var frame = flatFrame(
                PixelFormat.NV12, WIDTH, HEIGHT, new Fill(100, 50, 200), VideoFrame.ColorMatrix.BT601, false, 4);
        assertEquals(WIDTH + 4, frame.strides().get(0));
        assertEquals((byte) 100, frame.planes().get(0).get(ValueLayout.JAVA_BYTE, 0));
        assertEquals((byte) 50, frame.planes().get(1).get(ValueLayout.JAVA_BYTE, 0));
        assertEquals((byte) 200, frame.planes().get(1).get(ValueLayout.JAVA_BYTE, 1));
        assertFalse(frame.fullRange());
    }
}
