package dev.goldberry.build.benchmark;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.build.repository.Repository;

/**
 * The benchmark lane is a source set, a task and a workflow, and these are the
 * guards that keep each of them the only place a measurement runs.
 */
@DisplayName("The benchmark lane")
class BenchmarkLaneTest {

    private static final Pattern GRADLE_BENCHMARK = Pattern.compile("gradlew[^\\n]*\\s(:\\w+:)?(benchmark|jmh)\\b");

    private static String simpleName(String path) {
        var file = Path.of(path).getFileName().toString();
        return file.substring(0, file.length() - ".java".length());
    }

    private static List<String> measurements() {
        return Repository.files("*/src/benchmark/java/**.java").stream()
                .map(BenchmarkLaneTest::simpleName)
                .filter(BenchmarkLane::admits)
                .toList();
    }

    @Test
    @DisplayName("admits a Benchmark and a Probe by name, and nothing else")
    void names() {
        assertAll(
                () -> assertTrue(BenchmarkLane.admits("FrameBenchmark")),
                () -> assertTrue(BenchmarkLane.admits("GpuPresentProbe")),
                () -> assertFalse(BenchmarkLane.admits("Notes")),
                () -> assertFalse(BenchmarkLane.admits("FrameBenchmarkTest")));
    }

    @Test
    @DisplayName("has measurements in it, so the guards below are guarding something")
    void isNotEmpty() {
        assertTrue(measurements().size() >= 20, "only " + measurements() + " under src/benchmark");
    }

    @Test
    @DisplayName("is the only place a benchmark lives: none is in a test tree, and none is tagged")
    void noBenchmarkInATestTree() {
        var inTests = Repository.files("*/src/test/java/**.java").stream()
                .filter(path -> simpleName(path).endsWith("Benchmark"))
                .toList();
        var tagged = Repository.files("*/src/*/java/**.java").stream()
                .filter(path -> Repository.read(path).contains("@Tag(\"benchmark\")"))
                .toList();
        assertAll(
                () -> assertTrue(inTests.isEmpty(), "move these to src/benchmark/java: " + inTests),
                () -> assertTrue(tagged.isEmpty(), "a source set says what is a benchmark now, not a tag: " + tagged));
    }

    /**
     * A probe is whatever a {@code nativeTests.probe} task runs. A test tree may
     * have a {@code main} of its own -- a child JVM a test starts is one -- and
     * that is not a measurement, so the name alone does not decide it.
     */
    @Test
    @DisplayName("runs every probe from the benchmark source set of the module that registers it")
    void probesLiveInTheLane() {
        var registration = Pattern.compile("nativeTests\\.probe\\(\\s*'(\\w+)',\\s*'([\\w.]+)'");
        var found = new ArrayList<String>();
        var misplaced = new ArrayList<String>();
        for (var script : Repository.files("*/build.gradle")) {
            var module = script.substring(0, script.indexOf('/'));
            var matcher = registration.matcher(Repository.read(script));
            while (matcher.find()) {
                var source = module + "/src/benchmark/java/" + matcher.group(2).replace('.', '/') + ".java";
                found.add(matcher.group(1));
                if (!Repository.exists(source)) {
                    misplaced.add(matcher.group(1) + " runs " + matcher.group(2) + ", which is not " + source);
                }
            }
        }
        assertAll(
                () -> assertTrue(found.size() >= 4, "only " + found + " probe tasks registered"),
                () -> assertTrue(misplaced.isEmpty(), String.join("\n", misplaced)));
    }

    @Test
    @DisplayName("is inventoried: every benchmark and probe is named in docs/testing.md, so somebody re-runs it")
    void everyMeasurementIsInventoried() {
        var inventory = Repository.read("docs/testing.md");
        var missing = measurements().stream()
                .filter(name -> !inventory.contains("`" + name + "`"))
                .toList();
        assertTrue(missing.isEmpty(), "docs/testing.md §1.5 does not list " + missing);
    }

    @Test
    @DisplayName("runs in its own workflow, which nightly calls and a person can start, and nowhere else")
    void runsInItsOwnWorkflow() {
        var lane = Repository.workflow(BenchmarkLane.WORKFLOW);
        var nightly = Repository.workflow("nightly.yml");
        var elsewhere = Repository.workflowNames().stream()
                .filter(name -> !name.equals(BenchmarkLane.WORKFLOW))
                .filter(name -> GRADLE_BENCHMARK.matcher(Repository.workflow(name)).find())
                .toList();
        assertAll(
                () -> assertTrue(lane.contains("workflow_dispatch:"), "a person cannot start the lane"),
                () -> assertTrue(lane.contains("workflow_call:"), "nightly cannot call the lane"),
                () -> assertTrue(GRADLE_BENCHMARK.matcher(lane).find(), "the lane runs no benchmark"),
                () -> assertTrue(nightly.contains("uses: ./.github/workflows/" + BenchmarkLane.WORKFLOW),
                        "nightly does not call the lane"),
                () -> assertTrue(elsewhere.isEmpty(), "benchmarks run outside the lane in " + elsewhere));
    }
}
