package dev.goldberry.media.container.mp3;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.container.ByteReader;
import dev.goldberry.media.io.MemoryIO;

/// ID3v2 tags written by hand, in each of the three versions, with the
/// pictures and the oddities a reader meets: syncsafe sizes, a footer,
/// unsynchronisation of the tag and of a frame, a URL where a picture should
/// be, a compressed frame.
@DisplayName("Id3v2")
class Id3v2Test {

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 4, 5, 6};

    /// A tag of `major` version with `flags`, around `frames` already laid out.
    private static byte[] tag(int major, int flags, byte[] frames) {
        var out = new ByteArrayOutputStream();
        out.writeBytes("ID3".getBytes(StandardCharsets.ISO_8859_1));
        out.write(major);
        out.write(0);
        out.write(flags);
        out.writeBytes(syncsafe(frames.length));
        out.writeBytes(frames);
        if ((flags & 0x10) != 0) {
            out.writeBytes("3DI".getBytes(StandardCharsets.ISO_8859_1));
            out.write(major);
            out.write(0);
            out.write(flags);
            out.writeBytes(syncsafe(frames.length));
        }
        return out.toByteArray();
    }

    /// One frame of version 2.3 (plain size) or 2.4 (syncsafe size).
    private static byte[] frame(int major, String id, int formatFlags, byte[] data) {
        var out = new ByteArrayOutputStream();
        out.writeBytes(id.getBytes(StandardCharsets.ISO_8859_1));
        out.writeBytes(
                major == 4
                        ? syncsafe(data.length)
                        : new byte[] {
                            (byte) (data.length >> 24),
                            (byte) (data.length >> 16),
                            (byte) (data.length >> 8),
                            (byte) data.length
                        });
        out.write(0);
        out.write(formatFlags);
        out.writeBytes(data);
        return out.toByteArray();
    }

    /// One frame of version 2.2: a three-byte id and size.
    private static byte[] frame22(String id, byte[] data) {
        var out = new ByteArrayOutputStream();
        out.writeBytes(id.getBytes(StandardCharsets.ISO_8859_1));
        out.write(data.length >> 16);
        out.write(data.length >> 8);
        out.write(data.length);
        out.writeBytes(data);
        return out.toByteArray();
    }

    /// An `APIC` body: `encoding`, the MIME type, the picture type, the
    /// description already encoded and terminated, and the picture.
    private static byte[] apic(int encoding, String mime, byte[] description, byte[] picture) {
        var out = new ByteArrayOutputStream();
        out.write(encoding);
        out.writeBytes(mime.getBytes(StandardCharsets.ISO_8859_1));
        out.write(0);
        out.write(3);
        out.writeBytes(description);
        out.writeBytes(picture);
        return out.toByteArray();
    }

    private static byte[] latin1(String text) {
        var bytes = text.getBytes(StandardCharsets.ISO_8859_1);
        var out = new byte[bytes.length + 1];
        System.arraycopy(bytes, 0, out, 0, bytes.length);
        return out;
    }

    private static byte[] syncsafe(int value) {
        return new byte[] {
            (byte) ((value >> 21) & 0x7F),
            (byte) ((value >> 14) & 0x7F),
            (byte) ((value >> 7) & 0x7F),
            (byte) (value & 0x7F)
        };
    }

    private static byte[] concat(byte[]... parts) {
        var out = new ByteArrayOutputStream();
        for (var part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

    private static ByteReader reader(byte[] bytes) {
        return new ByteReader(new MemoryIO(bytes));
    }

    @Test
    @DisplayName("2.3: a title passed over, a PNG cover with its description, the tag's end")
    void version23() {
        var frames = concat(
                frame(3, "TIT2", 0, concat(new byte[] {0}, "Tone".getBytes(StandardCharsets.ISO_8859_1))),
                frame(3, "APIC", 0, apic(0, "image/png", latin1("Cover"), PNG)));
        var bytes = concat(tag(3, 0, frames), new byte[] {(byte) 0xFF, (byte) 0xFB});
        var in = reader(bytes);
        assertTrue(Id3v2.starts(in));
        assertEquals(0, in.position(), "starts() leaves the reader where it was");
        var tag = Id3v2.read(in);
        assertEquals(10 + frames.length, tag.end());
        assertEquals(tag.end(), in.position());
        assertEquals(1, tag.pictures().size());
        var picture = tag.pictures().getFirst();
        assertEquals(CodecId.PNG, picture.codec());
        assertEquals(Optional.of("Cover"), picture.description());
        assertArrayEquals(PNG, picture.data());
        assertEquals(0xFF, in.u8(), "the frame sync follows the tag");
    }

    @Test
    @DisplayName("2.4: syncsafe frame sizes, a footer, a UTF-16 description, and a frame unsynchronised on its own")
    void version24() {
        var description = concat(
                new byte[] {(byte) 0xFF, (byte) 0xFE}, "Art".getBytes(StandardCharsets.UTF_16LE), new byte[] {0, 0});
        var jpeg = apic(1, "image/jpeg", description, JPEG);
        // Unsynchronised: every FF is followed by a 00 the reader drops.
        var unsynced = new ByteArrayOutputStream();
        for (var b : jpeg) {
            unsynced.write(b);
            if ((b & 0xFF) == 0xFF) {
                unsynced.write(0);
            }
        }
        var frames = concat(
                frame(4, "TPE1", 0, concat(new byte[] {3}, "Nobody".getBytes(StandardCharsets.UTF_8))),
                frame(4, "APIC", 0x02, unsynced.toByteArray()));
        var bytes = tag(4, 0x10, frames);
        var in = reader(bytes);
        var tag = Id3v2.read(in);
        assertEquals(bytes.length, tag.end(), "the footer is part of the tag");
        assertEquals(1, tag.pictures().size());
        var picture = tag.pictures().getFirst();
        assertEquals(CodecId.MJPEG, picture.codec());
        assertEquals(Optional.of("Art"), picture.description());
        assertArrayEquals(JPEG, picture.data());
    }

    @Test
    @DisplayName("2.3 with the whole tag unsynchronised: the picture comes back with its FF bytes")
    void unsynchronisedTag() {
        var frames = frame(3, "APIC", 0, apic(0, "image/jpeg", latin1(""), JPEG));
        var unsynced = new ByteArrayOutputStream();
        for (var b : frames) {
            unsynced.write(b);
            if ((b & 0xFF) == 0xFF) {
                unsynced.write(0);
            }
        }
        var tag = Id3v2.read(reader(tag(3, 0x80, unsynced.toByteArray())));
        assertEquals(1, tag.pictures().size());
        assertArrayEquals(JPEG, tag.pictures().getFirst().data());
        assertEquals(Optional.empty(), tag.pictures().getFirst().description());
    }

    @Test
    @DisplayName("2.2: a PIC frame with a three-letter format")
    void version22() {
        var pic = concat(
                new byte[] {0}, "PNG".getBytes(StandardCharsets.ISO_8859_1), new byte[] {3}, latin1("Front"), PNG);
        var tag = Id3v2.read(reader(tag(2, 0, frame22("PIC", pic))));
        assertEquals(1, tag.pictures().size());
        assertEquals(CodecId.PNG, tag.pictures().getFirst().codec());
        assertEquals(Optional.of("Front"), tag.pictures().getFirst().description());
        assertArrayEquals(PNG, tag.pictures().getFirst().data());
    }

    @Test
    @DisplayName("a picture of unknown MIME type is sniffed; a URL, a compressed frame and a bitmap are not pictures")
    void oddPictures() {
        var frames = concat(
                frame(3, "APIC", 0, apic(0, "image/", latin1(""), PNG)),
                frame(3, "APIC", 0, apic(0, "-->", latin1(""), "http://x/".getBytes(StandardCharsets.ISO_8859_1))),
                frame(3, "APIC", 0x80, concat(new byte[] {0, 0, 0, 9}, apic(0, "image/png", latin1(""), PNG))),
                frame(3, "APIC", 0, apic(0, "image/bmp", latin1(""), new byte[] {'B', 'M', 1, 2})));
        var tag = Id3v2.read(reader(tag(3, 0, frames)));
        assertEquals(1, tag.pictures().size());
        assertEquals(CodecId.PNG, tag.pictures().getFirst().codec());
    }

    @Test
    @DisplayName(
            "starts() says no to a frame sync and to a tag of the wrong letters; an unknown version is skipped whole")
    void startsAndSkips() {
        assertFalse(Id3v2.starts(reader(new byte[] {(byte) 0xFF, (byte) 0xFB, (byte) 0x94, 0})));
        var wrong = reader("IDX\u0003\u0000\u0000\u0000\u0000\u0000\u0000".getBytes(StandardCharsets.ISO_8859_1));
        assertFalse(Id3v2.starts(wrong));
        assertEquals(0, wrong.position());
        assertFalse(Id3v2.starts(reader(new byte[0])));

        var future = tag(5, 0, frame(3, "APIC", 0, apic(0, "image/png", latin1(""), PNG)));
        var in = reader(concat(future, new byte[] {(byte) 0xFF}));
        var tag = Id3v2.read(in);
        assertEquals(future.length, tag.end());
        assertTrue(tag.pictures().isEmpty());
        assertEquals(0xFF, in.u8());
    }
}
