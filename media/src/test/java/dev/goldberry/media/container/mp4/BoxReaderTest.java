package dev.goldberry.media.container.mp4;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.media.MediaError;
import dev.goldberry.media.MediaException;
import dev.goldberry.media.container.ByteReader;
import dev.goldberry.media.io.MemoryIO;

/// The three ways a box states its size, the `uuid` header, and the full
/// box's first four bytes, over bytes made by hand.
@DisplayName("BoxReader")
class BoxReaderTest {

    private static byte[] box(String type, byte[] payload) {
        var out = ByteBuffer.allocate(8 + payload.length);
        out.putInt(8 + payload.length)
                .put(type.getBytes(StandardCharsets.US_ASCII))
                .put(payload);
        return out.array();
    }

    private static byte[] largeBox(String type, byte[] payload) {
        var out = ByteBuffer.allocate(16 + payload.length);
        out.putInt(1)
                .put(type.getBytes(StandardCharsets.US_ASCII))
                .putLong(16 + payload.length)
                .put(payload);
        return out.array();
    }

    private static byte[] concat(byte[]... parts) {
        var out = new ByteArrayOutputStream();
        for (var part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

    private static BoxReader reader(byte[] bytes, boolean seekable) {
        var io = new MemoryIO(bytes);
        io.seekable = seekable;
        return new BoxReader(new ByteReader(io));
    }

    @Test
    @DisplayName("a 32-bit size, a 64-bit largesize, and a size of 0 that runs to the end")
    void sizes() {
        var bytes = concat(box("free", new byte[4]), largeBox("wide", new byte[3]), new byte[] {
            0, 0, 0, 0, 'm', 'd', 'a', 't', 1, 2, 3
        });
        var boxes = reader(bytes, true);
        var free = boxes.next(Long.MAX_VALUE);
        assertEquals(BoxTypes.FREE, free.type());
        assertEquals(0, free.headerStart());
        assertEquals(8, free.dataStart());
        assertEquals(12, free.end());
        assertEquals(4, free.size());
        boxes.skip(free);
        var wide = boxes.next(Long.MAX_VALUE);
        assertEquals("wide", wide.name());
        assertEquals(28, wide.dataStart());
        assertEquals(31, wide.end());
        boxes.skip(wide);
        var mdat = boxes.next(Long.MAX_VALUE);
        assertEquals(BoxTypes.MDAT, mdat.type());
        assertEquals(39, mdat.dataStart());
        assertEquals(bytes.length, mdat.end());
        assertEquals(3, mdat.size());
        boxes.skip(mdat);
        assertFalse(boxes.hasNext(Long.MAX_VALUE));
    }

    @Test
    @DisplayName("a size of 0 on a stream of unknown length runs to its end, and skipping it reads it out")
    void toTheEndUnknown() {
        var bytes = concat(box("ftyp", new byte[4]), new byte[] {0, 0, 0, 0, 'm', 'd', 'a', 't', 1, 2, 3});
        var io = new MemoryIO(bytes);
        io.seekable = false;
        io.sizeKnown = false;
        var in = new ByteReader(io);
        var boxes = new BoxReader(in);
        boxes.skip(boxes.next(Long.MAX_VALUE));
        var mdat = boxes.next(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, mdat.end());
        boxes.skip(mdat);
        assertTrue(in.atEnd());
    }

    @Test
    @DisplayName("a uuid box's sixteen-byte type is header, so its payload starts after it")
    void uuid() {
        var payload = new byte[16 + 2];
        payload[16] = 7;
        var boxes = reader(box("uuid", payload), true);
        var uuid = boxes.next(Long.MAX_VALUE);
        assertEquals(BoxTypes.UUID, uuid.type());
        assertEquals(24, uuid.dataStart());
        assertEquals(26, uuid.end());
        assertEquals(7, boxes.in().u8());
    }

    @Test
    @DisplayName("a full box's version and flags")
    void fullBox() {
        var boxes = reader(box("elst", new byte[] {1, 0, 0, 3}), true);
        boxes.next(Long.MAX_VALUE);
        var full = boxes.fullBox();
        assertEquals(1, full.version());
        assertEquals(3, full.flags());
    }

    @Test
    @DisplayName("a box smaller than its header, or past its parent, is invalid data")
    void invalid() {
        var small = reader(new byte[] {0, 0, 0, 4, 'f', 'r', 'e', 'e'}, true);
        var e = assertThrows(MediaException.class, () -> small.next(Long.MAX_VALUE));
        assertInstanceOf(MediaError.InvalidData.class, e.error());

        var past = reader(box("free", new byte[4]), true);
        assertThrows(MediaException.class, () -> past.next(10));
    }

    @Test
    @DisplayName("seekTo goes forward on a stream that cannot seek, and backward on one that can")
    void seeksTo() {
        var bytes = concat(box("free", new byte[4]), box("skip", new byte[4]));
        var forward = reader(bytes, false);
        forward.seekTo(12);
        assertEquals(BoxTypes.SKIP, forward.next(Long.MAX_VALUE).type());

        var back = reader(bytes, true);
        back.skip(back.next(Long.MAX_VALUE));
        back.skip(back.next(Long.MAX_VALUE));
        back.seekTo(0);
        assertEquals(BoxTypes.FREE, back.next(Long.MAX_VALUE).type());
    }
}
