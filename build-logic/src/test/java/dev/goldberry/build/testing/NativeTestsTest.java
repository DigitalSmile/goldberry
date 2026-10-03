package dev.goldberry.build.testing;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.goldberry.build.benchmark.BenchmarkLane;
import dev.goldberry.build.natives.NativeTarget;

@DisplayName("NativeTests")
class NativeTestsTest {

    @TempDir
    Path directory;

    private Project project;
    private NativeTests nativeTests;

    @BeforeEach
    void project() {
        project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build();
        project.getPluginManager().apply("goldberry.benchmarks");
        nativeTests = project.getExtensions().create(
                NativeTests.EXTENSION,
                NativeTests.class,
                NativeTarget.byId("macos-aarch64"),
                project.getExtensions().getByType(SourceSetContainer.class));
    }

    private static List<String> dependencies(Task task) {
        return task.getDependsOn().stream().map(String::valueOf).toList();
    }

    @Test
    @DisplayName("grants native access, names the library this machine builds, and waits for its build")
    void wiresATestJvm() {
        var test = project.getTasks().named("test", org.gradle.api.tasks.testing.Test.class).get();
        nativeTests.wire(test);
        // Under the project's directory as Gradle holds it, which is canonical:
        // on macOS the `@TempDir` is a `/var/...` symlink to `/private/var/...`.
        var expected = project.getProjectDir()
                .toPath()
                .resolve("natives/build/native/macos-aarch64/install/lib/libgoldberry.dylib");
        assertAll(
                () -> assertTrue(test.getJvmArgs().contains(NativeTests.NATIVE_ACCESS), test.getJvmArgs()::toString),
                () -> assertEquals(
                        new File(expected.toString()).getAbsolutePath(),
                        test.getSystemProperties().get(NativeTests.LIBRARY_PROPERTY)),
                () -> assertTrue(dependencies(test).contains(NativeTests.NATIVE_BUILD), dependencies(test)::toString),
                () -> assertTrue(nativeTests.buildsLibrary()));
    }

    @Test
    @DisplayName("adds a GPU lane on the first thread that check runs, and takes the gpu tests out of test")
    void gpuLane() {
        var lane = nativeTests.gpuTests("dev.goldberry.example").get();
        var check = project.getTasks().getByName("check");
        assertAll(
                () -> assertEquals(NativeTests.GPU_LAUNCHER, lane.getMainClass().get()),
                () -> assertTrue(lane.getJvmArgs().contains(NativeTests.FIRST_THREAD), lane.getJvmArgs()::toString),
                () -> assertTrue(lane.getJvmArgs().contains(NativeTests.NATIVE_ACCESS)),
                () -> assertTrue(
                        check.getDependsOn().stream().anyMatch(dependency -> String.valueOf(dependency).contains("gpuTest")),
                        "check does not run the GPU lane"),
                () -> assertTrue(
                        lane.getArgumentProviders().stream()
                                .anyMatch(provider -> String.valueOf(provider.asArguments()).contains("dev.goldberry.example")),
                        "the lane is not told its package"));
    }

    @Test
    @DisplayName("registers a probe in the benchmark group, on the benchmark classpath, never up to date")
    void probe() {
        var probe = nativeTests.probe("framesProbe", "dev.goldberry.FramesProbe", "Measures frames.").get();
        var benchmarks = project.getExtensions().getByType(SourceSetContainer.class).getByName(BenchmarkLane.SOURCE_SET);
        assertAll(
                () -> assertEquals(BenchmarkLane.GROUP, probe.getGroup()),
                () -> assertEquals("dev.goldberry.FramesProbe", probe.getMainClass().get()),
                () -> assertEquals(benchmarks.getRuntimeClasspath().getFiles(), probe.getClasspath().getFiles()),
                () -> assertFalse(project.getTasks().getByName("check").getDependsOn().stream()
                        .anyMatch(dependency -> String.valueOf(dependency).contains("framesProbe"))));
    }

    @Test
    @DisplayName("gives the benchmark task its own source set, and check only compiles it")
    void benchmarkLane() {
        var benchmark = project.getTasks().named(BenchmarkLane.TASK, org.gradle.api.tasks.testing.Test.class).get();
        var benchmarks = project.getExtensions().getByType(SourceSetContainer.class).getByName(BenchmarkLane.SOURCE_SET);
        var check = dependencies(project.getTasks().getByName("check"));
        assertAll(
                () -> assertEquals(benchmarks.getOutput().getClassesDirs().getFiles(),
                        benchmark.getTestClassesDirs().getFiles()),
                () -> assertTrue(check.stream().anyMatch(name -> name.contains(benchmarks.getClassesTaskName())),
                        "check does not compile the benchmarks: " + check),
                () -> assertFalse(check.stream().anyMatch(name -> name.endsWith("task '" + BenchmarkLane.TASK + "'")
                        || name.equals(BenchmarkLane.TASK)), "check runs the benchmarks: " + check));
    }

    @Test
    @DisplayName("runs JavaExec and Test the same way")
    void javaExecIsWiredToo() {
        var exec = project.getTasks().register("run", JavaExec.class).get();
        nativeTests.wire(exec);
        assertTrue(exec.getSystemProperties().containsKey(NativeTests.LIBRARY_PROPERTY));
    }
}
