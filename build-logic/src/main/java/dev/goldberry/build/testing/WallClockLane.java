package dev.goldberry.build.testing;

import java.util.Set;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;
import org.gradle.testing.jacoco.tasks.JacocoReportBase;
import org.gradle.testretry.TestRetryTaskExtension;

import dev.goldberry.build.benchmark.BenchmarkLane;

/**
 * The lane for the tests that read a wall clock, and the names it is built from.
 *
 * <p>A test tagged {@link #TAG} compares a measured duration with a bound, or
 * waits a real delay out: the SDL audio sink draining at the device's rate, a
 * read timing out, a seek landing while a video thread runs. Such a test
 * measures the machine as well as the code, and under {@code ./gradlew build}
 * the machine is four suites, SpotBugs and javadoc running beside it: a
 * {@code sleep(1)} loop on a four-core runner managed five iterations in a
 * second and a half. So the tagged tests are left out of every other
 * {@code Test} task and run by {@link #TASK}, which holds the benchmark lane's
 * one slot, so nothing measured runs beside anything measured. The workflows
 * run it as a step of its own, after the build, so the JVM has the runner to
 * itself.
 *
 * <p>The lane is the one place a test is retried. A test that read the clock and
 * failed is run again, up to {@link #RETRIES} times, and a pass after a retry
 * is reported as a flaky test rather than hidden; a failure that persists fails
 * the build as any other. More than {@link #MAX_FAILURES} failures in one run
 * is a breakage, not a slow machine, and nothing is retried.
 *
 * <p>Read more: <a href="https://goldberry.dev/docs/contributing/testing.html#the-wall-clock-lane">The
 * wall-clock lane</a>.
 */
public final class WallClockLane {

    /** The JUnit tag of a test that reads a wall clock; {@code @WallClock} in {@code :core}'s test fixtures. */
    public static final String TAG = "wallclock";

    /** The task that runs a module's tagged tests, alone. */
    public static final String TASK = "wallClockTest";

    /** The task group, Gradle's own for a test task. */
    public static final String GROUP = "verification";

    /** How many times a failed test in the lane is run again. */
    public static final int RETRIES = 2;

    /** Past this many failures in one run, nothing is retried: the code is broken, not the machine. */
    public static final int MAX_FAILURES = 3;

    /** The test tasks the tag is not taken out of: the lane itself, and the benchmarks, which no tag runs. */
    private static final Set<String> NOT_FILTERED = Set.of(TASK, BenchmarkLane.TASK);

    private WallClockLane() {
    }

    /**
     * Whether a {@code Test} task of a module leaves the tagged tests out.
     *
     * @param taskName the task's name
     * @return {@code true} for every test task but the lane and the benchmarks
     */
    public static boolean excludesTag(String taskName) {
        return !NOT_FILTERED.contains(taskName);
    }

    /**
     * Wires the lane into a module: takes the tag out of every other test task,
     * registers {@link #TASK} over the test source set, hands it the single slot
     * and the retry policy, and makes {@code check} and the coverage tasks wait
     * for it.
     *
     * @param project    the module
     * @param oneAtATime the shared slot the benchmarks hold too
     * @return the lane's task
     */
    public static TaskProvider<Test> apply(Project project, Provider<BenchmarkLane.OneAtATime> oneAtATime) {
        var tasks = project.getTasks();
        tasks.withType(Test.class).configureEach(new Action<>() {
            @Override
            public void execute(Test task) {
                if (excludesTag(task.getName())) {
                    task.useJUnitPlatform(options -> options.excludeTags(TAG));
                }
            }
        });

        var sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        var test = sourceSets.getByName("test");
        var lane = tasks.register(TASK, Test.class, new Action<>() {
            @Override
            public void execute(Test task) {
                task.setGroup(GROUP);
                task.setDescription("Runs the tests that read a wall clock, one module at a time and nothing beside them.");
                task.setTestClassesDirs(test.getOutput().getClassesDirs());
                task.setClasspath(test.getRuntimeClasspath());
                task.useJUnitPlatform(options -> options.includeTags(TAG));
                task.usesService(oneAtATime);
                // A module with no tagged test is not a misconfiguration.
                task.getFilter().setFailOnNoMatchingTests(false);
                task.getFailOnNoDiscoveredTests().set(false);
                // After the module's own suite when both run, so the suite's
                // JVM is not the thing beside it.
                task.shouldRunAfter(tasks.named("test"));
                var retry = task.getExtensions().getByType(TestRetryTaskExtension.class);
                retry.getMaxRetries().set(RETRIES);
                retry.getMaxFailures().set(MAX_FAILURES);
                retry.getFailOnPassedAfterRetry().set(false);
            }
        });

        tasks.named("check", new Action<>() {
            @Override
            public void execute(org.gradle.api.Task check) {
                check.dependsOn(lane);
            }
        });
        // The lane's exec file is one of the files a report reads, so the floors
        // count the tests that moved out as they counted them before.
        tasks.withType(JacocoReportBase.class).configureEach(new Action<>() {
            @Override
            public void execute(JacocoReportBase report) {
                report.dependsOn(lane);
            }
        });
        return lane;
    }
}
