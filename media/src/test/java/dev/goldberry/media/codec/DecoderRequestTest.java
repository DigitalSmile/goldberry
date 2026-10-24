package dev.goldberry.media.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.MemorySegment;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.media.HardwareDecoding;

/// What a provider is asked: the track, and what the player allows of the
/// video engine.
@DisplayName("DecoderRequest")
class DecoderRequestTest {

    private static DecoderRequest h264() {
        return new DecoderRequest(
                CodecId.H264,
                "h264",
                new TrackParams.Video(
                        1280, 720, Optional.empty(), OptionalInt.empty(), OptionalInt.empty(), OptionalLong.empty()),
                MemorySegment.NULL,
                Rational.MICROSECONDS);
    }

    @Test
    @DisplayName("a container's request leaves the video engine to the provider")
    void autoByDefault() {
        var request = h264();
        assertEquals(HardwareDecoding.AUTO, request.hardware());
        assertFalse(request.softwareOnly());
    }

    @Test
    @DisplayName("withHardware changes that and nothing else")
    void withHardware() {
        var request = h264();
        var off = request.withHardware(HardwareDecoding.OFF);
        assertTrue(off.softwareOnly());
        assertEquals(request, off.withHardware(HardwareDecoding.AUTO));
        assertEquals(request.params(), off.params());
        assertThrows(NullPointerException.class, () -> request.withHardware(null));
    }
}
