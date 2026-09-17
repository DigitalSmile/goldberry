package io.github.digitalsmile.goldberry.build.repository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The repository build-logic builds, read as text by the drift guards -- the
 * tests that hold a table in Java and a workflow or build script to the same
 * statement (ADR-0082).
 */
public final class Repository {

    private Repository() {
    }

    /**
     * The repository root. Handed over by {@code build-logic/build.gradle} so the
     * test does not have to guess; found by walking up when it is absent, which is
     * what happens when the test is run straight from an IDE.
     */
    public static Path root() {
        var declared = System.getProperty("goldberry.repoRoot");
        if (declared != null && !declared.isBlank()) {
            return Path.of(declared);
        }
        var directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            if (Files.isDirectory(directory.resolve(".github/workflows"))) {
                return directory;
            }
            directory = directory.getParent();
        }
        // Deliberately not `assumeTrue`. A drift guard that skips when it cannot
        // find what it guards is a green tick over an unchecked invariant, which
        // is the one outcome worse than a red one.
        throw new IllegalStateException(
                "cannot find .github/workflows above " + Path.of("").toAbsolutePath()
                        + "; set -Dgoldberry.repoRoot=<repo>");
    }

    /**
     * A file under the root, read whole and with its line endings normalised to
     * LF; a missing file is an error, not an empty string.
     */
    public static String read(String relative) {
        var file = root().resolve(relative);
        if (!Files.isRegularFile(file)) {
            throw new AssertionError(file + " does not exist");
        }
        try {
            return lineFeeds(Files.readString(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * CRLF and a bare CR become LF.
     *
     * <p>Git on a Windows runner checks out with {@code core.autocrlf=true}, so
     * every workflow arrived there with CRLF endings. A guard that split
     * {@code showcase.yml} on {@code "\n\n"} found no such thing, and failed with
     * a {@code StringIndexOutOfBoundsException} instead of a message. The guards
     * are about content, not about line endings, so the endings are taken out
     * here, once, rather than in each of them (ADR-0338).
     */
    static String lineFeeds(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    /** Whether a file exists under the root. */
    public static boolean exists(String relative) {
        return Files.exists(root().resolve(relative));
    }

    /** A workflow under {@code .github/workflows}. */
    public static String workflow(String name) {
        return read(".github/workflows/" + name);
    }
}
