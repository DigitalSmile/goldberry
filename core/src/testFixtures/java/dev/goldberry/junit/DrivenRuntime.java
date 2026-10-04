package dev.goldberry.junit;

import java.time.Duration;
import java.util.Objects;

import dev.goldberry.Goldberry;
import dev.goldberry.GoldberryTestAccess;
import dev.goldberry.Host;
import dev.goldberry.render.backend.headless.HeadlessBackend;
import dev.goldberry.render.event.EventLoop;
import dev.goldberry.render.event.TestClock;

/// The launcher's runtime over a clock the test moves, and the three steps a
/// test takes against it.
///
/// Every delay a widget asks for is an [EventLoop#after], and the loop made here
/// reads a [TestClock]: a tooltip's dwell, a popup's focus-settle, a menu's safe
/// triangle elapse when the test says so, not when the machine gets round to
/// it. A test that slept 300 ms and looked was reading the scheduler; one that
/// moves the clock 61 ms and looks reads an exact time, and no loaded runner can
/// make the reading wrong in either direction.
///
/// ```java
/// runtime = DrivenRuntime.install(backend);          // in @BeforeEach
/// Goldberry.launch(new TestApp(host -> runtime.afterTheFirstFrame(host, () -> {
///     backend.post(new BackendEvent.PointerMoved(window, 50, 50, 0));
///     runtime.afterTheNextPump(() -> runtime.elapsed(Duration.ofMillis(501), () -> {
///         shown[0] = tooltipIsUp();
///         Goldberry.stop();
///     }));
/// })));
/// ```
///
/// **Why the steps are exact.** The loop runs drain, pump, fire the due timers,
/// drain. An event posted to the backend is delivered by the next pump. A timer
/// scheduled during a drain fires after that pump and never before it, because
/// `TimerQueue.fireDue` collects what is due before running any of it. So
/// [#afterTheNextPump] is a zero-delay timer that hops back onto a drained task,
/// and [#elapsed] is the clock moved and the same timer, which runs after the
/// widget's own timer has had the pass it was due in. Every step must be called
/// on the UI thread, from a drained task, which is where a launched
/// application's callbacks run.
///
/// Two waits stay on the wall clock, because they are the machine's and not the
/// loop's: a frame, which the backend produces, and anything the launcher itself
/// times with `System.nanoTime`. [#afterTheFirstFrame] polls for the first;
/// [#later] is for the second, and a sleep can only overshoot.
///
/// Read more: [A clock bound has
/// room](https://goldberry.dev/docs/contributing/testing.html#a-clock-bound-has-room).
public final class DrivenRuntime {

    /// How often a wall-clock poll looks, in milliseconds.
    private static final long POLL_MILLIS = 5;

    private final HeadlessBackend backend;
    private final TestClock clock;
    private final EventLoop loop;

    private DrivenRuntime(HeadlessBackend backend, TestClock clock, EventLoop loop) {
        this.backend = backend;
        this.clock = clock;
        this.loop = loop;
    }

    /// Installs the runtime over `backend` with a loop that reads a fresh
    /// [TestClock], before anything starts one. Take it down again with
    /// [#shutdown()].
    ///
    /// @param backend the headless backend the test posts events to
    /// @return the runtime, to drive
    public static DrivenRuntime install(HeadlessBackend backend) {
        Objects.requireNonNull(backend, "backend");
        var clock = new TestClock();
        var loop = clock.loopOver(backend);
        GoldberryTestAccess.install(backend, loop);
        return new DrivenRuntime(backend, clock, loop);
    }

    /// The same, over a new backend.
    public static DrivenRuntime install() {
        return install(new HeadlessBackend());
    }

    /// Takes the runtime down, so the next test starts from nothing.
    public void shutdown() {
        GoldberryTestAccess.shutdown();
    }

    /// The backend the runtime pumps.
    public HeadlessBackend backend() {
        return backend;
    }

    /// The clock the loop reads. [#elapsed] moves it; this is for a test that
    /// wants to read it.
    public TestClock clock() {
        return clock;
    }

    /// Runs `then` on the UI thread after the loop's next pump has delivered what
    /// was posted before this, and the timers due by then have fired.
    ///
    /// @param then what to run, on a drained task
    public void afterTheNextPump(Runnable then) {
        Objects.requireNonNull(then, "then");
        // Back onto a drained task rather than running inside the timer pass, so
        // whatever `then` posts or schedules is ordered the same way again.
        loop.after(Duration.ZERO, () -> loop.ui().execute(then));
    }

    /// Moves the clock `by` and runs `then` once the loop has fired whatever that
    /// made due.
    ///
    /// @param by   how far the clock moves; a tick past a delay to see it fire, a
    ///             tick short to see it has not
    /// @param then what to run, on a drained task
    public void elapsed(Duration by, Runnable then) {
        clock.advance(Objects.requireNonNull(by, "by"), backend);
        afterTheNextPump(then);
    }

    /// Runs `then` on the UI thread once `host`'s window has painted a frame: hit
    /// testing runs against the frame that was painted, so a pointer event that
    /// arrives before there is one lands on nothing at all.
    ///
    /// A real wait, because a frame is the backend's to produce and not the
    /// clock's; and a wait that polls until the thing has happened, so a slow
    /// machine makes it longer and never wrong.
    ///
    /// @param host the launched application's host, whose [Host#frames] says
    /// @param then what to run, on a drained task
    public void afterTheFirstFrame(Host host, Runnable then) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(then, "then");
        later(POLL_MILLIS, () -> {
            if (host.frames().isEmpty()) {
                afterTheFirstFrame(host, then);
            } else {
                then.run();
            }
        });
    }

    /// Runs `action` on the UI thread after `millis` of **wall-clock** time, for
    /// a wait that is the machine's rather than the loop's. A sleep can only
    /// overshoot, so it is safe before an event that must come *after* something
    /// the launcher times itself, and wrong before any reading of what has *not*
    /// happened yet; [#elapsed] is for that.
    ///
    /// @param millis how long to wait
    /// @param action what to run, on a drained task
    public static void later(long millis, Runnable action) {
        Objects.requireNonNull(action, "action");
        Goldberry.async(() -> {
                    try {
                        Thread.sleep(millis);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return null;
                })
                .thenRun(action);
    }
}
