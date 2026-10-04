package dev.goldberry.media.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.foreign.MemorySegment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.media.codec.Frame;
import dev.goldberry.media.codec.Packet;
import dev.goldberry.media.codec.Rational;

/// How long a pass over a looping source is, from its packets alone.
@DisplayName("PassLength")
class PassLengthTest {

    private static final Rational MS = new Rational(1, 1000);
    private static final long MILLI = 1_000_000L;

    private static Packet at(long ptsMillis, long millis) {
        return Packet.of(MemorySegment.NULL, 0, ptsMillis, ptsMillis, millis, true, MS);
    }

    @Test
    @DisplayName("says nothing before a packet with a time")
    void nothingYet() {
        var length = new PassLength();
        assertEquals(Frame.NO_PTS, length.nanos());
        length.video(Packet.of(MemorySegment.NULL, 0, Packet.NO_TIMESTAMP, Packet.NO_TIMESTAMP, 40, true, MS));
        assertEquals(Frame.NO_PTS, length.nanos());
    }

    @Test
    @DisplayName("is the end of the last picture, whatever the sound's packets say")
    void thePictureSetsIt() {
        var length = new PassLength();
        for (var pts = 0; pts < 1000; pts += 40) {
            length.video(at(pts, 40));
        }
        // An Opus track's last packet runs past the picture with the encoder's
        // padding, which is not played.
        length.audio(at(994, 20));
        assertEquals(1000 * MILLI, length.nanos());
    }

    @Test
    @DisplayName("with no picture, is the end of the last sound")
    void theSoundWhenAlone() {
        var length = new PassLength();
        length.audio(at(974, 20));
        length.audio(at(-7, 20));
        assertEquals(994 * MILLI, length.nanos());
    }

    @Test
    @DisplayName("a picture that does not say how long it lasts lasts the spacing of the pictures, out of order or not")
    void spacingStandsIn() {
        var length = new PassLength();
        // Decoding order with B-frames: 0, 120, 40, 80; no durations.
        for (var pts : new long[] {0, 120, 40, 80}) {
            length.video(at(pts, 0));
        }
        assertEquals(160 * MILLI, length.nanos());
    }
}
