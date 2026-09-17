package io.github.digitalsmile.goldberry.build.nativeimage;

import io.github.digitalsmile.goldberry.build.repository.Repository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("TraceVideoDriver")
class TraceVideoDriverTest {

    @ParameterizedTest(name = "\"{0}\" means the default")
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void unsetMeansHeadless(String requested) {
        assertEquals(TraceVideoDriver.DEFAULT, TraceVideoDriver.choose(requested));
    }

    @Test
    @DisplayName("a named driver is taken as given")
    void namedDriverIsTaken() {
        assertEquals("cocoa", TraceVideoDriver.choose(" cocoa "));
    }

    @Test
    @DisplayName("showcase.yml traces on the real driver on macOS and headless on Windows")
    void workflowTracesMacosOnCocoa() {
        var workflow = Repository.workflow("showcase.yml");
        var traceSteps = Pattern.compile("(?m)^      - ").splitAsStream(workflow)
                .filter(step -> step.contains(":example:nativeImageMetadata"))
                .toList();
        var macos = traceSteps.stream().filter(step -> step.contains("if: runner.os == 'macOS'")).findFirst();
        var windows = traceSteps.stream().filter(step -> step.contains("if: runner.os == 'Windows'")).findFirst();
        assertAll(
                () -> assertTrue(macos.isPresent(), "no macOS trace step"),
                () -> assertTrue(windows.isPresent(), "no Windows trace step"),
                () -> assertTrue(macos.orElseThrow().contains("-P" + TraceVideoDriver.PROPERTY + "=" + TraceVideoDriver.MACOS_CI),
                        "the macOS trace has to run on " + TraceVideoDriver.MACOS_CI + ": " + macos.orElse("")),
                () -> assertTrue(!windows.orElseThrow().contains(TraceVideoDriver.PROPERTY),
                        "the Windows trace stays headless: " + windows.orElse("")));
    }
}
