package dev.goldberry.build.assets;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.gradle.api.Project;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("AssetTool")
class AssetToolTest {

    @TempDir
    Path directory;

    private Project project;
    private AssetTool tool;

    /// The project's directory as Gradle holds it: canonical, so on macOS
    /// `/private/var/...` where the `@TempDir` is the `/var/...` symlink.
    private Path root;

    @BeforeEach
    void project() {
        project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build();
        root = project.getProjectDir().toPath();
        project.getPluginManager().apply("java");
        tool = project.getExtensions().create(
                AssetTool.EXTENSION,
                AssetTool.class,
                project.getConfigurations().create(AssetTool.CONFIGURATION),
                project.getExtensions().getByType(SourceSetContainer.class));
    }

    private JavaExec task(String name) {
        return project.getTasks().named(name, JavaExec.class).get();
    }

    @Test
    @DisplayName("bundles the entries asked for under the package asked for, and vendors their licences")
    void bundle() {
        tool.bundle("noto-emoji", "dev/goldberry/emoji");
        var prepare = task("prepareAssets");
        var licences = task("vendorLicences");
        var resources = project.getExtensions().getByType(SourceSetContainer.class)
                .getByName("main").getResources().getSrcDirs();
        assertAll(
                () -> assertEquals(AssetTool.PREPARE, prepare.getMainClass().get()),
                () -> assertTrue(prepare.getArgs().containsAll(List.of("--only=noto-emoji", "--root=dev/goldberry/emoji")),
                        prepare.getArgs()::toString),
                () -> assertEquals(root.resolve(".gradle/assets").toString(), prepare.getArgs().getFirst()),
                () -> assertTrue(resources.stream().anyMatch(dir -> dir.toPath().endsWith("build/generated/assets")),
                        "the prepared assets are not resources: " + resources),
                () -> assertTrue(licences.getArgs().contains(root.resolve("licenses").toString()),
                        licences.getArgs()::toString),
                () -> assertEquals(5, licences.getArgs().size(), licences.getArgs()::toString));
    }

    @Test
    @DisplayName("leaves --root out when the tool's own default is wanted")
    void defaultRoot() {
        tool.bundle("inter,jetbrains-mono,lucide", null);
        var arguments = task("prepareAssets").getArgs();
        assertAll(
                () -> assertEquals("--only=inter,jetbrains-mono,lucide", arguments.getLast()),
                () -> assertTrue(arguments.stream().noneMatch(argument -> argument.startsWith("--root"))));
    }

    @Test
    @DisplayName("compiles the showcase's tables into the package directory given")
    void catalogs() {
        tool.catalogs("dev/goldberry/example/catalog");
        var prepare = task("prepareCatalogs");
        assertAll(
                () -> assertEquals(AssetTool.CATALOGS, prepare.getMainClass().get()),
                () -> assertTrue(
                        Path.of(prepare.getArgs().getLast()).endsWith("generated/catalogs/dev/goldberry/example/catalog"),
                        prepare.getArgs()::toString));
    }
}
