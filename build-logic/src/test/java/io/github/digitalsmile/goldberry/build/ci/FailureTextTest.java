package io.github.digitalsmile.goldberry.build.ci;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("a failure as text")
class FailureTextTest {

    @Test
    @DisplayName("shows the whole cause chain, outermost first")
    void showsTheChain() {
        var root = new IllegalStateException("jlink exited with 1");
        var failure = new RuntimeException("Execution failed for task ':example:showcaseImage'.", root);

        var lines = FailureText.of(failure).lines().toList();

        assertAll(
                () -> assertEquals("RuntimeException: Execution failed for task ':example:showcaseImage'.",
                        lines.getFirst()),
                () -> assertEquals("IllegalStateException: jlink exited with 1", lines.get(1)),
                () -> assertTrue(lines.get(2).startsWith("    at ")));
    }

    @Test
    @DisplayName("keeps only the top frames of the deepest cause")
    void capsFrames() {
        var failure = new AssertionError("expected 3 but was 4");
        var frames = FailureText.of(failure).lines().filter(line -> line.startsWith("    at ")).count();
        assertEquals(Math.min(FailureText.FRAMES, failure.getStackTrace().length), frames);
    }

    @Test
    @DisplayName("names the type when there is no message")
    void namesAMessagelessThrowable() {
        assertTrue(FailureText.of(new NullPointerException()).startsWith("java.lang.NullPointerException"));
    }

    @Test
    @DisplayName("stops at a cause that loops back")
    void survivesACycle() {
        var outer = new RuntimeException("outer");
        var inner = new RuntimeException("inner", outer);
        outer.initCause(inner);

        assertEquals(2, FailureText.chain(outer).size());
    }
}
