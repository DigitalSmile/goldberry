package dev.goldberry.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Where the start-up timeline's zero comes from: the kernel's own clock on Linux,
/// `ProcessHandle` elsewhere. `ProcessHandle`'s start instant is up to a second
/// late on Linux, and a timeline measured from it would flatter every row.
@DisplayName("A process's age")
class ProcessAgeTest {

    /// A real `/proc/self/stat` line's shape, with `starttime` (field 22) at
    /// 123456 ticks — 1234.56 s after boot at `USER_HZ` 100.
    private static String stat(String command) {
        return "4242 (" + command + ") S 1 4242 4242 0 -1 4194304 1 0 0 0 0 0 0 0 20 0 1 0 123456"
                + " 12345678 100 18446744073709551615 1 1 0 0 0 0 0 0 0 0 0 0 17 3 0 0 0 0 0";
    }

    @Test
    @DisplayName("is uptime less the process's start, both in the kernel's clock")
    void uptimeLessStart() {
        assertEquals(Optional.of(Duration.ofMillis(1_500)), ProcessAge.parse(stat("java"), "1236.06 9876.54\n"));
    }

    @Test
    @DisplayName("counts fields from the last parenthesis, so a command with spaces and parentheses reads the same")
    void commandWithParentheses() {
        assertEquals(
                ProcessAge.parse(stat("java"), "1236.06 1.00"),
                ProcessAge.parse(stat("my (odd) ) name"), "1236.06 1.00"));
    }

    @Test
    @DisplayName("is empty for text the kernel does not write, rather than a guess")
    void refusesNonsense() {
        assertEquals(Optional.empty(), ProcessAge.parse("4242 java S 1", "1236.06 1.00"), "no command");
        assertEquals(Optional.empty(), ProcessAge.parse("4242 (java) S 1 2 3", "1236.06 1.00"), "too few fields");
        assertEquals(Optional.empty(), ProcessAge.parse(stat("java"), "soon"), "uptime not a number");
        assertEquals(Optional.empty(), ProcessAge.parse(stat("java"), ""), "no uptime");
        assertEquals(Optional.empty(), ProcessAge.parse(stat("java"), "1000.00 1.00"), "started after now");
    }

    @Test
    @DisplayName("on Linux, agrees with ProcessHandle to within the second ProcessHandle can be wrong by")
    void agreesWithProcessHandleOnLinux() {
        var fromProc = ProcessAge.fromProc();
        assumeTrue(fromProc.isPresent(), "no /proc here");
        var handle = ProcessHandle.current()
                .info()
                .startInstant()
                .map(start -> Duration.between(start, Instant.now()))
                .orElseThrow();
        var age = fromProc.orElseThrow();
        assertTrue(age.isPositive(), "a running process has an age: " + age);
        assertTrue(
                age.minus(handle).abs().compareTo(Duration.ofMillis(1_100)) < 0,
                "kernel " + age.toMillis() + " ms against ProcessHandle " + handle.toMillis() + " ms");
    }

    @Test
    @DisplayName("is never negative, from whichever source answered")
    void neverNegative() {
        assertTrue(!ProcessAge.now().isNegative());
    }
}
