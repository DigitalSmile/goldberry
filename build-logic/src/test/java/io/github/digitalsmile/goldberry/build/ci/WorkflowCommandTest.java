package io.github.digitalsmile.goldberry.build.ci;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("a workflow command")
class WorkflowCommandTest {

    @Test
    @DisplayName("renders as one ::error line")
    void rendersAnError() {
        assertEquals("::error title=Gradle build failed::boom",
                WorkflowCommand.error("Gradle build failed", "boom").render());
    }

    @Test
    @DisplayName("keeps a multi-line message on one line, or the runner ends the command early")
    void escapesNewlines() {
        var line = WorkflowCommand.error("t", "first\r\nsecond\nthird").render();
        assertAll(
                () -> assertFalse(line.contains("\n")),
                () -> assertFalse(line.contains("\r")),
                () -> assertTrue(line.endsWith("::first%0D%0Asecond%0Athird")));
    }

    @Test
    @DisplayName("escapes % first, so an escape it wrote is not escaped again")
    void escapesPercentFirst() {
        assertEquals("100%25%0A", WorkflowCommand.escapeData("100%\n"));
    }

    @Test
    @DisplayName("escapes a title's delimiters, which a message may keep")
    void escapesPropertyDelimiters() {
        var line = WorkflowCommand.error(":core:test A, B", "a: b, c").render();
        assertEquals("::error title=%3Acore%3Atest A%2C B::a: b, c", line);
    }

    @Test
    @DisplayName("caps a message the size of a stack trace")
    void capsLength() {
        var line = WorkflowCommand.error("t", "x".repeat(WorkflowCommand.MAX_MESSAGE_LENGTH + 500)).render();
        assertTrue(line.length() < WorkflowCommand.MAX_MESSAGE_LENGTH + 100);
        assertTrue(line.endsWith("%0A..."));
    }

    @Test
    @DisplayName("names the three levels as the runner does")
    void levels() {
        assertAll(
                () -> assertTrue(new WorkflowCommand(WorkflowCommand.Level.WARNING, "t", "m").render()
                        .startsWith("::warning ")),
                () -> assertTrue(new WorkflowCommand(WorkflowCommand.Level.NOTICE, "t", "m").render()
                        .startsWith("::notice ")));
    }

    @Test
    @DisplayName("knows a runner only by GITHUB_ACTIONS=true")
    void detectsTheRunner() {
        assertAll(
                () -> assertTrue(WorkflowCommand.onGithubActions(Map.of("GITHUB_ACTIONS", "true"))),
                () -> assertFalse(WorkflowCommand.onGithubActions(Map.of("GITHUB_ACTIONS", "false"))),
                () -> assertFalse(WorkflowCommand.onGithubActions(Map.of())));
    }

    @Test
    @DisplayName("refuses a missing part")
    void rejectsNulls() {
        assertThrows(NullPointerException.class, () -> WorkflowCommand.error(null, "m"));
        assertThrows(NullPointerException.class, () -> WorkflowCommand.error("t", null));
    }
}
