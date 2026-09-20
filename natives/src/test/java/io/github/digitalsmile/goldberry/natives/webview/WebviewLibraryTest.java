package io.github.digitalsmile.goldberry.natives.webview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.digitalsmile.goldberry.natives.NativeLibrary;
import io.github.digitalsmile.goldberry.natives.NativePlatform;

/// Finding the optional second library — ADR-0441.
///
/// The interesting part is the **sibling** lookup, and it is here because it was
/// wrong first: a run pointed at a locally built `libgoldberry` reported no web
/// view support on a machine that had just built one, because nothing looked in
/// the directory the superbuild installs both of them into.
///
/// Loading is not tested — that needs the library, WebKit and a display, and the
/// singleton behind it resolves once per JVM. What is testable is *where it
/// looks*, which is where the defect was.
@DisplayName("finding libgoldberry-webview")
class WebviewLibraryTest {

    private @Nullable String saved;

    private boolean touched;

    @AfterEach
    void restore() {
        if (!touched) {
            return;
        }
        if (saved == null) {
            System.clearProperty(NativeLibrary.LIBRARY_PATH_PROPERTY);
        } else {
            System.setProperty(NativeLibrary.LIBRARY_PATH_PROPERTY, saved);
        }
    }

    /// Points the *main* library property somewhere, remembering what was there.
    ///
    /// The suite runs with this property set to the library it just built, so
    /// putting it back matters: a test that left it pointing at a temporary
    /// directory would make every later test in this JVM unable to find
    /// `libgoldberry`.
    private void pointAt(@Nullable Path mainLibrary) {
        saved = System.getProperty(NativeLibrary.LIBRARY_PATH_PROPERTY);
        touched = true;
        if (mainLibrary == null) {
            System.clearProperty(NativeLibrary.LIBRARY_PATH_PROPERTY);
        } else {
            System.setProperty(NativeLibrary.LIBRARY_PATH_PROPERTY, mainLibrary.toString());
        }
    }

    @Test
    @DisplayName("looks beside the libgoldberry a run was pointed at")
    void findsTheSibling(@TempDir Path directory) throws IOException {
        var platform = NativePlatform.current();
        var main = directory.resolve(platform.libraryFileName());
        var sibling = directory.resolve(platform.sharedLibraryFileName(WebviewLibrary.LIBRARY_STEM));
        Files.createFile(main);
        Files.createFile(sibling);
        pointAt(main);

        assertEquals(Optional.of(sibling), WebviewLibrary.besideTheMainLibrary(platform));
    }

    @Test
    @DisplayName("answers empty when the build produced only the one library")
    void theOrdinaryBuild(@TempDir Path directory) throws IOException {
        // Most builds: WebKit's headers were absent, so the superbuild made one
        // library and said so. Not a failure — the caller falls through to the
        // classifier jar and then to "no web view support".
        var platform = NativePlatform.current();
        var main = directory.resolve(platform.libraryFileName());
        Files.createFile(main);
        pointAt(main);

        assertTrue(WebviewLibrary.besideTheMainLibrary(platform).isEmpty());
    }

    @Test
    @DisplayName("answers empty when nothing pointed at a libgoldberry at all")
    void noProperty() {
        // A packaged application, which has no such property and reads both
        // libraries out of the classifier jar instead.
        pointAt(null);

        assertTrue(WebviewLibrary.besideTheMainLibrary(NativePlatform.current()).isEmpty());
    }

    @Test
    @DisplayName("names the library the way the packaging does, on all three platforms")
    void theFileName() {
        // The contract between this class, `NativePlatform` and the `nativeJar*`
        // tasks: three places have to agree on one file name, and the jar is built
        // from this same method.
        assertEquals(
                "libgoldberry-webview.so",
                new NativePlatform(NativePlatform.OperatingSystem.LINUX, NativePlatform.Architecture.X64)
                        .sharedLibraryFileName(WebviewLibrary.LIBRARY_STEM));
        assertEquals(
                "goldberry-webview.dll",
                new NativePlatform(NativePlatform.OperatingSystem.WINDOWS, NativePlatform.Architecture.X64)
                        .sharedLibraryFileName(WebviewLibrary.LIBRARY_STEM));
        assertEquals(
                "libgoldberry-webview.dylib",
                new NativePlatform(NativePlatform.OperatingSystem.MACOS, NativePlatform.Architecture.AARCH64)
                        .sharedLibraryFileName(WebviewLibrary.LIBRARY_STEM));
    }

    @Test
    @DisplayName("sits beside libgoldberry in the classifier jar")
    void theResourcePath() {
        var platform = new NativePlatform(NativePlatform.OperatingSystem.LINUX, NativePlatform.Architecture.X64);

        // One artifact per platform carries whatever that build produced, so the
        // two resource paths differ only in the file name. `nativeJar*` copies
        // both into this directory and `NativeLibraryTest` asserts the other half.
        assertEquals(
                "/io/github/digitalsmile/goldberry/natives/linux-x64/libgoldberry-webview.so",
                WebviewLibrary.resourcePath(platform));
        assertEquals(
                NativeLibrary.resourcePath(platform).replace("libgoldberry.so", "libgoldberry-webview.so"),
                WebviewLibrary.resourcePath(platform));
    }
}
