package dev.goldberry.media.ffmpegcli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import dev.goldberry.media.HardwareDecoding;
import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.codec.MediaType;
import dev.goldberry.media.codec.PixelFormat;
import dev.goldberry.media.codec.Rational;
import dev.goldberry.media.codec.TrackParams;
import dev.goldberry.media.platform.fixtures.Fixtures;

/// What the provider claims, what it runs, and how it fails: with no program,
/// with one that is not `ffmpeg`, and with one that dies part of the way through
/// a track.
@DisplayName("FfmpegCliProvider")
@Timeout(60)
class FfmpegCliProviderTest {

    private static final TrackParams VIDEO = new TrackParams.Video(
            160, 90, Optional.empty(), OptionalInt.empty(), OptionalInt.empty(), OptionalLong.empty());
    private static final TrackParams AUDIO =
            new TrackParams.Audio(48_000, 2, Optional.empty(), OptionalInt.empty(), OptionalLong.empty());

    private static DecoderRequest request(CodecId codec, String name, TrackParams params) {
        return new DecoderRequest(codec, name, params, MemorySegment.NULL, new Rational(1, 1_000));
    }

    @Test
    @DisplayName("without -Dgoldberry.media.ffmpeg, the provider ServiceLoader makes supports nothing")
    void offByDefault() {
        Assumptions.assumeTrue(System.getProperty(FfmpegCliProvider.PROPERTY) == null);
        var provider = new FfmpegCliProvider();
        assertEquals(Optional.empty(), provider.ffmpeg());
        assertFalse(provider.available());
        assertFalse(provider.supports(request(CodecId.H264, "h264", VIDEO)));
        assertThrows(IllegalStateException.class, () -> provider.open(request(CodecId.H264, "h264", VIDEO)));
        assertEquals("ffmpeg-cli", provider.name());
        assertTrue(provider.priority() < 0, "below the system decoders");
    }

    @Test
    @DisplayName("a path with no program, or a program that is not ffmpeg, supports nothing")
    void noProgram(@TempDir Path directory) throws IOException {
        var missing = FfmpegCliProvider.of(directory.resolve("ffmpeg"));
        assertFalse(missing.available());
        assertFalse(missing.supports(request(CodecId.H264, "h264", VIDEO)));

        var impostor = script(directory, "impostor", "echo 'hello'\nexit 0\n");
        assertFalse(FfmpegCliProvider.of(impostor).available());
        assertFalse(FfmpegCliProvider.of(impostor).supports(request(CodecId.AAC, "aac", AUDIO)));
    }

    @Test
    @DisplayName("claims the patent-pool codecs ffmpeg decodes, and never a royalty-free one")
    void claims() {
        var provider = FfmpegCliProvider.of(FfmpegRequirement.ffmpeg());
        assertTrue(provider.supports(request(CodecId.H264, "h264", VIDEO)));
        assertTrue(provider.supports(request(CodecId.HEVC, "hevc", VIDEO)));
        assertTrue(provider.supports(request(CodecId.AAC, "aac", AUDIO)));
        assertTrue(provider.supports(request(CodecId.AC3, "ac3", AUDIO)));
        assertTrue(provider.supports(request(CodecId.H264, "h264", VIDEO).withHardware(HardwareDecoding.OFF)));
        // A Matroska track this module names only by its CodecID.
        assertTrue(provider.supports(request(CodecId.UNKNOWN, "A_DTS", AUDIO)));

        // The built-in decoders' codecs, ffmpeg decodes as well: not claimed.
        assertFalse(provider.supports(request(CodecId.VP9, "vp9", VIDEO)));
        assertFalse(provider.supports(request(CodecId.AV1, "av1", VIDEO)));
        assertFalse(provider.supports(request(CodecId.OPUS, "opus", AUDIO)));
        assertFalse(provider.supports(request(CodecId.MP3, "mp3", AUDIO)));
        // Not a track it plays, or one it cannot name to ffmpeg.
        assertFalse(provider.supports(request(CodecId.MOV_TEXT, "mov_text", new TrackParams.Subtitle())));
        assertFalse(provider.supports(request(CodecId.UNKNOWN, "V_MS/VFW/FOURCC", VIDEO)));
        assertFalse(provider.supports(request(CodecId.UNKNOWN, "dts", AUDIO)));
        assertFalse(provider.supports(request(CodecId.UNKNOWN, "A_DTS", new TrackParams.Other(MediaType.DATA))));
    }

    @Test
    @DisplayName("the command line: Matroska both ways, -hwaccel only where hardware is allowed")
    void commandLine() {
        var binary = FfmpegBinary.of(FfmpegRequirement.ffmpeg()).orElseThrow();
        var hardware = FfmpegCliDecoder.command(binary, PixelFormat.NV12, true);
        assertTrue(hardware.contains("-hwaccel"), hardware::toString);
        assertEquals("nv12", hardware.get(hardware.indexOf("-pix_fmt") + 1));
        assertEquals("pipe:0", hardware.get(hardware.indexOf("-i") + 1));
        assertEquals("pipe:1", hardware.getLast());
        var software = FfmpegCliDecoder.command(binary, PixelFormat.P010, false);
        assertFalse(software.contains("-hwaccel"), software::toString);
        assertEquals("p010le", software.get(software.indexOf("-pix_fmt") + 1));
        var sound = FfmpegCliDecoder.command(binary, null, true);
        assertFalse(sound.contains("-hwaccel"), sound::toString);
        assertEquals("pcm_f32le", sound.get(sound.indexOf("-c:a") + 1));
    }

    @Test
    @DisplayName("the codec listing: the names flagged D after the legend's rule")
    void listing() {
        var listing = """
                Codecs:
                 D..... = Decoding supported
                 .E.... = Encoding supported
                 -------
                 DEV.LS h264                 H.264 / AVC (decoders: h264 h264_cuvid)
                 .EVIL. a64_multi            Multicolor charset for Commodore 64
                 DEA.L. aac                  AAC (Advanced Audio Coding)
                """;
        assertEquals(Set.of("h264", "aac"), FfmpegBinary.decoded(listing));
    }

    @Test
    @DisplayName("a program that dies part of the way through fails the decoder, with its status and its words")
    void dies(@TempDir Path directory) throws IOException {
        var ffmpeg = FfmpegRequirement.ffmpeg();
        var dying = script(directory, "dying-ffmpeg", """
                case " $* " in *" -codecs "*) exec '%s' "$@" ;; esac
                head -c 3000 > /dev/null
                echo 'simulated failure' >&2
                exit 3
                """.formatted(ffmpeg));
        var provider = FfmpegCliProvider.of(dying);
        assertTrue(provider.available());
        var failure = assertThrows(
                FfmpegCliException.class,
                () -> Fixtures.decode("clip-h264-high.mp4", MediaType.VIDEO, provider::open, _ -> {}));
        assertTrue(failure.getMessage().contains("status 3"), failure.getMessage());
        assertTrue(failure.getMessage().contains("simulated failure"), failure.getMessage());
    }

    private static Path script(Path directory, String name, String body) throws IOException {
        Assumptions.assumeFalse(
                System.getProperty("os.name", "").startsWith("Windows"), "a shell script stands in for ffmpeg");
        var script = directory.resolve(name);
        Files.writeString(script, "#!/bin/sh\n" + body);
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwx------"));
        return script;
    }
}
