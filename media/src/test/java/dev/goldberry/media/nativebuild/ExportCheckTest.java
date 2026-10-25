package dev.goldberry.media.nativebuild;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/// The export check of the media superbuild, `check_exports.cmake`, held to what
/// each toolchain prints for a library: nm on ELF and Mach-O, and objdump's
/// export table for a DLL in both of the layouts binutils has printed it in.
@DisplayName("check_exports.cmake")
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class ExportCheckTest {

    private static final Path SCRIPT = Path.of("src", "main", "cmake", "check_exports.cmake");

    private static final List<String> EXPORTED = List.of("dav1d_open", "goldberry_media_abi");

    @TempDir
    private Path dir;

    /// One toolchain's listing of a library that defines [#EXPORTED].
    private enum Listing {
        ELF("Linux", """
                0000000000000000 A GOLDBERRY_MEDIA_1
                0000000000012340 T dav1d_open@@GOLDBERRY_MEDIA_1
                0000000000001230 T goldberry_media_abi@@GOLDBERRY_MEDIA_1
                """),
        MACH_O("Darwin", """
                0000000000003f40 T _dav1d_open
                0000000000003f80 T _goldberry_media_abi
                """),
        /// Binutils before 2.39: the name follows the index.
        PE_OLD_BINUTILS("Windows", """
                The Export Tables (interpreted .edata section contents)

                Export Address Table -- Ordinal Base 1
                \t[   0] +base[   1] 1a40 Export RVA
                \t[   1] +base[   2] 1a50 Export RVA

                [Ordinal/Name Pointer] Table
                \t[   0] dav1d_open
                \t[   1] goldberry_media_abi

                The Function Table (interpreted .pdata section contents)
                """),
        /// Binutils 2.39 and later, MSYS2's among them: a column header, and the
        /// ordinal and hint before the name.
        PE_NEW_BINUTILS("Windows", """
                The Export Tables (interpreted .edata section contents)

                Export Address Table -- Ordinal Base 1
                \t[   0] +base[   1] 1a40 Export RVA
                \t[   1] +base[   2] 1a50 Export RVA

                [Ordinal/Name Pointer] Table -- Ordinal Base 1
                \t          Ordinal   Hint Name
                \t[   0] +base[   1]  0000 dav1d_open
                \t[   1] +base[   2]  0001 goldberry_media_abi

                The Function Table (interpreted .pdata section contents)
                """),
        /// What a native Windows objdump writes: the same, with CRLF line ends.
        PE_CRLF("Windows", PE_NEW_BINUTILS.dump.replace("\n", "\r\n"));

        final String system;
        final String dump;

        Listing(String system, String dump) {
            this.system = system;
            this.dump = dump;
        }
    }

    private record Outcome(int status, String output) {
        boolean passed() {
            return status == 0;
        }
    }

    @BeforeAll
    static void cmakeIsThere() {
        assumeTrue(Files.isRegularFile(SCRIPT), "run from the :media project directory");
        assumeTrue(cmakeRuns(), "no cmake on PATH");
    }

    private static boolean cmakeRuns() {
        try {
            var process = new ProcessBuilder("cmake", "--version")
                    .redirectErrorStream(true)
                    .start();
            process.getInputStream().transferTo(OutputStream.nullOutputStream());
            return process.waitFor() == 0;
        } catch (IOException _) {
            return false;
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private Outcome check(Listing listing, List<String> wanted) throws IOException, InterruptedException {
        return check(listing.system, listing.dump, wanted);
    }

    private Outcome check(String system, String listing, List<String> wanted) throws IOException, InterruptedException {
        var dump = Files.writeString(dir.resolve("dump.txt"), listing, StandardCharsets.UTF_8);
        var symbols = Files.write(
                dir.resolve("goldberry-media.symbols"),
                Stream.concat(Stream.of("# the export list"), wanted.stream()).toList());
        var process = new ProcessBuilder(
                        "cmake",
                        "-DDUMP=" + dump.toAbsolutePath(),
                        "-DSYMBOLS=" + symbols.toAbsolutePath(),
                        "-DSYSTEM=" + system,
                        "-DLIBRARY=unused",
                        "-P",
                        SCRIPT.toAbsolutePath().toString())
                .redirectErrorStream(true)
                .start();
        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Outcome(process.waitFor(), output);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Listing.class)
    @DisplayName("finds every exported symbol in each toolchain's listing")
    void accepts(Listing listing) throws IOException, InterruptedException {
        var outcome = check(listing, EXPORTED);
        assertTrue(outcome.passed(), outcome.output());
        assertTrue(outcome.output().contains("defines every one of the 2 exported symbols"), outcome.output());
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Listing.class)
    @DisplayName("names the symbol a library does not define")
    void refuses(Listing listing) throws IOException, InterruptedException {
        var outcome = check(listing, List.of("dav1d_open", "opus_decode", "goldberry_media_abi"));
        assertFalse(outcome.passed(), outcome.output());
        assertTrue(outcome.output().contains("opus_decode"), outcome.output());
        assertFalse(outcome.output().contains("dav1d_open"), outcome.output());
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(
            value = Listing.class,
            names = {"PE_OLD_BINUTILS", "PE_NEW_BINUTILS", "PE_CRLF"})
    @DisplayName("reads a DLL's names from the name pointer table alone, not the address table")
    void nameTableOnly(Listing listing) throws IOException, InterruptedException {
        var outcome = check(listing, List.of("RVA"));
        assertFalse(outcome.passed(), outcome.output());
    }

    @Test
    @DisplayName("refuses a DLL with no export table at all")
    void noExportTable() throws IOException, InterruptedException {
        var outcome = check("Windows", "The Function Table (interpreted .pdata section contents)\n", EXPORTED);
        assertFalse(outcome.passed(), outcome.output());
        assertTrue(outcome.output().contains("goldberry_media_abi"), outcome.output());
    }
}
