package dev.goldberry.build.release;

import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Gatherers;
import java.util.stream.Stream;

import dev.goldberry.build.repository.Repository;
import dev.goldberry.build.version.CalendarVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The changelog, the release-notes template, the job that opens the draft, and the
 * coordinates the documentation tells a reader to use, held to each other and to
 * {@code gradle.properties}. The documentation installs the newest release, which
 * is the changelog's newest version section, and not the line master works on:
 * after a tag, master declares the next one and the documentation stays put.
 */
@DisplayName("the release notes, as the repository holds them")
class ReleaseNotesRepositoryTest {

    private static final Pattern DECLARED = Pattern.compile("(?m)^goldberryVersion=(\\S+)$");

    /** The BOM's version as the README and the guide write it, in Gradle or Maven. */
    private static final Pattern BOM = Pattern.compile(
            "goldberry-bom:([^'\"]+)['\"]|<artifactId>goldberry-bom</artifactId>\\s*<version>([^<]+)</version>");

    private static CalendarVersion declared() {
        var matcher = DECLARED.matcher(Repository.read("gradle.properties"));
        assertTrue(matcher.find(), "gradle.properties declares no goldberryVersion");
        return CalendarVersion.parse(matcher.group(1));
    }

    @Test
    @DisplayName("the changelog's sections name versions, newest first, none past the line being worked on")
    void changelogInOrder() {
        var versions = ReleaseNotes.sections(Repository.read("CHANGELOG.md")).stream()
                .map(ReleaseNotes.Section::version)
                .flatMap(Optional::stream)
                .toList();
        var outOfOrder = versions.stream()
                .gather(Gatherers.windowSliding(2))
                .filter(pair -> pair.size() == 2 && pair.getFirst().compareTo(pair.getLast()) <= 0)
                .toList();
        assertAll(
                () -> assertTrue(!versions.isEmpty(), "CHANGELOG.md has no release section"),
                () -> assertTrue(outOfOrder.isEmpty(), () -> "not newest first: " + outOfOrder),
                () -> assertTrue(versions.getFirst().compareTo(declared()) <= 0,
                        () -> "CHANGELOG.md has a section for " + versions.getFirst()
                                + ", past the line gradle.properties declares, " + declared()));
    }

    @Test
    @DisplayName("the template has a place for the changes and names the version")
    void templateHasItsPlaces() {
        var template = Repository.read(".github/release-notes.md");
        assertAll(
                () -> assertTrue(template.contains(ReleaseNotes.CHANGES)),
                () -> assertTrue(template.contains("goldberry-bom:" + ReleaseNotes.VERSION)));
    }

    @Test
    @DisplayName("release.yml opens the tag's release as a draft with the notes, on a tag push only")
    void theDraftJob() {
        var release = Repository.workflow("release.yml");
        assertAll(
                () -> assertTrue(release.contains("./gradlew -q :core:releaseNotes")),
                () -> assertTrue(release.contains("gh release create \"$TAG\" --draft --verify-tag")),
                () -> assertTrue(release.contains("--notes-file \"$NOTES\"")),
                () -> assertTrue(release.contains("gh release edit \"$TAG\" --notes-file \"$NOTES\"")),
                () -> assertTrue(release.contains("contents: write")));
    }

    @Test
    @DisplayName("the README and the guide install the newest release the changelog has")
    void documentedCoordinates() {
        var newest = ReleaseNotes.sections(Repository.read("CHANGELOG.md")).stream()
                .map(ReleaseNotes.Section::version)
                .flatMap(Optional::stream)
                .findFirst()
                .orElseThrow()
                .toString();
        assertAll(Stream.of(
                        "README.md",
                        "book/src/getting-started/installing.md",
                        "book/src/getting-started/first-java-application.md")
                .map(file -> () -> {
                    var found = BOM.matcher(Repository.read(file)).results()
                            .map(result -> result.group(1) != null ? result.group(1) : result.group(2))
                            .toList();
                    assertTrue(!found.isEmpty(), file + " names no goldberry-bom version");
                    assertTrue(found.stream().allMatch(newest::equals),
                            () -> file + " installs " + found + ", and the newest release is " + newest);
                }));
    }
}
