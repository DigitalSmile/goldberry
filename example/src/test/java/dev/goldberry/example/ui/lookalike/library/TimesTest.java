package dev.goldberry.example.ui.lookalike.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/// How the player writes time and counts.
@DisplayName("a player's clock")
class TimesTest {

    @ParameterizedTest
    @CsvSource({"0:07, 7", "3:41, 221", "12:00, 720", "75:30, 4530"})
    @DisplayName("reads m:ss")
    void parses(String clock, long seconds) {
        assertEquals(Duration.ofSeconds(seconds), Times.parse(clock));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "3", "3:6", "3:60", "a:00", "-1:00", "1:00:00"})
    @DisplayName("refuses anything that is not m:ss")
    void refuses(String clock) {
        assertThrows(IllegalArgumentException.class, () -> Times.parse(clock));
    }

    @ParameterizedTest
    @CsvSource({"0, 0:00", "7, 0:07", "221, 3:41", "3729, 1:02:09"})
    @DisplayName("writes a position as a clock")
    void clock(long seconds, String expected) {
        assertEquals(expected, Times.clock(Duration.ofSeconds(seconds)));
    }

    @Test
    @DisplayName("rounds a position down, so it never reads past the length")
    void roundsDown() {
        assertEquals("3:40", Times.clock(Duration.ofMillis(220_999)));
    }

    @ParameterizedTest
    @CsvSource({"1937, 32 min 17 sec", "3600, 1 hr 0 min", "3900, 1 hr 5 min", "59, 0 min 59 sec"})
    @DisplayName("writes a playlist's length in words")
    void spoken(long seconds, String expected) {
        assertEquals(expected, Times.spoken(Duration.ofSeconds(seconds)));
    }

    @Test
    @DisplayName("groups a count's thousands the same way in every locale")
    void count() {
        assertEquals("182,331,004", Times.count(182_331_004));
        assertEquals("7", Times.count(7));
    }
}
