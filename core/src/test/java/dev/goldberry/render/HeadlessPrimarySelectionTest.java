package dev.goldberry.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.render.backend.headless.HeadlessBackend;

/// The headless backend's primary selection — present, separate from the
/// clipboard, and able to be switched off.
///
/// The switch is the part worth a test of its own: every widget test of "no
/// primary selection, so a middle click is not a paste" stands on it.
class HeadlessPrimarySelectionTest {

    private HeadlessBackend backend;

    @BeforeEach
    void setUp() {
        backend = new HeadlessBackend();
    }

    @AfterEach
    void tearDown() {
        backend.close();
    }

    @Test
    @DisplayName("is present by default, and round-trips its text")
    void presentByDefault() {
        var selection = backend.primarySelection().orElseThrow();

        assertFalse(selection.hasText());
        assertTrue(selection.text("selected"));

        assertTrue(selection.hasText());
        assertEquals("selected", selection.text());
    }

    @Test
    @DisplayName("is not the clipboard")
    void notTheClipboard() {
        backend.clipboard().text("copied");
        backend.primarySelection().orElseThrow().text("selected");

        assertEquals("copied", backend.clipboard().text());
        assertEquals("selected", backend.primarySelection().orElseThrow().text());
    }

    @Test
    @DisplayName("can be switched off, as a platform without one has it")
    void canBeSwitchedOff() {
        backend.primarySelection(false);

        assertTrue(backend.primarySelection().isEmpty());

        backend.primarySelection(true);
        assertTrue(backend.primarySelection().isPresent());
    }

    @Test
    @DisplayName("a refused write changes nothing and is not counted")
    void refuses() {
        var buffer = backend.primaryBuffer();
        buffer.text("first");
        buffer.refuseWrites(true);

        assertFalse(buffer.text("second"));

        assertEquals("first", buffer.text());
        assertEquals(1, buffer.writes());
    }

    @Test
    @DisplayName("a null write clears, as the clipboard's does")
    void nullClears() {
        var buffer = backend.primaryBuffer();
        buffer.text("something");

        buffer.text(null);

        assertFalse(buffer.hasText());
        assertEquals("", buffer.text());
    }
}
