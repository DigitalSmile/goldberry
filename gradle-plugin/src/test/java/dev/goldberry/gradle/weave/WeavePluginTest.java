package dev.goldberry.gradle.weave;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.JavaExec;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// The plugin applied to a project of an application's: what it registers,
/// what it hangs off `classes`, and which weaver it resolves.
@DisplayName("the weave plugin")
class WeavePluginTest {

    private static Project project(File directory) {
        var project = ProjectBuilder.builder().withProjectDir(directory).build();
        project.getPluginManager().apply("dev.goldberry.weave");
        return project;
    }

    private static boolean dependsOn(Task task, String name) {
        return task.getTaskDependencies().getDependencies(task).stream()
                .anyMatch(dependency -> dependency.getName().equals(name));
    }

    @Test
    @DisplayName("registers both halves for both source sets, and runs the weaver's main class")
    void registers(@TempDir File directory) {
        var project = project(directory);
        for (var name : List.of("weaveCatalog", "weaveTestCatalog", "weaveModels", "weaveTestModels")) {
            var task = (JavaExec) project.getTasks().getByName(name);
            assertEquals(WeavePlugin.MAIN_CLASS, task.getMainClass().get(), name);
            assertEquals("build", task.getGroup());
        }
    }

    @Test
    @DisplayName("declares the catalog before classes, and weaves models only for a native image")
    void lifecycle(@TempDir File directory) {
        var project = project(directory);
        var classes = project.getTasks().getByName("classes");
        assertTrue(dependsOn(classes, "weaveCatalog"));
        assertFalse(dependsOn(classes, "weaveModels"), "a jar binds at run time");
        assertTrue(dependsOn(project.getTasks().getByName("testClasses"), "weaveTestCatalog"));

        var image = project(new File(directory, "image"));
        image.getExtensions().getByType(GoldberryWeaveExtension.class).getNativeImage().set(true);
        assertTrue(dependsOn(image.getTasks().getByName("classes"), "weaveModels"));
    }

    @Test
    @DisplayName("resolves the weaver released with it, unless the build names another")
    void weaverVersion(@TempDir File directory) {
        var project = project(directory);
        var configuration = project.getConfigurations().getByName(WeavePlugin.CONFIGURATION);
        var extension = project.getExtensions().getByType(GoldberryWeaveExtension.class);
        assertEquals(WeavePlugin.pluginVersion(), extension.getWeaverVersion().get());
        extension.getWeaverVersion().set("2026.9");
        var dependency = configuration.getIncoming().getDependencies().stream().findFirst().orElseThrow();
        assertEquals("dev.goldberry", dependency.getGroup());
        assertEquals("goldberry-weaver", dependency.getName());
        assertEquals("2026.9", dependency.getVersion());
    }
}
