package io.github.digitalsmile.goldberry.motion;

/// What time it is, for everything that animates — `docs/design-system.md` §1.7.
///
/// ## Why animation reads a clock rather than counting frames
///
/// §1.7's last rule: "animations are functions of the frame timestamp, not frame
/// counts". A frame-counting animation runs at a different speed on a 144 Hz
/// panel than on a 60 Hz one, and slows down whenever a frame is late — so a
/// dropped frame becomes a visibly slower transition rather than an invisible
/// one. Reading a timestamp makes a 160 ms transition take 160 ms whatever the
/// frame loop managed.
///
/// It is also the whole of what makes animation **testable**. A golden image of a
/// mid-animation frame is impossible against a wall clock: the test would have to
/// sleep, and would then be asserting on whatever the scheduler happened to give
/// it. With [#virtual()] the test says `clock.advance(80)` and gets exactly the
/// frame at 80 ms, on every machine and in CI.
///
/// ## One reading per frame
///
/// The renderer reads this **once** per frame and animates everything against
/// that one value. Two nodes in the same frame must not see different times, or
/// two properties transitioning together — a toggle's thumb and its track, which
/// §3.1 says "arrive together" — would arrive a few microseconds apart and drift
/// further the longer they ran.
@FunctionalInterface
public interface Clock {

    /// The current time in **milliseconds**, on an arbitrary origin.
    ///
    /// Milliseconds because that is the unit every duration in §1.7 is written
    /// in, and a `double` because sub-millisecond precision is what keeps a
    /// 100 ms transition smooth at 144 Hz — 6.9 ms a frame does not divide into
    /// whole milliseconds.
    ///
    /// Only differences are meaningful. Nothing may assume the origin is the
    /// epoch, process start, or anything else.
    double nowMillis();

    /// The system clock, monotonic.
    ///
    /// `System.nanoTime` rather than `currentTimeMillis`: an animation must not
    /// jump because NTP stepped the wall clock, and must not run backwards
    /// because the user changed a time zone.
    static Clock system() {
        return () -> System.nanoTime() / 1_000_000.0;
    }

    /// A clock a test drives by hand.
    ///
    /// Starts at zero and moves only when told to, so a test can paint the frame
    /// at any point of any animation and assert on it.
    static Virtual virtual() {
        return new Virtual();
    }

    /// A [Clock] that only moves when a test moves it.
    final class Virtual implements Clock {

        private double now;

        private Virtual() {
        }

        @Override
        public double nowMillis() {
            return now;
        }

        /// Moves the clock forward.
        ///
        /// @param millis how far; must not be negative, because a clock that
        ///               could run backwards would let a test assert on a state
        ///               no real frame can be in
        public Virtual advance(double millis) {
            if (!(millis >= 0)) {
                throw new IllegalArgumentException("a clock advances forwards, not by " + millis);
            }
            now += millis;
            return this;
        }

        /// Moves the clock to an absolute time.
        public Virtual set(double millis) {
            if (millis < now) {
                throw new IllegalArgumentException(
                        "a clock advances forwards: " + millis + " is before " + now);
            }
            now = millis;
            return this;
        }
    }
}
