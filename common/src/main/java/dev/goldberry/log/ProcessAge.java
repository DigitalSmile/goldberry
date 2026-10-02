package dev.goldberry.log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;

/// How long this process has been running — the zero [Startup] measures from.
///
/// ## Why not `ProcessHandle`
///
/// `ProcessHandle.current().info().startInstant()` is a wall-clock instant, and on
/// Linux the JDK builds it from `/proc/self/stat`'s `starttime` — clock ticks
/// since boot — plus the boot time from `/proc/stat`'s `btime`, which is a whole
/// number of **seconds**. So the instant is wrong by the fraction `btime`
/// dropped, up to a second, and the same for every process on that boot. On one
/// development machine it made every "runtime starting" row about 218 ms late: a
/// native image the kernel had started 55 ms earlier reported 275. Every Linux
/// timeline printed from that instant carried the error.
///
/// ## The kernel's own clock, both ends
///
/// `starttime` and `/proc/uptime` count from the same boot on the same clock, so
/// their difference is the process's age with no wall clock and no `btime` in
/// it — to the kernel's tick, 10 ms. Anywhere without `/proc`, or where it cannot
/// be read, `ProcessHandle` is the answer: macOS and Windows report a start time
/// to the microsecond, and the fault is Linux's.
final class ProcessAge {

    /// Linux's `USER_HZ`: the unit of `starttime`, and what
    /// `sysconf(_SC_CLK_TCK)` answers. Fixed at 100 in the kernel's user ABI on
    /// every architecture this toolkit builds for, so it is a constant here
    /// rather than a native call from a module that makes none.
    static final int USER_HZ = 100;

    /// `starttime` is field 22 of `/proc/[pid]/stat`; counting from the state,
    /// which is field 3 and the first after the command's closing parenthesis,
    /// it is the twentieth.
    private static final int STARTTIME_AFTER_COMMAND = 19;

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private static final Path STAT = Path.of("/proc/self/stat");
    private static final Path UPTIME = Path.of("/proc/uptime");

    private ProcessAge() {}

    /// This process's age now: from the kernel where it can say, from
    /// `ProcessHandle` where it cannot, and zero where neither can.
    static Duration now() {
        return fromProc().or(ProcessAge::fromProcessHandle).orElse(Duration.ZERO);
    }

    /// The age from `/proc`, or empty on a system without it.
    static Optional<Duration> fromProc() {
        if (!Files.isReadable(STAT) || !Files.isReadable(UPTIME)) {
            return Optional.empty();
        }
        try {
            // Uptime after the stat, so the read itself can only make the age
            // err older, never younger.
            var stat = Files.readString(STAT);
            var uptime = Files.readString(UPTIME);
            return parse(stat, uptime);
        } catch (IOException | RuntimeException e) {
            // A sandbox that hides /proc, or one that serves something else under
            // the name. ProcessHandle is still worth asking.
            return Optional.empty();
        }
    }

    /// The age from the text of `/proc/self/stat` and `/proc/uptime`.
    ///
    /// The command, field 2, is in parentheses and may itself contain spaces and
    /// parentheses, so the fields are counted from the **last** closing one.
    ///
    /// @return empty when either text is not what the kernel writes, or when the
    ///         two disagree about which came first
    static Optional<Duration> parse(String stat, String uptime) {
        var close = stat.lastIndexOf(')');
        if (close < 0) {
            return Optional.empty();
        }
        var fields = WHITESPACE.split(stat.substring(close + 1).trim(), -1);
        var up = WHITESPACE.split(uptime.trim(), -1);
        if (fields.length <= STARTTIME_AFTER_COMMAND || up.length == 0) {
            return Optional.empty();
        }
        try {
            var startTicks = Long.parseLong(fields[STARTTIME_AFTER_COMMAND]);
            var upNanos = Math.round(Double.parseDouble(up[0]) * 1e9);
            var startNanos = startTicks * (1_000_000_000L / USER_HZ);
            var age = upNanos - startNanos;
            return age < 0 || startTicks < 0 ? Optional.empty() : Optional.of(Duration.ofNanos(age));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static Optional<Duration> fromProcessHandle() {
        try {
            return ProcessHandle.current().info().startInstant().map(start -> Duration.between(start, Instant.now()));
        } catch (RuntimeException e) {
            // Some platforms and sandboxes decline to report it. A timeline from
            // class-init is still worth having; it is just missing its prologue.
            return Optional.empty();
        }
    }
}
