package dev.goldberry.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MediaError")
class MediaErrorTest {

    /// The switch the documentation shows, over every kind. It stops compiling when
    /// a kind is added without a case, which is the point of the interface being
    /// sealed.
    private static String kind(MediaError error) {
        return switch (error) {
            case MediaError.NativesUnavailable _ -> "natives";
            case MediaError.UnsupportedScheme(var scheme) -> "scheme " + scheme;
            case MediaError.UnsupportedCodec(var codecs) -> "codec " + String.join(",", codecs);
            case MediaError.UnsupportedContainer(var format) -> "container " + format;
            case MediaError.InvalidData _ -> "data";
            case MediaError.Io _ -> "io";
            case MediaError.Aborted _ -> "aborted";
        };
    }

    @Test
    @DisplayName("names what it is about in its message")
    void messages() {
        assertTrue(new MediaError.UnsupportedCodec(List.of("h264", "aac"))
                .message()
                .contains("h264, aac"));
        assertTrue(new MediaError.UnsupportedScheme("s3").message().contains("s3:"));
        assertTrue(new MediaError.NativesUnavailable("no jar").message().contains("no jar"));
        assertEquals("aborted", new MediaError.Aborted().message());
        assertEquals("no demuxer for MPEG-TS in this build", new MediaError.UnsupportedContainer("MPEG-TS").message());
    }

    @Test
    @DisplayName("is switched over exhaustively")
    void exhaustive() {
        assertEquals("scheme s3", kind(new MediaError.UnsupportedScheme("s3")));
        assertEquals("codec h264", kind(new MediaError.UnsupportedCodec(List.of("h264"))));
        assertEquals("io", kind(new MediaError.Io("reset")));
        assertEquals("container FLV", kind(new MediaError.UnsupportedContainer("FLV")));
    }

    @Test
    @DisplayName("an unsupported codec names at least one, and keeps a copy of the list")
    void unsupportedCodec() {
        assertThrows(IllegalArgumentException.class, () -> new MediaError.UnsupportedCodec(List.of()));
        var codecs = new ArrayList<>(List.of("h264"));
        var error = new MediaError.UnsupportedCodec(codecs);
        codecs.add("aac");
        assertEquals(List.of("h264"), error.codecs());
    }

    @Test
    @DisplayName("the exception carries the error, its message and the cause")
    void exception() {
        var cause = new IOException("reset");
        var error = new MediaError.Io("reset");
        var exception = new MediaException(error, cause);
        assertSame(error, exception.error());
        assertSame(cause, exception.getCause());
        assertEquals(error.message(), exception.getMessage());
    }
}
