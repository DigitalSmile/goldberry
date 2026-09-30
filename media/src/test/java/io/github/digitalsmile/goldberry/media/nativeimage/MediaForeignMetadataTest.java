package io.github.digitalsmile.goldberry.media.nativeimage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.digitalsmile.goldberry.media.ffi.FfmpegDescriptors;

/// The one file an image of this module is built from: FFmpeg's shapes and the
/// system decoders', and the natives jar's resources (ADR-0339, ADR-0493).
@DisplayName("MediaForeignMetadata")
class MediaForeignMetadataTest {

    @Test
    @DisplayName("holds FFmpeg's shapes and every system decoder's, each once")
    void bothHalves() {
        var downcalls = MediaForeignMetadata.downcalls();
        assertEquals(downcalls.size(), new HashSet<>(downcalls).size());
        assertTrue(downcalls.containsAll(FfmpegDescriptors.downcalls()));
        assertTrue(downcalls.containsAll(PlatformDescriptors.downcalls()));

        var upcalls = MediaForeignMetadata.upcalls();
        assertTrue(upcalls.containsAll(FfmpegDescriptors.UPCALLS));
        assertTrue(upcalls.containsAll(PlatformDescriptors.UPCALLS));
    }

    @Test
    @DisplayName("writes the file, with the natives jar's resources and every shape")
    void writes(@TempDir Path directory) throws IOException {
        var target = directory.resolve("META-INF/native-image/x/reachability-metadata.json");
        MediaForeignMetadata.main(new String[] {target.toString()});

        var json = Files.readString(target);
        assertTrue(json.contains("{\"glob\": \"" + MediaForeignMetadata.NATIVES_GLOB + "\"}"), json);
        for (var upcall : MediaForeignMetadata.upcalls()) {
            assertTrue(json.contains(MetadataGrammar.entry(upcall)), upcall::toString);
        }
        assertEquals(
                MediaForeignMetadata.downcalls().size()
                        + MediaForeignMetadata.upcalls().size(),
                json.lines().filter(line -> line.contains("\"returnType\"")).count());
        assertTrue(json.endsWith("}\n"));
    }

    @Test
    @DisplayName("refuses to run without exactly one path")
    void usage() {
        assertThrows(IllegalArgumentException.class, () -> MediaForeignMetadata.main(new String[0]));
    }
}
