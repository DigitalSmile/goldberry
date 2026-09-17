package io.github.digitalsmile.goldberry.build.ci;

import org.gradle.api.tasks.testing.TestDescriptor;
import org.gradle.api.tasks.testing.TestFailure;
import org.gradle.api.tasks.testing.TestResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("test failure annotations")
class TestFailureAnnotationsTest {

    /** Stands in for a null answer, which {@code Map.of} cannot hold. */
    private static final Object NULL = new Object();

    @Test
    @DisplayName("annotate a failed test with its task, class, name and assertion")
    void annotatesAFailure() {
        var lines = new ArrayList<String>();
        var listener = new TestFailureAnnotations(":core:test", lines::add);

        listener.afterTest(
                descriptor("io.github.digitalsmile.goldberry.GoldberryTest", "version() is a calendar version"),
                result(TestResult.ResultType.FAILURE, new AssertionError("not a calendar version: 1.0")));

        assertEquals(1, lines.size());
        var line = lines.getFirst();
        assertAll(
                () -> assertTrue(line.startsWith(
                        "::error title=%3Acore%3Atest GoldberryTest > version() is a calendar version::"), line),
                () -> assertTrue(line.contains("AssertionError: not a calendar version: 1.0"), line));
    }

    @Test
    @DisplayName("say nothing about a test that passed or was skipped")
    void ignoresTheRest() {
        var lines = new ArrayList<String>();
        var listener = new TestFailureAnnotations(":core:test", lines::add);
        var test = descriptor("a.B", "c");

        listener.afterTest(test, result(TestResult.ResultType.SUCCESS));
        listener.afterTest(test, result(TestResult.ResultType.SKIPPED));

        assertTrue(lines.isEmpty());
    }

    @Test
    @DisplayName("still annotate a failure that carried no exception")
    void annotatesAnExceptionlessFailure() {
        var lines = new ArrayList<String>();
        new TestFailureAnnotations(":x:test", lines::add)
                .afterTest(descriptor(null, "d"), result(TestResult.ResultType.FAILURE));

        assertEquals("::error title=%3Ax%3Atest d::failed without an exception", lines.getFirst());
    }

    /** Gradle's interfaces are large and only a few methods matter; a proxy answers those. */
    private static TestDescriptor descriptor(String className, String displayName) {
        return proxy(TestDescriptor.class, Map.of(
                "getClassName", className == null ? NULL : className,
                "getDisplayName", displayName,
                "getName", displayName));
    }

    private static TestResult result(TestResult.ResultType type, Throwable... exceptions) {
        return proxy(TestResult.class, Map.of(
                "getResultType", type,
                "getExceptions", List.of(exceptions),
                "getFailures", List.<TestFailure>of()));
    }

    private static <T> T proxy(Class<T> type, Map<String, Object> answers) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (_, method, _) -> {
                    var answer = answers.get(method.getName());
                    if (answer == null) {
                        throw new UnsupportedOperationException(method.getName());
                    }
                    return answer == NULL ? null : answer;
                }));
    }
}
