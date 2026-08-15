package io.github.digitalsmile.goldberry.natives.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Goldberry logs through SLF4J and binds no implementation, so an application
/// that configures nothing must see *nothing* — not even SLF4J explaining that it
/// found no provider.
///
/// These tests run in exactly that situation: `:natives` has slf4j-api on its
/// test path and no provider at all.
class LogsTest {

    @Test
    @DisplayName("SLF4J's internal reporting is turned down before any logger exists")
    void verbosityIsSilenced() {
        // Touching Logs is what runs the static initializer, and every logger in
        // the toolkit comes from here -- which is what makes the ordering hold.
        Logs.of(LogsTest.class);

        assertEquals("ERROR", System.getProperty(Logs.verbosityProperty()));
    }

    @Test
    @DisplayName("the property name is the one SLF4J's Reporter reads")
    void propertyNameMatchesSlf4j() {
        // org.slf4j.helpers.Reporter reads this at class-load and never again, so
        // a typo here is silent: the warning simply keeps appearing.
        assertEquals("slf4j.internal.verbosity", Logs.verbosityProperty());
    }

    @Test
    @DisplayName("with no provider, every logger is the shared no-op one")
    void fallsBackToNoOpLoggers() {
        var logger = Logs.of(LogsTest.class);

        assertNotNull(logger);
        // Not the class name: with nothing bound, SLF4J hands out its NOPLogger,
        // whose name is "NOP". That is the evidence for the claim this whole
        // class exists to make -- no provider means no output, from anywhere.
        // With Logback on the path (as in the showcase) the name is the class's.
        assertEquals("NOP", logger.getName());
    }

    @Test
    @DisplayName("asking twice gives the same logger")
    void loggersAreShared() {
        assertSame(Logs.of(LogsTest.class), Logs.of(LogsTest.class));
    }

    @Test
    @DisplayName("logging with no provider does nothing, quietly")
    void loggingWithoutAProviderIsHarmless() {
        var logger = Logs.of(LogsTest.class);

        // With no provider this is a NOP logger. What matters is that it neither
        // throws nor prints -- the console of an application that configured no
        // logging should stay empty.
        logger.info("this goes nowhere");
        logger.warn("so does this");
        logger.error("and this", new IllegalStateException("not a real failure"));
    }
}
