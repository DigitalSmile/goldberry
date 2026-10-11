package dev.goldberry.example.ui.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("the history behind Back and Forward")
class NavHistoryTest {

    @Test
    @DisplayName("starts with nowhere to go")
    void empty() {
        var history = new NavHistory();

        assertFalse(history.canGoBack());
        assertFalse(history.canGoForward());
        assertEquals(Optional.empty(), history.back("home"));
        assertEquals(Optional.empty(), history.forward("home"));
    }

    @Test
    @DisplayName("goes back the way it came, and forward again")
    void backAndForward() {
        var history = new NavHistory();
        history.visit("home");
        history.visit("buttons");
        // now on "forms"

        assertEquals(Optional.of("buttons"), history.back("forms"));
        assertEquals(Optional.of("home"), history.back("buttons"));
        assertFalse(history.canGoBack());
        assertEquals(Optional.of("buttons"), history.forward("home"));
        assertEquals(Optional.of("forms"), history.forward("buttons"));
        assertFalse(history.canGoForward());
    }

    @Test
    @DisplayName("forgets what was ahead when the reader goes somewhere new")
    void aNewBranchClearsForward() {
        var history = new NavHistory();
        history.visit("home");
        assertEquals(Optional.of("home"), history.back("buttons"));
        assertTrue(history.canGoForward());

        history.visit("home");

        assertFalse(history.canGoForward());
    }

    @Test
    @DisplayName("does not stack one screen on itself")
    void noRepeats() {
        var history = new NavHistory();
        history.visit("home");
        history.visit("home");

        assertEquals(List.of("home"), history.behind());
    }

    @Test
    @DisplayName("remembers at most its limit, dropping the oldest")
    void capped() {
        var history = new NavHistory();
        for (var i = 0; i < NavHistory.LIMIT + 10; i++) {
            history.visit("screen-" + i);
        }

        assertEquals(NavHistory.LIMIT, history.behind().size());
        assertEquals("screen-" + (NavHistory.LIMIT + 9), history.behind().getFirst());
    }
}
