package dev.goldberry.build.publish;

import dev.goldberry.build.publish.PublishedModule.Bom;
import dev.goldberry.build.publish.PublishedModule.GradlePlugin;
import dev.goldberry.build.publish.PublishedModule.Inclusion;
import dev.goldberry.build.publish.PublishedModule.Library;
import dev.goldberry.build.publish.PublishedModule.Umbrella;
import dev.goldberry.build.repository.Repository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PublishedModules} against the build scripts. The plugin refuses a
 * module that is not on the list; this refuses a module on the list that forgot
 * to apply the plugin, which would otherwise leave a hole in Maven Central that
 * nobody notices until a consumer's resolution fails.
 */
@DisplayName("PublishedModules")
class PublishedModulesTest {

    private static final Pattern INCLUDED = Pattern.compile("'\\:([a-z0-9-]+)'");

    private static final String PLUGIN = "id 'goldberry.publish'";

    static List<String> includedModules() {
        var settings = Repository.read("settings.gradle");
        var include = settings.substring(settings.indexOf("include "));
        return INCLUDED.matcher(include).results().map(match -> match.group(1)).toList();
    }

    @Test
    @DisplayName("names only projects the build includes")
    void namesIncludedModules() {
        assertTrue(includedModules().containsAll(PublishedModules.projectNames()),
                "settings.gradle includes " + includedModules());
    }

    @ParameterizedTest(name = ":{0}")
    @MethodSource("includedModules")
    @DisplayName("a project applies goldberry.publish exactly when it is on the list")
    void buildScriptsAgree(String module) {
        var applies = Repository.read(module + "/build.gradle").contains(PLUGIN);
        assertEquals(PublishedModules.isPublished(module), applies,
                ":" + module + (applies ? " applies goldberry.publish but is not published"
                        : " is published but does not apply goldberry.publish"));
    }

    @Test
    @DisplayName("gives every artifact its own id")
    void artifactIdsAreUnique() {
        var ids = PublishedModules.ALL.stream().map(PublishedModule::artifactId).toList();
        assertEquals(ids.size(), new HashSet<>(ids).size(), "duplicate artifact ids in " + ids);
    }

    @Test
    @DisplayName("prefixes a library's artifact id, publishes the weaver and its plugin, and refuses this build's own tools")
    void artifactIds() {
        assertAll(
                () -> assertEquals("goldberry-core", PublishedModules.artifactId("core")),
                () -> assertEquals("goldberry-natives", PublishedModules.artifactId("natives")),
                () -> assertEquals("goldberry-bom", PublishedModules.artifactId("bom")),
                () -> assertEquals("goldberry", PublishedModules.artifactId("toolkit")),
                () -> assertFalse(PublishedModules.isPublished("assets")),
                () -> assertEquals("goldberry-weaver", PublishedModules.artifactId("weaver")),
                () -> assertEquals("goldberry-gradle-plugin", PublishedModules.artifactId("gradle-plugin")),
                () -> assertThrows(IllegalArgumentException.class, () -> PublishedModules.require("example")));
    }

    @Test
    @DisplayName("names each kind for its POM")
    void displayNames() {
        assertAll(
                () -> assertEquals("Goldberry widgets", PublishedModules.displayName(new Library("widgets", Inclusion.REQUIRED))),
                () -> assertEquals("Goldberry BOM", PublishedModules.displayName(new Bom())),
                () -> assertEquals("Goldberry", PublishedModules.displayName(new Umbrella())));
    }

    @Nested
    @DisplayName("the umbrella")
    class TheUmbrella {

        @Test
        @DisplayName("requires the toolkit itself")
        void requiresTheToolkit() {
            assertEquals(List.of("common", "natives", "core", "widgets"),
                    PublishedModules.libraries(Inclusion.REQUIRED).stream().map(Library::project).toList());
        }

        @Test
        @DisplayName("lists the content modules, the emoji face, the GPU path, media, the microphone, the camera and spelling as optional")
        void listsOptionalModules() {
            // `emoji` is optional for a reason none of the others share: the
            // face was CC BY-SA, and an application that added the artifact took
            // on an attribution obligation with it.
            assertEquals(List.of("html", "emoji", "gpu", "media", "mic", "camera", "spell"),
                    PublishedModules.libraries(Inclusion.OPTIONAL).stream().map(Library::project).toList());
        }

        @Test
        @DisplayName("is found by its project")
        void isFound() {
            assertInstanceOf(Umbrella.class, PublishedModules.require("toolkit"));
            assertInstanceOf(Bom.class, PublishedModules.require("bom"));
        }

        @Test
        @DisplayName("every library is required, optional or a tool -- the BOM pins all three")
        void librariesPartition() {
            assertEquals(PublishedModules.libraries().size(),
                    PublishedModules.libraries(Inclusion.REQUIRED).size()
                            + PublishedModules.libraries(Inclusion.OPTIONAL).size()
                            + PublishedModules.libraries(Inclusion.TOOL).size());
        }

        @Test
        @DisplayName("leaves the weaver out: a build tool reaches no application's run time")
        void leavesTheToolsOut() {
            assertEquals(List.of("weaver"),
                    PublishedModules.libraries(Inclusion.TOOL).stream().map(Library::project).toList());
        }

        @Test
        @DisplayName("finds the Gradle plugin by its project, with the id a build applies")
        void findsThePlugin() {
            var plugin = assertInstanceOf(GradlePlugin.class, PublishedModules.require("gradle-plugin"));
            assertEquals("dev.goldberry.weave", plugin.pluginId());
            assertEquals("Goldberry Gradle plugin", PublishedModules.displayName(plugin));
        }
    }
}
