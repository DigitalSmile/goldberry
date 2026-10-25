package dev.goldberry.media.bitstream.vp9;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Superframe")
class SuperframeTest {

    /// `sizes` frames of zeros, then an index of `bytes`-byte sizes.
    private static byte[] packet(int bytes, int... sizes) {
        var total = Arrays.stream(sizes).sum();
        var marker = 0xC0 | ((bytes - 1) << 3) | (sizes.length - 1);
        var packet = new byte[total + 2 + bytes * sizes.length];
        var at = total;
        packet[at++] = (byte) marker;
        for (var size : sizes) {
            for (var b = 0; b < bytes; b++) {
                packet[at++] = (byte) (size >>> (8 * b));
            }
        }
        packet[at] = (byte) marker;
        return packet;
    }

    @Test
    @DisplayName("splits a packet into the frames its index lists, sizes little-endian")
    void split() {
        var packet = packet(2, 300, 5);
        var superframe = Superframe.parse(packet).orElseThrow();
        assertEquals(2, superframe.bytesPerFrameSize());
        assertEquals(6, superframe.indexSize());
        assertEquals(List.of(new Superframe.Frame(0, 300), new Superframe.Frame(300, 5)), superframe.frames());
        assertEquals(superframe.frames(), Superframe.frames(packet));

        var wide = Superframe.parse(packet(3, 0x01_02_03)).orElseThrow();
        assertEquals(List.of(new Superframe.Frame(0, 0x01_02_03)), wide.frames());
    }

    @Test
    @DisplayName("eight frames, the most an index lists, with one-byte sizes")
    void eightFrames() {
        var frames = Superframe.frames(packet(1, 1, 2, 3, 4, 5, 6, 7, 8));
        assertEquals(Superframe.MAX_FRAMES, frames.size());
        assertEquals(new Superframe.Frame(28, 8), frames.getLast());
    }

    @Test
    @DisplayName("a packet without an index, or whose index does not open with its marker, is one frame")
    void noIndex() {
        var plain = new byte[] {(byte) 0x82, 0x49, (byte) 0x83, 0x42, 0x00};
        assertTrue(Superframe.parse(plain).isEmpty());
        assertEquals(List.of(new Superframe.Frame(0, 5)), Superframe.frames(plain));

        var mismatched = packet(1, 3, 4);
        mismatched[7] = (byte) 0xC2; // the opening marker, changed
        assertTrue(Superframe.parse(mismatched).isEmpty());
        assertEquals(List.of(new Superframe.Frame(0, mismatched.length)), Superframe.frames(mismatched));

        assertTrue(Superframe.parse(new byte[0]).isEmpty());
        assertTrue(Superframe.parse(new byte[] {(byte) 0xC1}).isEmpty(), "an index longer than the packet");
    }

    @Test
    @DisplayName("an index whose frames run past it is IllegalArgumentException")
    void overrun() {
        var packet = packet(1, 3, 4);
        packet[packet.length - 2] = 5; // the second frame, a byte longer
        assertThrows(IllegalArgumentException.class, () -> Superframe.parse(packet));
        assertThrows(IllegalArgumentException.class, () -> Superframe.frames(packet));
    }

    @Test
    @DisplayName("padding between the last frame and the index is left out of the frames")
    void padding() {
        var index = packet(1, 3, 4);
        var padded = new byte[index.length + 2];
        System.arraycopy(index, 0, padded, 0, 7);
        System.arraycopy(index, 7, padded, 9, index.length - 7);
        assertEquals(List.of(new Superframe.Frame(0, 3), new Superframe.Frame(3, 4)), Superframe.frames(padded));
    }
}
