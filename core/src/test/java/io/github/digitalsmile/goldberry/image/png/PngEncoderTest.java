package io.github.digitalsmile.goldberry.image.png;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.CRC32;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.image.Image;

/// The PNG writer — ADR-0283.
///
/// Two kinds of assertion here, and both are needed. The **structural** ones read
/// the bytes by hand: a signature, an `IHDR` that says what it should, chunk
/// lengths that add up and CRCs that verify. They need no native library, and they
/// are what catches the classic hand-written-encoder bug of covering the length
/// field with the CRC.
///
/// The **round trip** goes the other way: encode an image and decode it with
/// Blend2D, which is an implementation that shares no code with this one. A pair
/// of assertions that agreed only with each other would be satisfied by an
/// encoder that wrote a consistently wrong file.
class PngEncoderTest {

    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private static Image sample() {
        return Image.ofArgb(3, 2, new int[] {
            0xFFFF0000, 0xFF00FF00, 0xFF0000FF,
            0x80FFFFFF, 0x00000000, 0xFF123456,
        });
    }

    /// Walks the chunks, checking each length and CRC, and returns the type and
    /// data of each in order.
    ///
    /// A reader written for this test rather than a use of the golden harness's:
    /// what is under test is the byte layout, so the check has to be the layout
    /// spelled out.
    private static java.util.List<java.util.Map.Entry<String, byte[]>> chunks(byte[] png) {
        assertArrayEquals(SIGNATURE, java.util.Arrays.copyOf(png, 8), "the PNG signature");

        var found = new java.util.ArrayList<java.util.Map.Entry<String, byte[]>>();
        var at = new java.util.concurrent.atomic.AtomicInteger(8);
        var buffer = ByteBuffer.wrap(png).order(ByteOrder.BIG_ENDIAN);
        while (at.get() < png.length) {
            var offset = at.get();
            var length = buffer.getInt(offset);
            assertTrue(length >= 0 && offset + 12 + length <= png.length, "chunk at " + offset + " fits in the file");
            var type = new String(png, offset + 4, 4, java.nio.charset.StandardCharsets.US_ASCII);
            var data = java.util.Arrays.copyOfRange(png, offset + 8, offset + 8 + length);

            var crc = new CRC32();
            // The type and the data, and **not** the length -- which is the one
            // detail a hand-written encoder gets wrong, and is silent in every
            // viewer that does not check.
            crc.update(png, offset + 4, 4 + length);
            assertEquals((int) crc.getValue(), buffer.getInt(offset + 8 + length), "the CRC of the " + type + " chunk");

            found.add(java.util.Map.entry(type, data));
            at.set(offset + 12 + length);
        }
        return found;
    }

    @Test
    @DisplayName("the file is a signature, IHDR, one IDAT and IEND — in that order and nothing else")
    void writesTheNarrowestRealPng() {
        var chunks = chunks(PngEncoder.encode(sample()));

        assertEquals(3, chunks.size(), "three chunks, because one IDAT is all an image this size needs");
        assertEquals("IHDR", chunks.get(0).getKey());
        assertEquals("IDAT", chunks.get(1).getKey());
        assertEquals("IEND", chunks.get(2).getKey());
        assertEquals(0, chunks.get(2).getValue().length, "IEND carries nothing");
    }

    @Test
    @DisplayName("IHDR says 8-bit RGBA, not interlaced, at the image's own size")
    void writesTheHeader() {
        var header = ByteBuffer.wrap(chunks(PngEncoder.encode(sample())).get(0).getValue())
                .order(ByteOrder.BIG_ENDIAN);

        assertEquals(13, header.capacity(), "IHDR is always thirteen bytes");
        assertEquals(3, header.getInt(0), "width");
        assertEquals(2, header.getInt(4), "height");
        assertEquals(8, header.get(8), "bit depth");
        assertEquals(6, header.get(9), "colour type 6 is truecolour with alpha");
        assertEquals(0, header.get(10), "the one compression method PNG defines");
        assertEquals(0, header.get(11), "the one filter method");
        assertEquals(0, header.get(12), "not interlaced");
    }

    @Test
    @DisplayName("the pixels are RGBA and unpremultiplied, which is what a PNG means")
    void writesUnpremultipliedPixels() throws Exception {
        var idat = chunks(PngEncoder.encode(sample())).get(1).getValue();

        var inflater = new java.util.zip.Inflater();
        byte[] raw;
        try {
            inflater.setInput(idat);
            var out = new java.io.ByteArrayOutputStream();
            var chunk = new byte[256];
            while (!inflater.finished()) {
                var written = inflater.inflate(chunk);
                if (written == 0) {
                    break;
                }
                out.write(chunk, 0, written);
            }
            raw = out.toByteArray();
        } finally {
            inflater.end();
        }

        // Two rows of (filter byte + three RGBA pixels).
        assertEquals(2 * (1 + 3 * 4), raw.length);
        assertEquals(0, raw[0], "row filter: None");
        assertArrayEquals(
                new byte[] {(byte) 255, 0, 0, (byte) 255, 0, (byte) 255, 0, (byte) 255, 0, 0, (byte) 255, (byte) 255},
                java.util.Arrays.copyOfRange(raw, 1, 13),
                "red, green and blue as R,G,B,A");
        assertEquals(0, raw[13], "the second row's filter byte");
        // White at half alpha. In the buffer it is 0x80808080 and in the file it
        // must be FF FF FF 80 -- writing the buffer's bytes out would make every
        // translucent pixel visibly too dark.
        assertArrayEquals(
                new byte[] {(byte) 255, (byte) 255, (byte) 255, (byte) 128},
                java.util.Arrays.copyOfRange(raw, 14, 18),
                "half-transparent white, unpremultiplied");
    }

    @Test
    @DisplayName("what it writes, Blend2D reads back pixel for pixel")
    void roundTripsThroughAnIndependentDecoder() {
        RendererRequirement.enforce();
        var original = sample();

        var decoded = Image.decode(original.encodePng());

        assertEquals(original.size(), decoded.size());
        for (var y = 0; y < original.height(); y++) {
            for (var x = 0; x < original.width(); x++) {
                assertEquals(
                        original.argb(x, y),
                        decoded.argb(x, y),
                        "the pixel at (" + x + ", " + y + ") survived the round trip");
            }
        }
    }

    @Test
    @DisplayName("a one-pixel image is still a valid PNG")
    void encodesTheSmallestImage() {
        RendererRequirement.enforce();
        var one = Image.ofArgb(1, 1, new int[] {0xFF123456});

        var decoded = Image.decode(one.encodePng());
        assertEquals(0xFF123456, decoded.argb(0, 0));
    }

    @Test
    @DisplayName("nothing is encoded from nothing")
    void refusesNull() {
        assertThrows(NullPointerException.class, () -> PngEncoder.encode(null));
    }
}
