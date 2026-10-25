package dev.goldberry.media.ffm;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import dev.goldberry.media.MediaCapabilities;
import dev.goldberry.media.codec.CodecId;

/// `libgoldberry-media` found the way an application finds it: in
/// `goldberry-media`'s `natives-<target>` jar on the class path, with nothing
/// naming a directory.
///
/// Run only by `:media:testNativesJar`, in a JVM of its own, because the loader
/// loads once per process. It checks the one path no other test takes: the
/// resource layout the jar task writes, against the one [MediaPlatform] reads.
@Tag("natives-jar")
@DisplayName("goldberry-media's natives classifier, on the class path")
class MediaNativesJarTest {

    @Test
    @DisplayName("loads from the jar, unpacked into a temporary directory, and decodes")
    void loadsFromTheJar() {
        assertNull(System.getProperty(MediaNatives.LIBRARY_DIRECTORY_PROPERTY));
        assertTrue(
                MediaNatives.isAvailable(),
                () -> "not loaded from the jar: "
                        + MediaNatives.unavailableReason().orElse(""));

        var library = MediaNatives.get();
        var directory = library.directory();
        assertTrue(directory.getFileName().toString().startsWith("goldberry-media"), directory.toString());
        assertTrue(Files.isRegularFile(directory.resolve(NativeLayout.FILE_NAME)));
        assertTrue(Files.isRegularFile(directory.resolve(MediaPlatform.current().fileName())));
        assertTrue(library.versions().contains("dav1d"), library.versions());

        var capabilities = MediaCapabilities.current();
        assertTrue(capabilities.canDecode(CodecId.AV1));
        assertTrue(capabilities.canDecode(CodecId.OPUS));
    }

    @Test
    @DisplayName("carries the notices the upstreams' licences ask a binary to travel with")
    void carriesNotices() {
        var loader = MediaNativesJarTest.class.getClassLoader();
        assertNotNull(loader.getResource("META-INF/media-NOTICE.txt"));
        for (var licence : new String[] {"dav1d", "libvpx", "opus", "libvorbis", "libtheora", "FLAC", "libyuv"}) {
            assertNotNull(loader.getResource("META-INF/licenses/" + licence + ".txt"), licence);
        }
    }
}
