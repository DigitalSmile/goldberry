package dev.goldberry.build.publish;

import dev.goldberry.build.repository.Repository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CentralPom}: no POM goes out without what Central refuses a release
 * without, and every published project has a description for it to carry.
 */
@DisplayName("the POM Central validates")
class CentralPomTest {

    private static final String URL = "https://goldberry.dev";

    /** A `description = '...'` at the top level of a build script, not a task's. */
    private static final Pattern DESCRIPTION = Pattern.compile("(?m)^description = '([^']+)'$");

    private static final String CONVENTION = "build-logic/src/main/groovy/goldberry.publish.gradle";

    static List<String> publishedProjects() {
        return PublishedModules.projectNames();
    }

    @Test
    @DisplayName("a POM with a name, a description and a URL passes")
    void complete() {
        var pom = new CentralPom("goldberry-core", "Goldberry core", "widgets, style, layout", URL);
        assertEquals(List.of(), pom.missing());
        assertDoesNotThrow(pom::require);
    }

    @Test
    @DisplayName("a POM with no description is refused, naming the artifact and the element")
    void noDescription() {
        var refusal = assertThrows(IllegalStateException.class,
                () -> new CentralPom("goldberry-widgets", "Goldberry widgets", null, URL).require());
        assertAll(
                () -> assertTrue(refusal.getMessage().startsWith("goldberry-widgets's POM has no description."),
                        refusal.getMessage()),
                () -> assertTrue(refusal.getMessage().contains("Maven Central"), refusal.getMessage()));
    }

    @Test
    @DisplayName("blank is as missing as absent")
    void blank() {
        assertEquals(List.of("description"), new CentralPom("goldberry", "Goldberry", "  ", URL).missing());
    }

    @Test
    @DisplayName("everything missing is named, in the order the POM writes it")
    void everythingMissing() {
        assertEquals(List.of("name", "description", "url"), new CentralPom("goldberry-bom", null, "", null).missing());
    }

    @Test
    @DisplayName("goldberry.publish reads the description late and checks every POM before any uploads")
    void theConventionChecks() {
        var convention = Repository.read(CONVENTION);
        assertAll(
                () -> assertTrue(convention.contains("description = provider { project.description }"),
                        "the description must be read when the POM is written, after the build script has set it"),
                () -> assertFalse(convention.contains("description = project.description"),
                        "an eager read runs before the build script's description line, and publishes null"),
                () -> assertTrue(convention.contains("new CentralPom("), "the check is not called"),
                () -> assertTrue(convention.contains(").require()"), "the check is built and not run"));
        var whenReady = convention.substring(convention.indexOf("gradle.taskGraph.whenReady"));
        assertTrue(whenReady.contains("new CentralPom("),
                "the check must run when the task graph is ready, before the first module uploads");
    }

    @ParameterizedTest(name = ":{0}")
    @MethodSource("publishedProjects")
    @DisplayName("every published project says what it is")
    void everyProjectHasADescription(String project) {
        var script = Repository.read(project + "/build.gradle");
        var description = DESCRIPTION.matcher(script);
        assertTrue(description.find(), ":" + project + " publishes a POM and sets no description");
        assertTrue(description.group(1).startsWith("Goldberry"), description.group(1));
    }
}
