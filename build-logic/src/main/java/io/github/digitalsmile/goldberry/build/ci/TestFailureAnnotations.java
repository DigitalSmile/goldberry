package io.github.digitalsmile.goldberry.build.ci;

import org.gradle.api.tasks.testing.TestDescriptor;
import org.gradle.api.tasks.testing.TestListener;
import org.gradle.api.tasks.testing.TestResult;

import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Writes every failed test as an error annotation on the GitHub Actions run
 * (ADR-0338).
 *
 * <p>Registered on every {@code Test} task by {@code goldberry.java-conventions},
 * and only on a runner: locally the console already shows the failure, and a line
 * starting {@code ::error} would be noise.
 *
 * <p>One annotation per failing test <em>method</em>, never per container: a class
 * whose tests all fail would otherwise be reported once for itself and once for
 * each test, and the runner keeps only the first ten errors of a step.
 */
public final class TestFailureAnnotations implements TestListener {

    private final String taskPath;
    private final Consumer<String> out;

    /**
     * @param taskPath the {@code Test} task's path, e.g. {@code :core:test}, so an
     *                 annotation says which module and which binding mode ran it
     * @param out      where the command line goes; standard output on a runner
     */
    public TestFailureAnnotations(String taskPath, Consumer<String> out) {
        this.taskPath = taskPath;
        this.out = out;
    }

    @Override
    public void beforeSuite(TestDescriptor suite) {
    }

    @Override
    public void afterSuite(TestDescriptor suite, TestResult result) {
    }

    @Override
    public void beforeTest(TestDescriptor test) {
    }

    @Override
    public void afterTest(TestDescriptor test, TestResult result) {
        if (result.getResultType() != TestResult.ResultType.FAILURE) {
            return;
        }
        out.accept(annotation(test, result).render());
    }

    /** The annotation for one failed test. */
    WorkflowCommand annotation(TestDescriptor test, TestResult result) {
        var className = test.getClassName();
        // A failure outside any class -- an engine that could not start a container --
        // has no class name, and says so by its display name alone.
        var title = className == null || className.isBlank()
                ? "%s %s".formatted(taskPath, test.getDisplayName())
                : "%s %s > %s".formatted(taskPath, simpleName(className), test.getDisplayName());
        var body = result.getExceptions().stream()
                .map(FailureText::of)
                .collect(Collectors.joining("\n\n"));
        return WorkflowCommand.error(title, body.isEmpty() ? "failed without an exception" : body);
    }

    private static String simpleName(String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }
}
