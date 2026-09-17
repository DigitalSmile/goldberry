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
    @DisplayName("the showcase publishes once, from a push, with a token that can write packages")
    void showcasePublishes() {
        var text = Repository.workflow("showcase.yml");
        assertAll(
                () -> assertTrue(text.contains("packages: write")),
                () -> assertTrue(text.contains("github.event_name == 'push'")),
                () -> assertTrue(text.contains("publishShowcasePublicationToGithubPackagesRepository")),
                () -> assertTrue(text.contains("publishShowcaseNativePublicationToGithubPackagesRepository")));
    }

    @Test
    @DisplayName("example.yml stays retired: showcase.yml runs the example's tests instead")
    void exampleIsFolded() {
        assertAll(
                () -> assertFalse(Repository.exists(".github/workflows/example.yml"),
                        "example.yml is back; ADR-0335 folded it into showcase.yml"),
                () -> assertTrue(Repository.workflow("showcase.yml").contains(":example:build")));
    }
}
