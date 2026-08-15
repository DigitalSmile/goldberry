package io.github.digitalsmile.goldberry.natives.log;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.slf4j.Logger;

/// The start-up timeline: what Goldberry did before the first pixel, and when.
///
/// `docs/ARCHITECTURE.md` §1 opens with "starts in milliseconds". That is a claim
/// with a number in it, and until now nothing in the toolkit could say whether it
/// was true — or, when it stops being true, which part stopped it.
///
/// Every phase is timed from **process start**, not from the toolkit's first
/// line, because JVM start-up is part of what a user waits for and leaving it out
/// would flatter the number. `ProcessHandle` supplies that instant; the deltas
/// between marks come from [System#nanoTime()], which is monotonic where a wall
/// clock is not.
///
/// Marks are recorded whether or not anything is listening — they cost a
/// timestamp and a queue append — and reported at **trace**. [#summarize()] prints
/// the whole table once, after the first frame:
///
/// ```text
/// TRACE Startup - start-up timeline (first frame at 412.7ms):
///     108.4ms  +108.4ms  runtime starting
///     141.9ms   +33.5ms  libgoldberry loaded
///     ...
/// ```
///
/// Recording stops after [#MAX_MARKS], so a mark accidentally placed in a loop
/// grows the heap by nothing and the timeline stays readable.
public final class Startup {

    /// Enough for every phase the toolkit has, with room for the ones it will
    /// grow. Past this, marks are counted and discarded.
    public static final int MAX_MARKS = 256;

    private static final Logger LOG = Logs.of(Startup.class);

    /// When this class was initialized, in both clocks — so a mark can be
    /// reported against process start while its precision comes from `nanoTime`.
    private static final long INIT_NANOS = System.nanoTime();

    private static final Duration AGE_AT_INIT = processAgeAtInit();

    private static final Queue<Mark> MARKS = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger RECORDED = new AtomicInteger();
    private static final AtomicBoolean SUMMARIZED = new AtomicBoolean();

    private Startup() {
    }

    /// One recorded moment.
    ///
    /// @param phase what happened
    /// @param sinceStart how long after process start it happened
    public record Mark(String phase, Duration sinceStart) {
    }

    /// Records that `phase` has just happened.
    public static void mark(String phase) {
        var elapsed = sinceProcessStart();
        if (RECORDED.incrementAndGet() > MAX_MARKS) {
            return;
        }
        MARKS.add(new Mark(phase, elapsed));
        LOG.trace("{} {}", millis(elapsed), phase);
    }

    /// Times `work` and records how long it took.
    ///
    /// The mark is written **after** the work, so the timeline reads as a
    /// sequence of completions rather than of intentions.
    public static <T> T time(String phase, Supplier<T> work) {
        var started = System.nanoTime();
        try {
            return work.get();
        } finally {
            mark(phase + " (" + millis(Duration.ofNanos(System.nanoTime() - started)).trim() + ")");
        }
    }

    /// Times `work` when there is nothing to return.
    public static void time(String phase, Runnable work) {
        time(phase, () -> {
            work.run();
            return null;
        });
    }

    /// The timeline so far, oldest first.
    public static List<Mark> marks() {
        return List.copyOf(MARKS);
    }

    /// How old this process is.
    public static Duration sinceProcessStart() {
        return AGE_AT_INIT.plusNanos(System.nanoTime() - INIT_NANOS);
    }

    /// Prints the whole timeline, once.
    ///
    /// Called after the first frame reaches the screen, which is the moment the
    /// "starts in milliseconds" claim is actually about. Later calls do nothing —
    /// a second window is not a second start-up.
    public static void summarize() {
        if (!LOG.isTraceEnabled() || !SUMMARIZED.compareAndSet(false, true)) {
            return;
        }

        var marks = marks();
        if (marks.isEmpty()) {
            return;
        }

        var report = new StringBuilder("start-up timeline (")
                .append(millis(marks.getLast().sinceStart()).trim())
                .append(" to here):");
        var previous = Duration.ZERO;
        for (var mark : marks) {
            report.append(System.lineSeparator())
                    .append("    ").append(millis(mark.sinceStart()))
                    .append("  ").append(delta(mark.sinceStart().minus(previous)))
                    .append("  ").append(mark.phase());
            previous = mark.sinceStart();
        }
        var dropped = RECORDED.get() - marks.size();
        if (dropped > 0) {
            report.append(System.lineSeparator())
                    .append("    (").append(dropped).append(" further marks not recorded)");
        }
        LOG.trace("{}", report);
    }

    /// Logs which Goldberry modules the JVM actually resolved, and their
    /// versions.
    ///
    /// Not the same question as which are on the module path: a module nothing
    /// `requires` is never resolved, so this is the honest list of what is loaded
    /// — and it is the first thing to check when a widget package appears to be
    /// missing.
    public static void logModules() {
        if (!LOG.isTraceEnabled()) {
            return;
        }
        var modules = new ArrayList<String>();
        for (var module : ModuleLayer.boot().modules()) {
            if (module.getName().startsWith("io.github.digitalsmile.goldberry")) {
                modules.add(module.getName()
                        + module.getDescriptor().version().map(v -> "@" + v).orElse(""));
            }
        }
        modules.sort(null);
        LOG.trace("goldberry modules resolved: {}", modules.isEmpty() ? "none (classpath)" : modules);
    }

    /// Clears the timeline. For tests; there is one start-up per process
    /// otherwise.
    static void reset() {
        MARKS.clear();
        RECORDED.set(0);
        SUMMARIZED.set(false);
    }

    private static Duration processAgeAtInit() {
        try {
            return ProcessHandle.current().info().startInstant()
                    .map(start -> Duration.between(start, Instant.now()))
                    .orElse(Duration.ZERO);
        } catch (RuntimeException e) {
            // Some platforms and sandboxes decline to report it. A timeline from
            // class-init is still worth having; it is just missing its prologue.
            return Duration.ZERO;
        }
    }

    private static String millis(Duration duration) {
        return String.format("%8.1fms", duration.toNanos() / 1_000_000d);
    }

    private static String delta(Duration duration) {
        return String.format("%+8.1fms", duration.toNanos() / 1_000_000d);
    }
}
