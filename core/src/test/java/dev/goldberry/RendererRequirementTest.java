package dev.goldberry;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.TestAbortedException;

class RendererRequirementTest {

    @Test
    void anOptionalRendererCanBeAbsent() {
        assertThrows(
                TestAbortedException.class,
                () -> RendererRequirement.enforce(
                        () -> {
                            throw new UnsatisfiedLinkError("missing");
                        },
                        false));
    }

    @Test
    void aRequiredRendererReportsTheLinkageError() {
        var error = new NoClassDefFoundError("BlendImage");
        var failure = assertThrows(
                AssertionFailedError.class,
                () -> RendererRequirement.enforce(
                        () -> {
                            throw error;
                        },
                        true));
        assertSame(error, failure.getCause());
    }
}
