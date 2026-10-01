package dev.goldberry.media.ffi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/// Naming a container from its first bytes, with no FFmpeg (ADR-0471).
@DisplayName("ContainerSniffer")
class ContainerSnifferTest {

    /// The demuxers of the published build: what is *not* reported.
    private static final Set<String> BUILT =
            Set.of("matroska", "webm", "mov", "mp4", "avi", "ogg", "flac", "mp3", "wav");

    /// `prefix`, then zeros to `length`.
    private static byte[] head(int length, int... prefix) {
        var head = new byte[length];
        for (var i = 0; i < prefix.length; i++) {
            head[i] = (byte) prefix[i];
        }
        return head;
    }

    private static byte[] ascii(String text, int length) {
        return Arrays.copyOf(text.getBytes(StandardCharsets.ISO_8859_1), length);
    }

    private static Optional<String> name(byte[] head) {
        return ContainerSniffer.identify(head, BUILT).map(ContainerSniffer.Signature::name);
    }

    /// Magic strings as they are, trailing spaces and control bytes included,
    /// which a CSV source would trim.
    static Stream<Arguments> magics() {
        return Stream.of(
                Arguments.of("FLV\u0001", "FLV"),
                Arguments.of(".RMF", "RealMedia"),
                Arguments.of("DKIF", "IVF"),
                Arguments.of("caff", "Core Audio Format"),
                Arguments.of("#!AMR\n", "AMR"),
                Arguments.of("wvpk", "WavPack"),
                Arguments.of("MAC ", "Monkey's Audio"),
                Arguments.of("MPCK", "Musepack"),
                Arguments.of("MP+", "Musepack"),
                Arguments.of("DSD ", "DSD (DSF)"));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("magics")
    @DisplayName("names a container by its magic")
    void magic(String prefix, String expected) {
        assertEquals(Optional.of(expected), name(ascii(prefix, 64)));
    }

    @Test
    @DisplayName("names a transport stream by three sync bytes a packet apart, and M2TS by its offset ones")
    void transportStreams() {
        var ts = new byte[600];
        ts[0] = ts[188] = ts[376] = 0x47;
        assertEquals(Optional.of("MPEG-TS"), name(ts));
        var m2ts = new byte[600];
        m2ts[4] = m2ts[196] = m2ts[388] = 0x47;
        assertEquals(Optional.of("MPEG-TS (M2TS)"), name(m2ts));
        var once = new byte[600];
        once[0] = 0x47;
        assertEquals(Optional.empty(), name(once), "one sync byte is not a stream");
    }

    @Test
    @DisplayName("names ASF, MPEG program and elementary streams, raw H.264, MXF, ADTS and AC-3 by their bytes")
    void binarySignatures() {
        assertEquals(Optional.of("ASF (WMV, WMA)"), name(head(64, 0x30, 0x26, 0xB2, 0x75, 0x8E, 0x66, 0xCF, 0x11)));
        assertEquals(Optional.of("MPEG program stream (MPG, VOB)"), name(head(64, 0, 0, 1, 0xBA)));
        assertEquals(Optional.of("MPEG-1/2 video"), name(head(64, 0, 0, 1, 0xB3)));
        assertEquals(Optional.of("raw H.264"), name(head(64, 0, 0, 0, 1, 0x67)));
        assertEquals(Optional.of("MXF"), name(head(64, 0x06, 0x0E, 0x2B, 0x34)));
        assertEquals(Optional.of("AAC (ADTS)"), name(head(64, 0xFF, 0xF1)));
        assertEquals(Optional.of("AC-3"), name(head(64, 0x0B, 0x77)));
        assertEquals(Optional.of("AIFF"), name(ascii("FORM\0\0\0\0AIFF", 64)));
    }

    @Test
    @DisplayName("says nothing of a container the build demuxes: that one is damaged, not unsupported")
    void builtIsNotReported() {
        var avi = ascii("RIFF\0\0\0\0AVI LIST", 64);
        assertEquals(Optional.empty(), name(avi));
        assertEquals(
                Optional.of("AVI"),
                ContainerSniffer.identify(avi, Set.of("matroska")).map(ContainerSniffer.Signature::name),
                "a build without the AVI demuxer names it");
    }

    @Test
    @DisplayName("an MP3 frame is not ADTS, and bytes with no signature, or none at all, are nothing")
    void nothing() {
        assertEquals(Optional.empty(), ContainerSniffer.identify(head(64, 0xFF, 0xFB), Set.of()));
        assertEquals(Optional.empty(), name("These are notes about music.".getBytes(StandardCharsets.US_ASCII)));
        assertEquals(Optional.empty(), name(new byte[0]));
        assertEquals(Optional.empty(), name(new byte[] {0x47}));
    }

    @Test
    @DisplayName("every signature names a demuxer, and keeps within the bytes it is given")
    void everySignatureIsBounded() {
        for (var signature : ContainerSniffer.SIGNATURES) {
            assertTrue(!signature.demuxer().isBlank(), signature.name());
            for (var length = 0; length < 8; length++) {
                signature.matches().test(new byte[length]);
            }
        }
    }
}
