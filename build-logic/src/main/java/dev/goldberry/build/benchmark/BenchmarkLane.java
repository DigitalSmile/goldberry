package dev.goldberry.build.benchmark;

import java.util.List;

import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

/**
 * The names the benchmark lane is built from, shared by the
 * {@code goldberry.benchmarks} plugin, the probes and the guards that hold the
 * repository to them.
 *
 * <p>A benchmark is a JUnit class under {@code src/benchmark/java} and nowhere
 * else. It prints what something costs and asserts almost nothing, so
 * {@code check} compiles it and never runs it; {@code ./gradlew benchmark} runs
 * every module's, one module at a time, and the {@code Benchmarks} workflow runs
 * that on a schedule or by hand.
 *
 * <p>Read more: <a href="https://goldberry.dev/docs/performance/measuring.html#the-benchmark-lane">The
 * benchmark lane</a>.
 */
public final class BenchmarkLane {

    /** The source set the benchmarks and probes live in: {@code src/benchmark/java}. */
    public static final String SOURCE_SET = "benchmark";

    /** The task that runs a module's benchmarks. */
    public static final String TASK = "benchmark";

    /** The task group the benchmarks and the probes are listed under. */
    public static final String GROUP = "benchmark";

    /** The shared service that lets one {@link #TASK} run at a time. */
    public static final String SERVICE = "goldberryBenchmarks";

    /** The workflow that runs the lane, nightly and on request. */
    public static final String WORKFLOW = "benchmarks.yml";

    /**
     * What a class in the source set is called: a JUnit benchmark ends in
     * {@code Benchmark}, and a {@code main} a probe task runs ends in {@code Probe}.
     */
    public static final List<String> CLASS_SUFFIXES = List.of("Benchmark", "Probe");

    private BenchmarkLane() {
    }

    /**
     * Whether a class name is one the lane admits.
     *
     * @param simpleName a top-level class's simple name
     * @return {@code true} for a {@code *Benchmark} or a {@code *Probe}
     */
    public static boolean admits(String simpleName) {
        return CLASS_SUFFIXES.stream().anyMatch(simpleName::endsWith);
    }

    /**
     * Held by every {@link #TASK} while it runs, with one usage allowed at a time.
     *
     * <p>A benchmark measures the machine as well as the code, and two of them
     * measured at once measure each other: with {@code org.gradle.parallel} on, a
     * frame measured in {@code :widgets} while {@code :core} rasterized beside it
     * read several times its own cost. One at a time is the only way two runs'
     * numbers can be compared.
     */
    public abstract static class OneAtATime implements BuildService<BuildServiceParameters.None> {
    }
}
