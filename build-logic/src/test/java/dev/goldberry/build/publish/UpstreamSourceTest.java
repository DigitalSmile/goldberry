package dev.goldberry.build.publish;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * {@link UpstreamSource} against a repository made here, with git: the archive is
 * the tag's whole tree, a moved tag is refused, and a cached clone needs no
 * network.
 */
@DisplayName("an upstream's source")
class UpstreamSourceTest {

    @TempDir
    Path temp;

    private Path upstream;

    private String tagged;

    @BeforeEach
    void anUpstreamWithATag() throws IOException {
        assumeTrue(gitWorks(), "no git on this machine");
        upstream = temp.resolve("upstream");
        Files.createDirectories(upstream.resolve("src"));
        Files.writeString(upstream.resolve("configure"), "#!/bin/sh\n");
        Files.writeString(upstream.resolve("src/codec.c"), "int codec;\n");
        // What an upstream may one day mark, and an archive must still carry.
        Files.writeString(upstream.resolve("tests.ref"), "reference\n");
        Files.writeString(upstream.resolve("version.h"), "$Format:%H$\n");
        Files.writeString(upstream.resolve(".gitattributes"), "tests.ref export-ignore\nversion.h export-subst\n");
        git(upstream, "init", "--quiet");
        git(upstream, "add", ".");
        git(upstream, "-c", "user.name=t", "-c", "user.email=t@t", "commit", "--quiet", "-m", "one");
        git(upstream, "tag", "v1");
        tagged = git(upstream, "rev-parse", "HEAD");
    }

    @Test
    @DisplayName("archives the tag's whole tree under name-tag/, export-ignore and export-subst notwithstanding")
    void archivesTheWholeTree() throws IOException {
        var archive = temp.resolve("out/demo.tar");
        new UpstreamSource("demo", upstream.toUri().toString(), "v1", tagged)
                .archive("git", temp.resolve("clone"), archive);

        var extracted = extract(archive);
        assertAll(
                () -> assertTrue(Files.isRegularFile(extracted.resolve("demo-v1/configure"))),
                () -> assertTrue(Files.isRegularFile(extracted.resolve("demo-v1/src/codec.c"))),
                () -> assertTrue(Files.isRegularFile(extracted.resolve("demo-v1/tests.ref")),
                        "export-ignore left a file out of the source"),
                () -> assertEquals("$Format:%H$\n", Files.readString(extracted.resolve("demo-v1/version.h")),
                        "export-subst rewrote a file"),
                () -> assertTrue(Files.isRegularFile(extracted.resolve("demo-v1/.gitattributes"))));
    }

    @Test
    @DisplayName("refuses a tag that no longer names the pinned commit")
    void refusesAMovedTag() throws IOException {
        Files.writeString(upstream.resolve("src/codec.c"), "int codec = 1;\n");
        git(upstream, "-c", "user.name=t", "-c", "user.email=t@t", "commit", "--quiet", "-am", "two");
        git(upstream, "tag", "--force", "v1");
        var moved = git(upstream, "rev-parse", "HEAD");

        var pinned = new UpstreamSource("demo", upstream.toUri().toString(), "v1", tagged);
        var refusal = assertThrows(IllegalStateException.class,
                () -> pinned.archive("git", temp.resolve("clone"), temp.resolve("demo.tar")));
        assertAll(
                () -> assertTrue(refusal.getMessage().contains("names " + moved + ", not the pinned " + tagged),
                        refusal.getMessage()),
                () -> assertTrue(Files.notExists(temp.resolve("demo.tar")), "an archive of the wrong tree"));
    }

    @Test
    @DisplayName("needs no network once the clone has the commit")
    void worksOffline() {
        var clone = temp.resolve("clone");
        new UpstreamSource("demo", upstream.toUri().toString(), "v1", tagged)
                .archive("git", clone, temp.resolve("first.tar"));

        var unreachable = temp.resolve("gone").toUri().toString();
        new UpstreamSource("demo", unreachable, "v1", tagged).archive("git", clone, temp.resolve("second.tar"));
        assertTrue(Files.isRegularFile(temp.resolve("second.tar")));
    }

    @Test
    @DisplayName("takes a full commit id and nothing shorter")
    void wantsAFullCommit() {
        assertThrows(IllegalArgumentException.class, () -> new UpstreamSource("demo", "x", "v1", "1041abdc9"));
    }

    /** Unpacked with tar, which every runner has: the JDK cannot read one. */
    private Path extract(Path archive) throws IOException {
        var into = Files.createDirectories(temp.resolve("extracted"));
        run(into, "tar", "-xf", archive.toAbsolutePath().toString());
        return into;
    }

    private static boolean gitWorks() {
        try {
            run(Path.of("").toAbsolutePath(), "git", "--version");
            return true;
        } catch (IOException | IllegalStateException e) {
            return false;
        }
    }

    private static String git(Path directory, String... arguments) throws IOException {
        var command = new ArrayList<String>(List.of("git"));
        command.addAll(List.of(arguments));
        return run(directory, command.toArray(String[]::new));
    }

    private static String run(Path directory, String... command) throws IOException {
        var process = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start();
        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        try {
            if (process.waitFor() != 0) {
                throw new IllegalStateException(String.join(" ", command) + ": " + output);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        return output;
    }
}
