package dev.goldberry.example.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("a rate meter")
class RateMeterTest {

    private final AtomicLong now = new AtomicLong();
    private final RateMeter meter = new RateMeter(Duration.ofSeconds(1), now::get);

    private void at(long millis, long amount) {
        now.set(Duration.ofMillis(millis).toNanos());
        meter.record(amount);
    }

    @Test
    @DisplayName("reads nothing before two arrivals, but counts the first")
    void empty() {
        assertEquals(RateMeter.Reading.NONE, meter.reading());
        at(0, 480);
        assertEquals(new RateMeter.Reading(1, 480, 0, 0, Duration.ZERO, Duration.ZERO), meter.reading());
    }

    @Test
    @DisplayName("measures the rate, the amount a second, and the mean and longest gap")
    void steady() {
        for (var tick = 0; tick <= 10; tick++) {
            at(tick * 10L + (tick == 5 ? 5 : 0), 480);
        }
        var reading = meter.reading();

        assertEquals(11, reading.events());
        assertEquals(11 * 480, reading.total());
        assertEquals(100, reading.eventsPerSecond(), 1e-9);
        assertEquals(48_000, reading.amountPerSecond(), 1e-6);
        assertEquals(Duration.ofMillis(10), reading.meanInterval());
        assertEquals(Duration.ofMillis(15), reading.longestInterval());
    }

    @Test
    @DisplayName("forgets what fell out of its window, and keeps the totals")
    void window() {
        at(0, 1);
        at(500, 1);
        at(3_000, 1);
        at(3_100, 1);
        var reading = meter.reading();

        assertEquals(4, reading.events());
        assertEquals(4, reading.total());
        assertEquals(10, reading.eventsPerSecond(), 1e-9, "two arrivals 100 ms apart");
        assertEquals(Duration.ofMillis(100), reading.longestInterval());
    }

    @Test
    @DisplayName("refuses a window of no length")
    void refuses() {
        assertThrows(IllegalArgumentException.class, () -> new RateMeter(Duration.ZERO, now::get));
    }
}
