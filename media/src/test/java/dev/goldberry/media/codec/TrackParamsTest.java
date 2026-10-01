package dev.goldberry.media.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TrackParams")
class TrackParamsTest {

    private static final TrackParams.Video VIDEO = new TrackParams.Video(
            1920, 1080, Optional.of("yuv420p"), OptionalInt.of(0), OptionalInt.empty(), OptionalLong.empty());

    private static final TrackParams.Audio AUDIO =
            new TrackParams.Audio(48_000, 2, Optional.of("fltp"), OptionalInt.empty(), OptionalLong.of(128_000));

    /// The switch the interface's documentation shows. It compiles only while it
    /// covers every kind, which is the point of the interface being sealed.
    private static String describe(TrackParams params) {
        return switch (params) {
            case TrackParams.Video v -> v.width() + "×" + v.height();
            case TrackParams.Audio a -> a.sampleRate() + " Hz, " + a.channels() + " ch";
            case TrackParams.Subtitle _ -> "subtitles";
            case TrackParams.Other o -> o.type().name().toLowerCase(Locale.ROOT);
        };
    }

    @Test
    @DisplayName("each kind reports its own media type")
    void types() {
        assertEquals(MediaType.VIDEO, VIDEO.type());
        assertEquals(MediaType.AUDIO, AUDIO.type());
        assertEquals(MediaType.SUBTITLE, new TrackParams.Subtitle().type());
        assertEquals(MediaType.ATTACHMENT, new TrackParams.Other(MediaType.ATTACHMENT).type());
    }

    @Test
    @DisplayName("is switched over exhaustively")
    void exhaustive() {
        assertEquals("1920×1080", describe(VIDEO));
        assertEquals("48000 Hz, 2 ch", describe(AUDIO));
        assertEquals("subtitles", describe(new TrackParams.Subtitle()));
        assertEquals("data", describe(new TrackParams.Other(MediaType.DATA)));
    }

    @Test
    @DisplayName("refuses negative sizes and rates, and an Other that is a playable kind")
    void refuses() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TrackParams.Video(
                        -1, 1, Optional.empty(), OptionalInt.empty(), OptionalInt.empty(), OptionalLong.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TrackParams.Audio(-1, 2, Optional.empty(), OptionalInt.empty(), OptionalLong.empty()));
        assertThrows(IllegalArgumentException.class, () -> new TrackParams.Other(MediaType.AUDIO));
    }
}
