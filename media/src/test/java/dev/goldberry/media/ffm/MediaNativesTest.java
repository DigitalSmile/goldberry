package dev.goldberry.media.ffm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.goldberry.media.MediaNativesRequirement;

@DisplayName("MediaNatives")
class MediaNativesTest {

    @TempDir
    Path directory;

    private MediaPlatform platform;

    @BeforeEach
    void platform() {
        platform = MediaPlatform.current();
    }

    private void copyFixture() throws IOException {
        try (var in = LayoutFixture.class.getResourceAsStream(LayoutFixture.RESOURCE)) {
            Files.copy(in, directory.resolve(NativeLayout.FILE_NAME));
        }
    }

    private static String reason(MediaNatives.State state) {
        return assertInstanceOf(MediaNatives.State.Unavailable.class, state).reason();
    }

    @Test
    @DisplayName("refuses a directory with no layout file")
    void noLayout() {
        assertTrue(reason(MediaNatives.load(directory, platform)).startsWith("no " + NativeLayout.FILE_NAME));
    }

    @Test
    @DisplayName("refuses a layout file it cannot read")
    void badLayout() throws IOException {
        Files.writeString(directory.resolve(NativeLayout.FILE_NAME), "nonsense\n", StandardCharsets.UTF_8);
        assertTrue(reason(MediaNatives.load(directory, platform)).startsWith("unreadable"));
    }

    @Test
    @DisplayName("refuses a layout file of another ABI, or of none")
    void wrongAbi() throws IOException {
        Files.writeString(directory.resolve(NativeLayout.FILE_NAME), "abi=99\n", StandardCharsets.UTF_8);
        assertTrue(reason(MediaNatives.load(directory, platform)).contains("describes ABI 99"));
        Files.writeString(directory.resolve(NativeLayout.FILE_NAME), "const.A=1\n", StandardCharsets.UTF_8);
        assertTrue(reason(MediaNatives.load(directory, platform)).contains("does not say which ABI"));
    }

    @Test
    @DisplayName("names the library when it is missing")
    void missingLibrary() throws IOException {
        copyFixture();
        assertEquals("no " + platform.fileName() + " in " + directory, reason(MediaNatives.load(directory, platform)));
    }

    @Test
    @DisplayName("refuses a library file that will not load, rather than crash")
    void unloadable() throws IOException {
        copyFixture();
        Files.writeString(directory.resolve(platform.fileName()), "not a library", StandardCharsets.UTF_8);
        assertTrue(reason(MediaNatives.load(directory, platform)).contains("would not load"));
    }

    @Test
    @DisplayName("loads the built library, checks it, and says what is in it")
    void loadsTheBuiltLibrary() {
        MediaNativesRequirement.enforce();
        var library = MediaNatives.get();
        assertEquals(MediaLibrary.ABI, library.media().abi().call());
        assertTrue(library.versions().startsWith("dav1d 1.5.4"), library.versions());
        assertTrue(library.versions().contains("libvpx v1.17.0"), library.versions());
        assertTrue(library.versions().contains("opus"), library.versions());
        assertTrue(library.versions().contains("theora Xiph.Org libtheora 1.2.0"), library.versions());
        assertTrue(library.versions().contains("FLAC 1.5.0"), library.versions());
    }
}
