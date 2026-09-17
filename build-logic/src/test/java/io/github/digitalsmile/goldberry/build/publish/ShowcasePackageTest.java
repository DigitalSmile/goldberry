package io.github.digitalsmile.goldberry.build.publish;

import io.github.digitalsmile.goldberry.build.publish.ShowcasePackage.Archive;
import io.github.digitalsmile.goldberry.build.publish.ShowcasePackage.Kind;
import io.github.digitalsmile.goldberry.build.repository.Repository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ShowcasePackage")
class ShowcasePackageTest {

    @Nested
    @DisplayName("the runtime image")
    class RuntimeImage {

        @Test
        @DisplayName("is tarred on unix and zipped on Windows -- it is a directory")
        void archives() {
            assertAll(
                    () -> assertEquals(Archive.TAR_GZ, new ShowcasePackage(Kind.RUNTIME_IMAGE, "linux-x64").archive()),
                    () -> assertEquals(Archive.TAR_GZ, new ShowcasePackage(Kind.RUNTIME_IMAGE, "macos-aarch64").archive()),
                    () -> assertEquals(Archive.ZIP, new ShowcasePackage(Kind.RUNTIME_IMAGE, "windows-x64").archive()));
        }

        @Test
        @DisplayName("names the files the way the workflow writes them")
        void fileNames() {
            assertEquals(
                    List.of(
                            "goldberry-showcase-linux-x64.tar.gz",
                            "goldberry-showcase-macos-aarch64.tar.gz",
                            "goldberry-showcase-windows-x64.zip"),
                    ShowcasePackage.all(Kind.RUNTIME_IMAGE).stream().map(ShowcasePackage::fileName).toList());
        }
    }

    @Nested
    @DisplayName("the native image")
    class NativeImage {

        @Test
        @DisplayName("is tarred on unix and travels as its own .exe on Windows -- it is one file")
        void archives() {
            assertAll(
                    () -> assertEquals(Archive.TAR_GZ, new ShowcasePackage(Kind.NATIVE_IMAGE, "linux-x64").archive()),
                    () -> assertEquals(Archive.TAR_GZ, new ShowcasePackage(Kind.NATIVE_IMAGE, "macos-aarch64").archive()),
                    () -> assertEquals(Archive.EXE, new ShowcasePackage(Kind.NATIVE_IMAGE, "windows-x64").archive()));
        }

        @Test
        @DisplayName("names the files the way the workflow writes them")
        void fileNames() {
            assertEquals(
                    List.of(
                            "goldberry-showcase-native-linux-x64.tar.gz",
                            "goldberry-showcase-native-macos-aarch64.tar.gz",
                            "goldberry-showcase-native-windows-x64.exe"),
                    ShowcasePackage.all(Kind.NATIVE_IMAGE).stream().map(ShowcasePackage::fileName).toList());
        }
    }

    @Test
    @DisplayName("gives each kind its own artifact, publication and property")
    void kindsAreDistinct() {
        assertAll(
                () -> assertNotEquals(Kind.RUNTIME_IMAGE.artifactId(), Kind.NATIVE_IMAGE.artifactId()),
                () -> assertNotEquals(Kind.RUNTIME_IMAGE.directoryProperty(), Kind.NATIVE_IMAGE.directoryProperty()),
                () -> assertEquals("publishShowcasePublicationToGithubPackagesRepository",
                        Kind.RUNTIME_IMAGE.publishTask()),
                () -> assertEquals("publishShowcaseNativePublicationToGithubPackagesRepository",
                        Kind.NATIVE_IMAGE.publishTask()));
    }

    @Test
    @DisplayName("refuses a target no runner builds")
    void refusesUnknownTargets() {
        assertThrows(IllegalArgumentException.class, () -> new ShowcasePackage(Kind.NATIVE_IMAGE, "linux-aarch64"));
    }

    @Nested
    @DisplayName("agrees with showcase.yml")
    class AgreesWithTheWorkflow {

        private final String workflow = Repository.workflow("showcase.yml");

        @Test
        @DisplayName("builds on exactly the targets")
        void matrix() {
            var targets = Pattern.compile("- target: ([a-z0-9-]+)").matcher(workflow).results()
                    .map(match -> match.group(1))
                    .collect(LinkedHashSet<String>::new, LinkedHashSet::add, LinkedHashSet::addAll);
            assertEquals(ShowcasePackage.TARGETS, List.copyOf(targets), "showcase.yml builds " + targets);
        }

        @Test
        @DisplayName("writes the file names this record expects")
        void fileNames() {
            assertAll(
                    () -> assertTrue(workflow.contains("\"$GITHUB_WORKSPACE/goldberry-showcase-${{ matrix.target }}.tar.gz\"")),
                    () -> assertTrue(workflow.contains("\"$GITHUB_WORKSPACE/goldberry-showcase-${{ matrix.target }}.zip\"")),
                    () -> assertTrue(workflow.contains("\"$GITHUB_WORKSPACE/goldberry-showcase-native-${{ matrix.target }}.tar.gz\"")),
                    () -> assertTrue(workflow.contains("\"$GITHUB_WORKSPACE/goldberry-showcase-native-${{ matrix.target }}.exe\"")));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Kind.class)
        @DisplayName("publishes each kind with its task and property")
        void publishes(Kind kind) {
            assertAll(
                    () -> assertTrue(workflow.contains("task: " + kind.publishTask()), kind.publishTask()),
                    () -> assertTrue(workflow.contains("property: " + kind.directoryProperty()), kind.directoryProperty()),
                    () -> assertTrue(workflow.contains("artifact: " + kind.artifactId()), kind.artifactId()));
        }

        @Test
        @DisplayName("keeps the native uploads out of the runtime job's download pattern")
        void downloadPatternsDoNotOverlap() {
            assertAll(
                    () -> assertTrue(workflow.contains("name: showcase-native-${{ matrix.target }}")),
                    () -> assertFalse(workflow.contains("name: goldberry-showcase-native-")));
        }
    }

    @Test
    @DisplayName("the example's build script declares the publications and does not switch uploads off")
    void exampleCanPublish() {
        var script = Repository.read("example/build.gradle");
        assertAll(
                () -> assertTrue(script.contains("ShowcasePackage.all(kind)"),
                        "example/build.gradle no longer publishes from this record"),
                () -> assertFalse(script.contains("tasks.withType(PublishToMavenRepository).configureEach { enabled = false }"),
                        "a disabled upload task reports SKIPPED and the run stays green (ADR-0335)"));
    }
}
