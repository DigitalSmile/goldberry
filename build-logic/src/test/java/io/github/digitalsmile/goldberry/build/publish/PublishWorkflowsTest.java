package io.github.digitalsmile.goldberry.build.publish;

import io.github.digitalsmile.goldberry.build.repository.Repository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shape ADR-0334 gives the publishing workflows, held as text.
 *
 * <p>Each rule here is one that fails quietly when broken. A per-OS workflow that
 * regains its {@code push} trigger builds every native library twice per commit
 * and turns nothing red. A second publisher of snapshots races the first and
 * leaves Central's {@code maven-metadata.xml} listing one platform's classifier
 * jar -- which is a resolution failure on a consumer's machine, days later.
 */
@DisplayName("the publishing workflows")
class PublishWorkflowsTest {

    /** A top-level {@code push:} trigger, indented under {@code on:}. */
    private static final Pattern PUSH_TRIGGER = Pattern.compile("(?m)^  push:");

    @ParameterizedTest(name = "{0} is not triggered by a push")
    @ValueSource(strings = {"linux.yml", "macos.yml", "windows.yml"})
    @DisplayName("the per-OS workflows run on a push only through snapshot.yml")
    void perOsWorkflowsHaveNoPushTrigger(String name) {
        var text = Repository.workflow(name);
        assertAll(
                () -> assertFalse(PUSH_TRIGGER.matcher(text).find(),
                        name + " has a push trigger; snapshot.yml already runs it on every push to master"),
                () -> assertTrue(text.contains("workflow_call:"), name + " must stay callable"),
                () -> assertTrue(text.contains("pull_request:"), name + " must still gate pull requests"));
    }

    @ParameterizedTest(name = "{0} calls publish.yml")
    @ValueSource(strings = {"snapshot.yml", "release.yml"})
    @DisplayName("snapshots and releases go through the one publishing workflow")
    void callersUseTheReusableWorkflow(String name) {
        var text = Repository.workflow(name);
        assertAll(
                () -> assertTrue(text.contains("uses: ./.github/workflows/publish.yml"), name),
                () -> assertTrue(text.contains("secrets: inherit"),
                        name + " must hand its secrets to publish.yml, or Central sees no credentials"));
    }

    @Test
    @DisplayName("publish.yml builds every platform before it publishes anything")
    void publishBuildsEveryPlatform() {
        var text = Repository.workflow("publish.yml");
        assertAll(
                () -> assertTrue(text.contains("uses: ./.github/workflows/linux.yml")),
                () -> assertTrue(text.contains("uses: ./.github/workflows/macos.yml")),
                () -> assertTrue(text.contains("uses: ./.github/workflows/windows.yml")),
                () -> assertTrue(text.contains("needs: [linux, windows, macos]")),
                () -> assertTrue(text.contains("publishToMavenCentral")));
    }

    @Test
    @DisplayName("exactly one workflow publishes to Maven Central")
    void oneCentralPublisher() {
        for (var name : new String[]{"linux.yml", "macos.yml", "windows.yml", "showcase.yml",
                "snapshot.yml", "release.yml", "nightly.yml", "codeql.yml", "qodana.yml"}) {
            assertFalse(Repository.workflow(name).contains("publishToMavenCentral"),
                    name + " publishes to Central; only publish.yml may");
        }
    }

    @Test
    @DisplayName("the showcase is a release artifact: built on a tag or by hand, attached to the release")
    void showcaseIsAReleaseArtifact() {
        var text = Repository.workflow("showcase.yml");
        assertAll(
                () -> assertTrue(text.contains("tags: ['v*']"), "a v* tag builds it"),
                () -> assertTrue(text.contains("workflow_dispatch:"), "and so does a manual run"),
                () -> assertFalse(text.contains("branches:"), "a push to a branch does not"),
                () -> assertFalse(text.contains("pull_request:"), "nor does a pull request"),
                () -> assertTrue(text.contains("gh release upload"), "the binaries go on the GitHub Release"),
                () -> assertTrue(text.contains("startsWith(github.ref, 'refs/tags/v')"),
                        "and only a tag has a release to attach them to"));
    }

    @Test
    @DisplayName("nothing goes to GitHub Packages, and no jlink image is built")
    void noPackagesAndNoJlink() {
        var showcase = Repository.workflow("showcase.yml");
        var example = Repository.read("example/build.gradle");
        assertAll(
                () -> assertFalse(showcase.contains("packages: write"), "showcase.yml asks to write packages"),
                () -> assertFalse(showcase.contains("maven.pkg.github.com")),
                () -> assertFalse(showcase.contains("showcaseImage") || showcase.contains("jlinkImage")),
                () -> assertFalse(example.contains("maven-publish"), "the example applies maven-publish"),
                () -> assertFalse(example.contains("maven.pkg.github.com")),
                () -> assertFalse(example.contains("jlinkImage")));
    }

    @Test
    @DisplayName("example.yml stays retired: the Linux verify leg runs the example's tests instead")
    void exampleIsFolded() {
        assertAll(
                () -> assertFalse(Repository.exists(".github/workflows/example.yml"),
                        "example.yml is back; ADR-0335 folded it into showcase.yml, ADR-0340 into linux.yml"),
                () -> assertTrue(Repository.workflow("linux.yml").contains(":example:build"),
                        "linux.yml's verify leg must run the example's tests against the library"),
                () -> assertFalse(Repository.workflow("showcase.yml").contains(":example:build"),
                        "showcase.yml runs only on a tag now, so the example's tests cannot live there"));
    }
}
