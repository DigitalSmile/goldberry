package io.github.digitalsmile.goldberry.emoji;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/// The face is declared to `native-image`, not left to a trace (ADR-0160,
/// ADR-0453).
///
/// This module ships one resource and it is the exact one `:core`'s own
/// metadata names as the thing a traced run misses — "Inter but neither
/// JetBrains Mono nor OpenMoji, because the run never switched theme and never
/// drew mono text or an emoji". OpenMoji then moved out of `:core` (ADR-0384)
/// and its declaration did not follow, so nothing covered it and the showcase's
/// native image died on the first paragraph containing an emoji.
///
/// Read as text rather than through `native-image`, because there is no
/// GraalVM in an ordinary test run and the property worth guarding is an
/// agreement between three files: the glob, the `--root` the asset step is
/// given, and the path [OpenMojiFont] asks for.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("the declared font resource")
class DeclaredFontResourceTest {

    /// What `OpenMojiFont.RESOURCE` names, without its leading slash — the form
    /// a `glob` in the metadata is written in.
    private static final String FONT = "io/github/digitalsmile/goldberry/emoji/fonts/OpenMoji-color.ttf";

    private final Path projectDir = locateProjectDir();

    private final String metadata = read(
            projectDir.resolve(
                    "src/main/resources/META-INF/native-image/io.github.digitalsmile/goldberry-emoji/reachability-metadata.json"));

    /// The `:emoji` project directory, found by walking up from wherever the
    /// tests were started — Gradle uses the project directory, an IDE may use
    /// the repository root.
    ///
    /// Not a guess that falls back to nothing: a drift guard that skips when it
    /// cannot find what it guards is a green tick over an unchecked invariant,
    /// which is the reasoning `SuperbuildTest` states for the same walk.
    private static Path locateProjectDir() {
        var directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            if (Files.isRegularFile(
                    directory.resolve("src/main/java/io/github/digitalsmile/goldberry/emoji/OpenMojiFont.java"))) {
                return directory;
            }
            if (Files.isRegularFile(directory.resolve(
                    "emoji/src/main/java/io/github/digitalsmile/goldberry/emoji/OpenMojiFont.java"))) {
                return directory.resolve("emoji");
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException(
                "cannot find the :emoji project at or above " + Path.of("").toAbsolutePath());
    }

    private static String read(Path path) {
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("cannot read " + path + ", so nothing below checks anything");
        }
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + path, e);
        }
    }

    @Test
    @DisplayName("declares the font this module actually reads")
    void declaresTheFont() {
        // The glob's directory, and the module it is declared over. Both wrong
        // in the same way is the failure this test exists for: the old
        // declaration was correct for a path in another module.
        assertAll(
                () -> assertTrue(
                        metadata.contains("\"io/github/digitalsmile/goldberry/emoji/fonts/*.ttf\""),
                        "the glob must cover " + FONT + ":\n" + metadata),
                () -> assertTrue(
                        metadata.contains("\"module\": \"io.github.digitalsmile.goldberry.emoji\""),
                        "declared over this module, not the one the face used to live in"));
    }

    @Test
    @DisplayName("the glob, the asset step's root and the read path agree")
    void theThreeSpellingsAgree() {
        // The asset step writes under `--root=…`; the metadata globs under the
        // same directory; OpenMojiFont reads a file inside it. Any two of the
        // three can be changed without the build noticing, and the symptom is a
        // native image that is fine until something draws an emoji.
        var build = read(projectDir.resolve("build.gradle"));
        assertAll(
                () -> assertTrue(
                        build.contains("'--root=io/github/digitalsmile/goldberry/emoji'"),
                        "the asset step writes somewhere else:\n" + build),
                () -> assertTrue(FONT.startsWith("io/github/digitalsmile/goldberry/emoji/fonts/"), FONT),
                () -> assertTrue(FONT.endsWith(".ttf"), FONT));
    }

    @Test
    @DisplayName("the font really is on the test classpath under that name")
    void theFontIsWhereItIsDeclared() {
        // The declaration is worth nothing if it names a file the asset step
        // does not produce. This is the half a text comparison cannot cover.
        assertNotNull(
                OpenMojiFont.class.getResourceAsStream("/" + FONT),
                FONT + " is not on the classpath — the asset step did not write it, or wrote it elsewhere");
    }
}
