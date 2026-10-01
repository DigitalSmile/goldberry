package dev.goldberry.media.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("CodecId")
class CodecIdTest {

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = CodecId.class, mode = EnumSource.Mode.EXCLUDE, names = "UNKNOWN")
    @DisplayName("maps back from its own FFmpeg name")
    void roundTrip(CodecId codec) {
        assertEquals(codec, CodecId.fromFfmpegName(codec.ffmpegName()));
    }

    @Test
    @DisplayName("gives every codec its own name")
    void unique() {
        var names = Arrays.stream(CodecId.values()).map(CodecId::ffmpegName).toList();
        assertEquals(names.size(), new HashSet<>(names).size());
    }

    @Test
    @DisplayName("a name it does not know is UNKNOWN, including the empty one")
    void unknown() {
        assertEquals(CodecId.UNKNOWN, CodecId.fromFfmpegName("prores"));
        assertEquals(CodecId.UNKNOWN, CodecId.fromFfmpegName(""));
    }

    @Test
    @DisplayName("marks the patent-pool codecs as outside the policy, and the free ones inside")
    void policy() {
        for (var patented : new CodecId[] {CodecId.H264, CodecId.HEVC, CodecId.AAC, CodecId.AC3, CodecId.EAC3}) {
            assertFalse(patented.royaltyFree(), patented.name());
        }
        for (var free : new CodecId[] {CodecId.VP9, CodecId.AV1, CodecId.OPUS, CodecId.FLAC, CodecId.MP3}) {
            assertTrue(free.royaltyFree(), free.name());
        }
    }

    @Test
    @DisplayName("knows which kind of stream a codec carries")
    void type() {
        assertEquals(MediaType.VIDEO, CodecId.AV1.type());
        assertEquals(MediaType.AUDIO, CodecId.OPUS.type());
        assertEquals(MediaType.SUBTITLE, CodecId.WEBVTT.type());
    }
}
