package io.github.digitalsmile.goldberry.natives.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StartupTest {

    @BeforeEach
    void clearTimeline() {
        Startup.reset();
    }

    @Test
    @DisplayName("marks are kept in the order they happened")
    void keepsOrder() {
        Startup.mark("first");
        Startup.mark("second");
        Startup.mark("third");

        assertEquals(
                List.of("first", "second", "third"),
                Startup.marks().stream().map(Startup.Mark::phase).toList());
    }

    @Test
    @DisplayName("each mark is timed from process start, so they only move forwards")
    void timesFromProcessStart() {
        Startup.mark("first");
        Startup.mark("second");

        var marks = Startup.marks();
        assertTrue(
                marks.get(0).sinceStart().compareTo(Duration.ZERO) > 0,
                "a mark should be measured against process start, not against zero");
        assertTrue(
                marks.get(1).sinceStart().compareTo(marks.get(0).sinceStart()) >= 0,
                "the timeline must not go backwards");
    }

    @Test
    @DisplayName("timing a block returns its value and records how long it took")
    void timesAndReturns() {
        var value = new Object();

        var returned = Startup.time("work", () -> value);

        assertSame(value, returned);
        assertEquals(1, Startup.marks().size());
        assertTrue(
                Startup.marks().getFirst().phase().startsWith("work ("),
                () -> "the duration belongs in the phase: " + Startup.marks().getFirst().phase());
    }

    @Test
    @DisplayName("a failing block is still timed, and its failure still propagates")
    void timesFailures() {
        // A phase that throws is the one you most want to see in the timeline.
        assertThrows(IllegalStateException.class, () -> Startup.time("doomed", () -> {
            throw new IllegalStateException("no");
        }));

        assertEquals(1, Startup.marks().size());
        assertTrue(Startup.marks().getFirst().phase().startsWith("doomed ("));
    }

    @Test
    @DisplayName("timing a void block works too")
    void timesRunnable() {
        var ran = new boolean[1];

        Startup.time("side effect", () -> ran[0] = true);

        assertTrue(ran[0]);
        assertEquals(1, Startup.marks().size());
    }

    @Test
    @DisplayName("recording stops at the cap, so a mark in a loop cannot grow the heap")
    void capsRecording() {
        for (var i = 0; i < Startup.MAX_MARKS + 50; i++) {
            Startup.mark("mark " + i);
        }

        assertEquals(Startup.MAX_MARKS, Startup.marks().size());
        assertEquals("mark 0", Startup.marks().getFirst().phase(), "the earliest marks are the ones worth keeping");
    }

    @Test
    @DisplayName("the process has an age before the toolkit does anything")
    void reportsProcessAge() {
        // Measuring from the toolkit's first line would flatter the number: JVM
        // start-up is part of what a user waits for.
        assertTrue(Startup.sinceProcessStart().compareTo(Duration.ZERO) > 0);
    }

    @Test
    @DisplayName("summarizing is harmless with nothing recorded")
    void summarizesEmpty() {
        Startup.summarize();

        assertTrue(Startup.marks().isEmpty());
    }

    @Test
    @DisplayName("the timeline is safe to write from several threads")
    void isThreadSafe() throws Exception {
        var threads = new Thread[4];
        for (var t = 0; t < threads.length; t++) {
            var id = t;
            threads[t] = new Thread(() -> {
                for (var i = 0; i < 20; i++) {
                    Startup.mark("thread " + id + " mark " + i);
                }
            }, "marker-" + t);
            threads[t].start();
        }
        for (var thread : threads) {
            thread.join();
        }

        // Background work marks its own phases, so this is not hypothetical.
        assertEquals(80, Startup.marks().size());
    }

    @Test
    @DisplayName("modules can be listed without a module path")
    void listsModulesAnywhere() {
        // Tests run on the classpath, where no Goldberry module is resolved. It
        // must say so rather than fail.
        Startup.logModules();

        assertFalse(ModuleLayer.boot().modules().isEmpty());
    }
}
