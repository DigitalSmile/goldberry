package dev.goldberry.build.publish;

import dev.goldberry.build.repository.Repository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CorrespondingSource}: no FFmpeg binary is published without its source
 * beside it, and the build is wired so that the rule is the one that
 * runs.
 */
@DisplayName("the FFmpeg corresponding source")
class CorrespondingSourceTest {

    /** A pin of a full commit id in the version catalog. */
    private static final String COMMIT_PIN = "(?m)^%s = \"[0-9a-f]{40}\"$";

    /** A publication's classifiers: the main jar's {@code null}, sources, javadoc, and these. */
    private static List<String> publication(String... ffmpeg) {
        var classifiers = new ArrayList<String>(Arrays.asList(null, "sources", "javadoc"));
        classifiers.addAll(List.of(ffmpeg));
        return classifiers;
    }

    @Test
    @DisplayName("refuses a publication carrying FFmpeg without ffmpeg-sources")
    void refusesBinariesWithoutSource() {
        var refusal = assertThrows(IllegalStateException.class, () -> CorrespondingSource.require(
                "goldberry-media", publication("ffmpeg-linux-x64", "ffmpeg-macos-aarch64")));
        assertAll(
                () -> assertTrue(refusal.getMessage().startsWith(
                        "goldberry-media would publish ffmpeg-linux-x64, ffmpeg-macos-aarch64 without ffmpeg-sources"),
                        refusal.getMessage()),
                () -> assertTrue(refusal.getMessage().contains("snapshot is a distribution"), refusal.getMessage()),
                () -> assertTrue(refusal.getMessage().contains("Attach :media:ffmpegSourcesJar"),
                        refusal.getMessage()));
    }

    @Test
    @DisplayName("refuses one binary as surely as four")
    void refusesASingleBinary() {
        assertThrows(IllegalStateException.class,
                () -> CorrespondingSource.require("goldberry-media", publication("ffmpeg-windows-x64")));
    }

    @Test
    @DisplayName("lets the binaries through with the source beside them")
    void acceptsBinariesWithSource() {
        assertDoesNotThrow(() -> CorrespondingSource.require("goldberry-media",
                publication("ffmpeg-linux-x64", "ffmpeg-linux-aarch64", "ffmpeg-sources")));
    }

    @Test
    @DisplayName("asks nothing of a publication with no FFmpeg in it, nor of the source alone")
    void asksNothingWithoutBinaries() {
        assertAll(
                () -> assertDoesNotThrow(() -> CorrespondingSource.require("goldberry-core", publication())),
                () -> assertDoesNotThrow(
                        () -> CorrespondingSource.require("goldberry-natives", publication("linux-x64"))),
                () -> assertDoesNotThrow(() -> CorrespondingSource.require("goldberry-media",
                        publication("ffmpeg-sources"))),
                () -> assertEquals(Set.of(), CorrespondingSource.binaries(publication("ffmpeg-sources"))));
    }

    @Test
    @DisplayName("goldberry.publish checks every publication a build is about to publish")
    void thePublishingConventionChecks() {
        var convention = Repository.read("build-logic/src/main/groovy/goldberry.publish.gradle");
        assertAll(
                () -> assertTrue(convention.contains("CorrespondingSource.require("), "the check is not called"),
                () -> assertTrue(convention.contains("AbstractPublishToMaven"),
                        "the check must cover mavenLocal and Central alike"),
                () -> assertTrue(convention.contains("taskGraph.whenReady"),
                        "the check must run before any module uploads, not in one module's publish task"));
    }

    @Test
    @DisplayName("media attaches the source wherever it attaches a binary")
    void mediaAttachesTheSource() {
        var media = Repository.read("media/build.gradle");
        var attach = media.substring(media.indexOf("if (mediaArtifactsDir.isPresent())"));
        assertAll(
                () -> assertTrue(attach.contains("classifier = \"ffmpeg-${target}\""), attach),
                () -> assertTrue(attach.contains("classifier = CorrespondingSource.SOURCES"), attach),
                () -> assertTrue(media.contains("tasks.register('ffmpegSourcesJar', Jar)")));
    }

    @Test
    @DisplayName("the catalog pins each upstream's commit beside its tag")
    void commitsArePinned() {
        var catalog = Repository.read("gradle/libs.versions.toml");
        assertAll(
                () -> assertTrue(Pattern.compile(COMMIT_PIN.formatted("ffmpegCommit")).matcher(catalog).find(),
                        "no 40-character ffmpegCommit in gradle/libs.versions.toml"),
                () -> assertTrue(Pattern.compile(COMMIT_PIN.formatted("dav1dCommit")).matcher(catalog).find(),
                        "no 40-character dav1dCommit in gradle/libs.versions.toml"));
    }

    @Test
    @DisplayName("every notice says where the source is")
    void noticesPointAtTheSource() {
        for (var file : List.of("NOTICE", "THIRD-PARTY-NOTICES.md", "media/src/main/cmake/package.cmake")) {
            assertTrue(Repository.read(file).contains(CorrespondingSource.SOURCES),
                    file + " does not name the " + CorrespondingSource.SOURCES + " classifier");
        }
    }
}
