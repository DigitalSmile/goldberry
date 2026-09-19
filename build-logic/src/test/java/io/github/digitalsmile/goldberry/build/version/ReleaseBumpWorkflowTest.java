package io.github.digitalsmile.goldberry.build.version;

import io.github.digitalsmile.goldberry.build.repository.Repository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bump job in {@code release.yml}, held as text (ADR-0426).
 *
 * <p>Every rule here is one that fails <em>quietly</em>, which is what makes them
 * worth a drift guard rather than a comment. A job that lost its {@code needs:
 * publish} proposes a version bump for a release that went red. One that lost the
 * {@code github.event_name == 'push'} condition proposes one on every rehearsal.
 * One whose permissions were trimmed back to the workflow's {@code contents: read}
 * fails at the push, after the release is already permanent on Central.
 */
@DisplayName("the release workflow's bump job")
class ReleaseBumpWorkflowTest {

    private String release;

    @BeforeEach
    void read() {
        release = Repository.workflow("release.yml");
    }

    @Test
    @DisplayName("runs only after a successful publish, and only for a tag")
    void onlyAfterARealRelease() {
        assertAll(
                () -> assertTrue(release.contains("needs: publish"),
                        "the bump must not run when the release failed"),
                () -> assertTrue(release.contains("if: github.event_name == 'push'"),
                        "a workflow_dispatch rehearsal publishes nothing and must propose no bump"));
    }

    @Test
    @DisplayName("has the two permissions it needs, against a workflow that grants neither")
    void permissions() {
        assertAll(
                () -> assertTrue(release.contains("permissions:\n  contents: read"),
                        "the workflow's own default must stay read-only"),
                () -> assertTrue(release.contains("      contents: write"),
                        "the job pushes a branch"),
                () -> assertTrue(release.contains("      pull-requests: write"),
                        "the job opens a pull request"));
    }

    @Test
    @DisplayName("bumps the default branch, not the tag it was triggered by")
    void bumpsTheDefaultBranch() {
        assertAll(
                () -> assertTrue(release.contains("ref: ${{ github.event.repository.default_branch }}"),
                        "checking out the tag would bump a detached commit nobody reads"),
                () -> assertTrue(release.contains("--base \"${{ github.event.repository.default_branch }}\"")
                                || release.contains("--base \"$DEFAULT_BRANCH\""),
                        "the pull request targets the default branch"));
    }

    @Test
    @DisplayName("asks the build for the arithmetic instead of doing it in shell")
    void usesTheTask() {
        // The whole reason VersionBump exists in Java. A `sed` that incremented
        // the last number would turn 2026.1.1 into 2026.1.2 correctly and 2026.3
        // into 2026.4 in a year when it should be 2027.1.
        assertTrue(release.contains(":core:bumpVersion"), "release.yml must call the task");
    }

    @Test
    @DisplayName("reads gradle.properties with the pattern that file actually matches")
    void theGuardMatchesTheRealFile() {
        // The guard is a `sed` in the workflow, and it is the one piece of
        // arithmetic that could not move into Java: it runs before the JDK is even
        // set up. So it is held to the real file here instead.
        var declared = Repository.read("gradle.properties").lines()
                .filter(line -> line.startsWith(VersionBump.PROPERTY + "="))
                .count();
        assertAll(
                () -> assertTrue(release.contains("sed -n 's/^goldberryVersion=//p' gradle.properties"),
                        "the guard's pattern is the one this test checks"),
                () -> assertTrue(declared == 1,
                        "gradle.properties declares " + VersionBump.PROPERTY + " " + declared
                                + " times; the guard's sed would print " + declared + " lines"));
    }
}
