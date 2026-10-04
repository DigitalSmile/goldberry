package dev.goldberry.junit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Tag;

/// A test that reads a wall clock, and so runs in the wall-clock lane.
///
/// A test wears this when what it asserts is about real time: a sink that
/// drains at a device's rate, a read that times out, a popup that waits a real
/// delay out. Such a test measures the machine as well as the code, and under
/// `./gradlew build` the machine is four suites and three analysers running
/// beside it. Tagged, it is left out of every other test task and run by
/// `wallClockTest`, one module at a time and nothing beside it, where a failure
/// is run again before it is believed.
///
/// On a class, every test in it; on a method, that one. A test whose delay goes
/// through [dev.goldberry.render.event.EventLoop#after] does not need this: it
/// moves a [dev.goldberry.render.event.TestClock] instead, and is deterministic.
///
/// Read more:
/// [The wall-clock lane](https://goldberry.dev/docs/contributing/testing.html#the-wall-clock-lane).
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Tag(WallClock.TAG)
public @interface WallClock {

    /// The JUnit tag, which the build's `WallClockLane` names too.
    String TAG = "wallclock";
}
