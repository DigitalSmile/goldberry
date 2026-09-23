package io.github.digitalsmile.goldberry.media.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The master clock on a hand-moved [io.github.digitalsmile.goldberry.media.MediaClock]:
/// held and run, handed over from audio, and counted at a rate.
@DisplayName("MasterClock")
class MasterClockTest {

    private final AtomicLong now = new AtomicLong(5_000_000_000L);
    private final MasterClock clock = new MasterClock(now::get);

    @Test
    @DisplayName("stands until run, counts while running, and stands again when held")
    void runAndHold() {
        now.addAndGet(100);
        assertEquals(0, clock.nanos());
        assertFalse(clock.running());
        clock.run();
        now.addAndGet(1_000);
        assertEquals(1_000, clock.nanos());
        clock.hold();
        now.addAndGet(1_000);
        assertEquals(1_000, clock.nanos());
        clock.set(7_000);
        assertEquals(7_000, clock.nanos());
    }

    @Test
    @DisplayName("counts stream time at the rate, and keeps what it counted at the rate before")
    void rate() {
        clock.run();
        now.addAndGet(1_000);
        clock.setRate(2);
        assertEquals(1_000, clock.nanos(), "a change of rate does not move the clock");
        now.addAndGet(1_000);
        assertEquals(3_000, clock.nanos());
        clock.hold();
        clock.setRate(0.5);
        now.addAndGet(1_000);
        assertEquals(3_000, clock.nanos(), "held, at any rate");
        clock.run();
        now.addAndGet(1_000);
        assertEquals(3_500, clock.nanos());
        assertThrows(IllegalArgumentException.class, () -> clock.setRate(0));
    }

    @Test
    @DisplayName("follows the audio clock while there is one, whatever the rate, and runs on from where it ended")
    void audio() {
        var audio = new AtomicLong(400);
        clock.followAudio(audio::get);
        clock.setRate(2);
        assertTrue(clock.followingAudio());
        assertTrue(clock.running());
        assertEquals(400, clock.nanos());
        clock.freeRunFrom(audio.get(), true);
        assertFalse(clock.followingAudio());
        now.addAndGet(100);
        assertEquals(600, clock.nanos(), "free from 400, at twice the speed");
    }
}
