package dev.goldberry.media.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MediaTime")
class MediaTimeTest {

    @Test
    @DisplayName("writes m:ss, and h:mm:ss only past an hour")
    void formats() {
        assertEquals("0:00", MediaTime.format(Duration.ZERO));
        assertEquals("0:07", MediaTime.format(Duration.ofMillis(7_999)));
        assertEquals("3:05", MediaTime.format(Duration.ofSeconds(185)));
        assertEquals("1:02:09", MediaTime.format(Duration.ofSeconds(3729)));
        assertEquals("0:00", MediaTime.format(Duration.ofSeconds(-3)));
    }

    @Test
    @DisplayName("writes the time left with a minus, and never less than zero")
    void remaining() {
        assertEquals("-0:02", MediaTime.remaining(Duration.ofMillis(500), Duration.ofMillis(2500)));
        assertEquals("-0:00", MediaTime.remaining(Duration.ofSeconds(5), Duration.ofSeconds(3)));
    }
}
