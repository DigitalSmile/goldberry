package io.github.digitalsmile.goldberry.media;

/// Where a [MediaPlayer] reads the passing of time when no audio track is its
/// clock: the Clock SPI of `docs/goldberry-media.md` §3 ("Master clock").
///
/// While an audio track plays, the audio clock is the master and this is not
/// read: what is playing is what the sink has played. A source with no audio, or
/// the rest of a video after its audio has ended, is timed against this instead.
///
/// [#system()] is the monotonic clock, and the default. A test hands the player a
/// clock of its own and moves it by hand, which is what makes a golden of "the
/// picture at 0.4 s" the same picture on every run (§7, S5):
///
/// ```java
/// var now = new AtomicLong();
/// var player = MediaPlayer.builder().clock(now::get).build();
/// ```
@FunctionalInterface
public interface MediaClock {

    /// A monotonic reading in nanoseconds. Only differences between two readings
    /// mean anything, as with [System#nanoTime()].
    long nanoTime();

    /// The system's monotonic clock.
    static MediaClock system() {
        return System::nanoTime;
    }
}
