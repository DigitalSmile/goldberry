package dev.goldberry.build.testing;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions;
import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.testretry.TestRetryTaskExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.build.benchmark.BenchmarkLane;
import dev.goldberry.build.repository.Repository;

/**
 * The wall-clock lane is a tag, a task and a step in every workflow that runs
 * tests, and these are the guards that keep a test that reads a clock out of
 * the parallel build.
 */
@DisplayName("The wall-clock lane")
class WallClockLaneTest {

    /**
     * A Gradle command that pulls the lane in through {@code check}: {@code build}
     * or {@code check}, of the whole build or of one module. A {@code :core:test}
     * on its own does not, since the tag is out of {@code test}.
     */
    private static final Pattern PULLS_THE_LANE_IN =
            Pattern.compile("gradlew[^\\n]*\\s(build|check|:\\w+:check|:\\w+:build)\\b");

    @TempDir
    Path directory;

    private Project project;

    @BeforeEach
    void project() {
        project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build();
        project.getPluginManager().apply("goldberry.benchmarks");
        project.getPluginManager().apply("jacoco");
        project.getPluginManager().apply("org.gradle.test-retry");
        var service = project.getGradle()
                .getSharedServices()
                .registerIfAbsent(BenchmarkLane.SERVICE, BenchmarkLane.OneAtATime.class, spec -> {});
        WallClockLane.apply(project, service);
    }

    private Test task(String name) {
        return project.getTasks().named(name, Test.class).get();
    }

    private static JUnitPlatformOptions junit(Test task) {
        return (JUnitPlatformOptions) task.getOptions();
    }

    private static List<String> dependencies(Task task) {
        return task.getDependsOn().stream().map(String::valueOf).toList();
    }

    /** Whether {@code task} depends on {@code other}, with providers resolved. */
    private static boolean dependsOn(Task task, Task other) {
        return task.getTaskDependencies().getDependencies(task).contains(other);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("takes the tag out of test and the benchmarks keep theirs")
    void testExcludesTheTag() {
        assertAll(
                () -> assertTrue(junit(task("test")).getExcludeTags().contains(WallClockLane.TAG)),
                () -> assertFalse(junit(task(BenchmarkLane.TASK)).getExcludeTags().contains(WallClockLane.TAG)),
                () -> assertTrue(WallClockLane.excludesTag("testWithoutGpu")),
                () -> assertFalse(WallClockLane.excludesTag(WallClockLane.TASK)));
    }

    @org.junit.jupiter.api.Test
    @DisplayName("runs the tag alone, in the one slot, with retries, and check waits for it")
    void theLane() {
        var lane = task(WallClockLane.TASK);
        var retry = lane.getExtensions().getByType(TestRetryTaskExtension.class);
        assertAll(
                () -> assertEquals(List.of(WallClockLane.TAG), List.copyOf(junit(lane).getIncludeTags())),
                () -> assertTrue(junit(lane).getExcludeTags().isEmpty(), "the lane excludes nothing"),
                () -> assertEquals(WallClockLane.RETRIES, retry.getMaxRetries().get()),
                () -> assertEquals(WallClockLane.MAX_FAILURES, retry.getMaxFailures().get()),
                () -> assertFalse(retry.getFailOnPassedAfterRetry().get()),
                () -> assertFalse(lane.getFilter().isFailOnNoMatchingTests()),
                () -> assertTrue(
                        dependsOn(project.getTasks().getByName("check"), lane),
                        () -> "check depends on " + dependencies(project.getTasks().getByName("check"))),
                () -> assertTrue(
                        dependsOn(project.getTasks().getByName("jacocoTestReport"), lane),
                        "the coverage report reads the lane's exec file"));
    }

    @org.junit.jupiter.api.Test
    @DisplayName("every other test task has no retries")
    void onlyTheLaneRetries() {
        var test = task("test").getExtensions().getByType(TestRetryTaskExtension.class);
        assertEquals(0, test.getMaxRetries().getOrElse(0));
    }

    @org.junit.jupiter.api.Test
    @DisplayName("the annotation in :core's test fixtures names the same tag")
    void theAnnotationAgrees() {
        var source = Repository.read("core/src/testFixtures/java/dev/goldberry/junit/WallClock.java");
        assertTrue(
                source.contains("String TAG = \"" + WallClockLane.TAG + "\""),
                "@WallClock must tag \"" + WallClockLane.TAG + "\"");
    }

    /**
     * A workflow that runs a suite with {@code build} or {@code check} would run
     * the lane inside the parallel build, which is the thing the lane exists to
     * avoid; so every such line leaves it out, and a later step runs it alone.
     */
    @ParameterizedTest(name = "{0} runs the lane in a step of its own")
    @ValueSource(strings = {"linux.yml", "macos.yml", "windows.yml", "media.yml"})
    @DisplayName("the workflows that run tests run the lane after them, alone")
    void workflowsRunTheLaneAlone(String name) {
        var text = Repository.workflow(name);
        var suites = PULLS_THE_LANE_IN.matcher(text);
        var offending = new java.util.ArrayList<String>();
        var count = 0;
        while (suites.find()) {
            var command = commandAt(text, suites.start());
            // A comment that quotes the command is not the command.
            if (command.startsWith("#")) {
                continue;
            }
            count++;
            if (!command.contains("-x " + WallClockLane.TASK)) {
                offending.add(command.strip());
            }
        }
        var laneStep = text.indexOf("./gradlew " + WallClockLane.TASK) >= 0
                || text.indexOf(":" + WallClockLane.TASK) >= 0;
        var finalCount = count;
        assertAll(
                () -> assertTrue(finalCount > 0, name + " runs no suite at all; is the pattern stale?"),
                () -> assertTrue(offending.isEmpty(), name + " runs the lane inside the build: " + offending),
                () -> assertTrue(laneStep, name + " never runs " + WallClockLane.TASK));
    }

    /** The shell command at {@code offset}, with its backslash-continued lines joined. */
    private static String commandAt(String text, int offset) {
        var start = text.lastIndexOf('\n', offset) + 1;
        var command = new StringBuilder();
        var from = start;
        while (true) {
            var end = text.indexOf('\n', from);
            var line = text.substring(from, end < 0 ? text.length() : end);
            command.append(line.strip()).append(' ');
            if (end < 0 || !line.stripTrailing().endsWith("\\")) {
                return command.toString();
            }
            from = end + 1;
        }
    }
}
