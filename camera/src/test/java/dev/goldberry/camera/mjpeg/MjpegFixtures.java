package dev.goldberry.camera.mjpeg;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/// The Motion JPEG frames the tests decode, written by
/// `tools/media/mjpeg_fixtures.py` with Pillow (libjpeg), an encoder other
/// than the code under test: a 64×48 test card of seven bars over a grey
/// ramp, baseline 4:2:2, with JPEG's standard Huffman tables.
public final class MjpegFixtures {

    /// The card as encoded, its DHT segments in.
    public static final String CARD = "card.jpg";

    /// The same bytes, every DHT segment cut out.
    public static final String CARD_WITHOUT_TABLES = "card-no-huffman.jpg";

    /// The card at 32×24.
    public static final String SMALL_CARD = "card-32x24.jpg";

    private MjpegFixtures() {}

    /// The bytes of fixture `name`.
    public static byte[] bytes(String name) {
        try (var in =
                Objects.requireNonNull(MjpegFixtures.class.getResourceAsStream(name), () -> "no fixture " + name)) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /// The same, in a read-only direct buffer, as SDL lends a frame.
    public static ByteBuffer lent(String name) {
        var bytes = bytes(name);
        return ByteBuffer.allocateDirect(bytes.length).put(0, bytes).asReadOnlyBuffer();
    }

    /// The Huffman tables `jpeg`'s DHT segments define before its scan, by
    /// their class-and-id byte: the 16 code counts and then the symbols.
    static Map<Integer, ByteBuffer> huffmanTables(ByteBuffer jpeg) {
        var tables = new LinkedHashMap<Integer, ByteBuffer>();
        var at = 2;
        while (at + 4 <= jpeg.limit()) {
            var marker = jpeg.get(at + 1) & 0xFF;
            if (marker == 0xDA) {
                break;
            }
            var length = ((jpeg.get(at + 2) & 0xFF) << 8) | (jpeg.get(at + 3) & 0xFF);
            if (marker == 0xC4) {
                var table = at + 4;
                while (table < at + 2 + length) {
                    var counts = 0;
                    for (var bits = 0; bits < 16; bits++) {
                        counts += jpeg.get(table + 1 + bits) & 0xFF;
                    }
                    tables.put(jpeg.get(table) & 0xFF, jpeg.slice(table + 1, 16 + counts));
                    table += 1 + 16 + counts;
                }
            }
            at += 2 + length;
        }
        return tables;
    }
}
