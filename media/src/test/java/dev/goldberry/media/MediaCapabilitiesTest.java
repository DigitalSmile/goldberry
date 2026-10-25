package dev.goldberry.media;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// [MediaCapabilities]: what it answers of the containers read in Java, which
/// needs no natives, and that it keeps no caller's collections.
@DisplayName("MediaCapabilities")
class MediaCapabilitiesTest {

    @Test
    @DisplayName("names the containers read in Java, with or without the natives, and not AVI")
    void demuxers() {
        var current = MediaCapabilities.current();
        for (var format : List.of("matroska", "webm", "mp4", "ogg", "mp3")) {
            assertTrue(current.canDemux(format), format);
        }
        assertFalse(current.canDemux("avi"));
        assertFalse(current.canDemux("ffmpeg"));
    }

    @Test
    @DisplayName("answers from its own copies, which cannot be changed")
    void copies() {
        var demuxers = new HashSet<>(Set.of("webm"));
        var providers = new ArrayList<>(List.of("dav1d"));
        var capabilities = new MediaCapabilities(Set.of(), demuxers, providers);
        demuxers.add("mp4");
        assertTrue(capabilities.canDemux("webm"));
        assertFalse(capabilities.canDemux("mp4"));
        assertThrows(
                UnsupportedOperationException.class,
                () -> capabilities.demuxers().add("ogg"));
        assertThrows(
                UnsupportedOperationException.class,
                () -> capabilities.providers().add("vorbis"));
    }
}
