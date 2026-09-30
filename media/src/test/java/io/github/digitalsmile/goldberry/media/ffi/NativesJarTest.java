package io.github.digitalsmile.goldberry.media.ffi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Files;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.media.MediaProbe;
import io.github.digitalsmile.goldberry.media.Wav;
import io.github.digitalsmile.goldberry.media.codec.MediaType;
import io.github.digitalsmile.goldberry.media.io.MemoryIO;
import io.github.digitalsmile.goldberry.media.io.Source;

/// FFmpeg found the way an application finds it: in `goldberry-media`'s
/// `ffmpeg-<target>` jar on the class path, with nothing naming a directory.
///
/// Run only by `:media:testNativesJar`, in a JVM of its own, because the loader
/// loads once per process. It checks the one path no other test takes: the
/// resource layout the jar task writes, against the one [FfmpegPlatform] reads.
@Tag("natives-jar")
@DisplayName("goldberry-media's FFmpeg classifier, on the class path")
class NativesJarTest {

    @Test
    @DisplayName("loads from the jar, unpacked into a temporary directory, and probes")
    void loadsFromTheJar() {
        assertNull(System.getProperty(FfmpegLibraries.LIBRARY_DIRECTORY_PROPERTY));
        assertTrue(
                FfmpegLibraries.isAvailable(),
                () -> "not loaded from the jar: "
                        + FfmpegLibraries.unavailableReason().orElse(""));

        var directory = FfmpegLibraries.get().directory();
        assertTrue(directory.getFileName().toString().startsWith("goldberry-ffmpeg"), directory.toString());
        assertTrue(Files.isRegularFile(directory.resolve(FfmpegLayout.FILE_NAME)));
        for (var library : FfmpegLibrary.values()) {
            assertTrue(Files.isRegularFile(
                    directory.resolve(FfmpegPlatform.current().fileName(library))));
        }

        var info =
                MediaProbe.probe(Source.of(URI.create("mem:///tone.wav")), new MemoryIO(Wav.silence(8_000, 1, 8_000)));
        assertEquals(1, info.tracks(MediaType.AUDIO).size());
    }

    @Test
    @DisplayName("carries the notices the LGPL and BSD-2 ask a binary to travel with")
    void carriesNotices() {
        var loader = NativesJarTest.class.getClassLoader();
        assertNotEquals(null, loader.getResource("META-INF/ffmpeg-NOTICE.txt"));
        assertNotEquals(null, loader.getResource("META-INF/licenses/ffmpeg.txt"));
        assertNotEquals(null, loader.getResource("META-INF/licenses/dav1d.txt"));
    }
}
