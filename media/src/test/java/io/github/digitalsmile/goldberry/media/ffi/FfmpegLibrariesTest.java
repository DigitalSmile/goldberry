package io.github.digitalsmile.goldberry.media.ffi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// The loader's refusals, which need no FFmpeg: each is a directory that is wrong
/// in one chosen way.
@DisplayName("FfmpegLibraries")
class FfmpegLibrariesTest {

    private static final FfmpegPlatform PLATFORM = FfmpegPlatform.of("Linux", "amd64");

    @TempDir
    Path directory;

    private String reason(FfmpegLibraries.State state) {
        return assertInstanceOf(FfmpegLibraries.State.Unavailable.class, state).reason();
    }

    private void copyFixture() throws IOException {
        try (var in = LayoutFixture.class.getResourceAsStream(LayoutFixture.RESOURCE)) {
            Files.copy(in, directory.resolve(FfmpegLayout.FILE_NAME));
        }
    }

    @Test
    @DisplayName("refuses a directory with no layout file")
    void noLayout() {
        assertTrue(reason(FfmpegLibraries.load(directory, PLATFORM)).startsWith("no ffmpeg-layout.properties"));
    }

    @Test
    @DisplayName("refuses a layout file it cannot read")
    void badLayout() throws IOException {
        Files.writeString(directory.resolve(FfmpegLayout.FILE_NAME), "nonsense\n");
        assertTrue(reason(FfmpegLibraries.load(directory, PLATFORM)).startsWith("unreadable"));
    }

    @Test
    @DisplayName("names the first library that is missing, in load order")
    void missingLibrary() throws IOException {
        copyFixture();
        assertEquals("no libavutil-goldberry.so.60 in " + directory, reason(FfmpegLibraries.load(directory, PLATFORM)));
    }

    @Test
    @DisplayName("refuses a library file that will not load, rather than crash")
    void unloadable() throws IOException {
        copyFixture();
        var current = FfmpegPlatform.current();
        for (var library : FfmpegLibrary.values()) {
            Files.writeString(directory.resolve(current.fileName(library)), "not a shared library");
        }
        assertTrue(reason(FfmpegLibraries.load(directory, current)).contains("would not load"));
    }

    @Test
    @DisplayName("reports every library whose major is not the pinned one, and only those")
    void versions() {
        var versions = new EnumMap<FfmpegLibrary, Integer>(FfmpegLibrary.class);
        for (var library : FfmpegLibrary.values()) {
            versions.put(library, library.pinnedMajor() << 16 | 3 << 8 | 100);
        }
        assertEquals(List.of(), FfmpegLibraries.versionMismatches(versions));
        versions.put(FfmpegLibrary.AVCODEC, 61 << 16 | 19 << 8 | 101);
        assertEquals(
                List.of("libavcodec is 61.19.101, expected major 62"), FfmpegLibraries.versionMismatches(versions));
        assertEquals(
                "no version for libavutil",
                FfmpegLibraries.versionMismatches(Map.of()).getFirst());
    }

    @Test
    @DisplayName("reads a packed version as FFmpeg writes it")
    void describe() {
        assertEquals(62, FfmpegLibrary.majorOf(62 << 16 | 28 << 8 | 100));
        assertEquals("62.28.100", FfmpegLibrary.describe(62 << 16 | 28 << 8 | 100));
    }
}
