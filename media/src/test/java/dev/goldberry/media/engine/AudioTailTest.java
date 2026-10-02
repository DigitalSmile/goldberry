package dev.goldberry.media.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The last samples on their way to the ear, on a hand-moved
/// [dev.goldberry.media.MediaClock].
@DisplayName("AudioTail")
class AudioTailTest {

    private final AtomicLong now = new AtomicLong(9_000_000_000L);
    private final AudioTail tail = new AudioTail(now::get);

    @Test
    @DisplayName("there is none until the queue empties, and none again once there is a queue")
    void noneUntilStarted() {
        assertEquals(AudioTail.NONE, tail.elapsedNanos());
        tail.start(false);
        now.addAndGet(40);
        assertEquals(40, tail.elapsedNanos());
        tail.reset();
        assertEquals(AudioTail.NONE, tail.elapsedNanos());
    }

    @Test
    @DisplayName("stands still while paused, and runs on from where it stood")
    void pauses() {
        tail.start(false);
        now.addAndGet(30);
        tail.pause();
        now.addAndGet(1_000);
        assertEquals(30, tail.elapsedNanos());
        tail.pause();
        assertEquals(30, tail.elapsedNanos(), "pausing twice does not move it");
        tail.resume();
        now.addAndGet(5);
        assertEquals(35, tail.elapsedNanos());
    }

    @Test
    @DisplayName("started while paused, it waits for play")
    void startsPaused() {
        tail.start(true);
        now.addAndGet(500);
        assertEquals(0, tail.elapsedNanos());
        tail.resume();
        now.addAndGet(7);
        assertEquals(7, tail.elapsedNanos());
    }

    @Test
    @DisplayName("pause and resume with no tail do nothing")
    void idle() {
        tail.pause();
        tail.resume();
        assertEquals(AudioTail.NONE, tail.elapsedNanos());
    }
}
