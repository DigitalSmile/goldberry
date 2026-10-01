package dev.goldberry.media.ffi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.media.FfmpegRequirement;

/// The loaded libraries themselves: the checks [FfmpegLibraries] made at load,
/// asserted again where a failure names what is wrong, and the few functions phase
/// 1 binds beyond the probe.
@DisplayName("FFmpeg, loaded")
class FfmpegNativeTest {

    @BeforeEach
    void requireFfmpeg() {
        FfmpegRequirement.enforce();
    }

    @Test
    @DisplayName("the packaged layout file agrees with FfmpegStructs on this target")
    void packagedLayout() throws IOException {
        var ffmpeg = FfmpegLibraries.get();
        try (var reader =
                Files.newBufferedReader(ffmpeg.directory().resolve(FfmpegLayout.FILE_NAME), StandardCharsets.UTF_8)) {
            assertEquals(List.of(), FfmpegLayoutCheck.verify(FfmpegLayout.parse(reader), FfmpegStructs.ALL));
        }
    }

    @Test
    @DisplayName("every library is the pinned major")
    void majors() {
        var ffmpeg = FfmpegLibraries.get();
        assertEquals(
                FfmpegLibrary.AVUTIL.pinnedMajor(),
                FfmpegLibrary.majorOf(ffmpeg.util().version().call()));
        assertEquals(
                FfmpegLibrary.AVCODEC.pinnedMajor(),
                FfmpegLibrary.majorOf(ffmpeg.codec().version().call()));
        assertEquals(
                FfmpegLibrary.AVFORMAT.pinnedMajor(),
                FfmpegLibrary.majorOf(ffmpeg.format().version().call()));
        assertEquals(
                FfmpegLibrary.SWSCALE.pinnedMajor(),
                FfmpegLibrary.majorOf(ffmpeg.swScale().version().call()));
        assertEquals(
                FfmpegLibrary.SWRESAMPLE.pinnedMajor(),
                FfmpegLibrary.majorOf(ffmpeg.swResample().version().call()));
    }

    @Test
    @DisplayName("names formats and errors in FFmpeg's words")
    void names() {
        var ffmpeg = FfmpegLibraries.get();
        assertEquals(Optional.of("nv12"), ffmpeg.pixelFormatName(23));
        assertEquals(Optional.empty(), ffmpeg.pixelFormatName(-1));
        assertEquals(Optional.of("flt"), ffmpeg.sampleFormatName(3));
        assertTrue(
                ffmpeg.describe(ffmpeg.constants().averrorEof()).toLowerCase().contains("end of file"));
    }

    @Test
    @DisplayName("names a codec this build cannot decode, which is how UNSUPPORTED_CODEC can say h264")
    void codecNames() {
        var ffmpeg = FfmpegLibraries.get();
        // AV_CODEC_ID_H264 is 27 in every major since the enum was introduced.
        assertEquals("h264", ffmpeg.codecName(27));
    }
}
